package isi.dan.ms.pedidos.servicio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import feign.FeignException;
import isi.dan.ms.pedidos.dao.PedidoRepository;
import isi.dan.ms.pedidos.dto.EntregaItemRequest;
import isi.dan.ms.pedidos.dto.EntregaRequest;
import isi.dan.ms.pedidos.dto.StockUpdateDTO;
import isi.dan.ms.pedidos.exception.ConflictException;
import isi.dan.ms.pedidos.exception.ExternalServiceUnavailableException;
import isi.dan.ms.pedidos.exception.PedidoNotFoundException;
import isi.dan.ms.pedidos.exception.UnprocessableEntityException;
import isi.dan.ms.pedidos.modelo.AuditLog;
import isi.dan.ms.pedidos.modelo.DetallePedido;
import isi.dan.ms.pedidos.modelo.Entrega;
import isi.dan.ms.pedidos.modelo.EntregaItem;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Pedido;
import isi.dan.ms.pedidos.modelo.Producto;

@Service
public class PedidoService {

    private static final List<EstadoPedido> ESTADOS_QUE_CONSUMEN_CREDITO = List.of(
            EstadoPedido.ACEPTADO,
            EstadoPedido.EN_PREPARACION,
            EstadoPedido.ENTREGA_PARCIAL,
            EstadoPedido.ENTREGADO);

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SecuenciaService secuenciaService;

    @Autowired
    private ClienteFeignClient clienteFeignClient;

    @Autowired
    private ProductoFeignClient productoFeignClient;

    Logger log = LoggerFactory.getLogger(PedidoService.class);

    public Pedido savePedido(Pedido pedido) {
        return savePedido(pedido, "ADMIN", null, "sistema");
    }

    public Pedido savePedido(Pedido pedido, String rol, String idempotencyKey, String actor) {
        String effectiveKey = normalizarIdempotencyKey(idempotencyKey);
        Optional<Pedido> existente = pedidoRepository.findByIdempotencyKey(effectiveKey);
        if (existente.isPresent()) {
            log.info("Pedido idempotente reutilizado con key {}", effectiveKey);
            return existente.get();
        }

        validarPedido(pedido);
        pedido.setIdempotencyKey(effectiveKey);
        pedido.setUsuario(actor);
        inicializarColecciones(pedido);
        log.info("Creando pedido para cliente {} por usuario autenticado {}", pedido.getCliente().getId(), actor);

        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedido detalle : pedido.getDetalle()) {
            Producto productoReal = obtenerProductoReal(detalle.getProducto().getId(), rol);
            if (productoReal.getPrecio() == null) {
                throw new UnprocessableEntityException(
                        "El producto " + productoReal.getId() + " no tiene precio vigente");
            }

            BigDecimal precioUnitario = productoReal.getPrecio();
            BigDecimal descuento = clampDescuento(productoReal.getDescuentoPromocional());
            BigDecimal precioFinal = precioUnitario
                    .multiply(detalle.getCantidad())
                    .multiply(BigDecimal.ONE.subtract(descuento.divide(BigDecimal.valueOf(100))));

            detalle.setLineaId(UUID.randomUUID().toString());
            detalle.setProducto(productoReal);
            detalle.setCantidadEntregada(BigDecimal.ZERO);
            detalle.setPrecioUnitario(precioUnitario);
            detalle.setDescuento(descuento);
            detalle.setPrecioFinal(precioFinal);
            total = total.add(precioFinal);
        }
        pedido.setTotal(total);
        pedido.setFecha(Instant.now());
        pedido.setNumeroPedido(secuenciaService.nextNumeroPedido());

        BigDecimal maximoDescubierto = obtenerMaximoDescubierto(pedido.getCliente().getId());
        BigDecimal gastado = pedidoRepository
                .findByCliente_IdAndEstadoInActivos(pedido.getCliente().getId(), ESTADOS_QUE_CONSUMEN_CREDITO)
                .stream()
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!checkSaldo(pedido.getCliente().getId(), gastado.add(total))) {
            log.warn("Saldo insuficiente: gastado {}, nuevo {}, maximoDescubierto {}",
                    gastado, total, maximoDescubierto);
            pedido.setEstado(EstadoPedido.RECHAZADO);
            auditar(pedido, actor, "PEDIDO_RECHAZADO",
                    "Credito insuficiente. Consumo: " + gastado.add(total));
            return guardarConIdempotencia(pedido, effectiveKey);
        }

        pedido.setEstado(EstadoPedido.ACEPTADO);

        List<StockUpdateDTO> stockItems = buildStockItems(pedido);
        boolean pendienteStock = false;
        try {
            productoFeignClient.reserveStock(stockItems);
            pedido.setEstado(EstadoPedido.EN_PREPARACION);
        } catch (FeignException.NotFound e) {
            throw new UnprocessableEntityException("No se encontro uno de los productos del pedido");
        } catch (FeignException.Conflict e) {
            // 409 STOCK_INSUFICIENTE: el pedido queda ACEPTADO a la espera de stock.
            log.warn("Stock insuficiente, pedido {} queda pendiente de stock", pedido.getNumeroPedido());
            pendienteStock = true;
        } catch (Exception e) {
            // Timeout, 5xx o servicio caido: no se acepta el pedido en silencio.
            log.error("Error al reservar stock para pedido {}", pedido.getNumeroPedido(), e);
            throw new ExternalServiceUnavailableException(
                    "No se pudo reservar el stock del pedido " + pedido.getNumeroPedido(), e);
        }

        auditar(pedido, actor, "PEDIDO_CREADO",
                "Estado inicial " + pedido.getEstado() + ", total " + total);
        if (pendienteStock) {
            auditar(pedido, actor, "PEDIDO_PENDIENTE_STOCK", "Stock insuficiente al crear el pedido");
        }
        return guardarConIdempotencia(pedido, effectiveKey);
    }

    public Pedido registrarEntrega(String id, EntregaRequest request, String actor) {
        Pedido pedido = pedidoRepository.findByIdActivo(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));

        if (pedido.getEstado() != EstadoPedido.EN_PREPARACION
                && pedido.getEstado() != EstadoPedido.ENTREGA_PARCIAL) {
            throw new ConflictException(
                    "Solo se puede entregar un pedido con stock reservado (EN_PREPARACION o ENTREGA_PARCIAL)");
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new UnprocessableEntityException("La entrega debe contener al menos un item");
        }

        inicializarColecciones(pedido);
        asegurarLineas(pedido);
        Map<String, DetallePedido> lineas = pedido.getDetalle().stream()
                .collect(Collectors.toMap(DetallePedido::getLineaId, Function.identity()));

        Set<String> lineasVistas = new HashSet<>();
        List<EntregaItem> items = new ArrayList<>();
        for (EntregaItemRequest itemRequest : request.getItems()) {
            if (!lineasVistas.add(itemRequest.getLineaId())) {
                throw new UnprocessableEntityException(
                        "La linea " + itemRequest.getLineaId() + " esta repetida en la entrega");
            }
            DetallePedido linea = lineas.get(itemRequest.getLineaId());
            if (linea == null) {
                throw new UnprocessableEntityException(
                        "La linea " + itemRequest.getLineaId() + " no pertenece al pedido");
            }
            if (itemRequest.getCantidad() == null || itemRequest.getCantidad().signum() <= 0) {
                throw new UnprocessableEntityException("La cantidad entregada debe ser mayor a cero");
            }
            if (itemRequest.getCantidad().compareTo(linea.getCantidadPendiente()) > 0) {
                throw new UnprocessableEntityException(
                        "La cantidad entregada de " + linea.getProducto().getNombre()
                                + " supera la pendiente (" + linea.getCantidadPendiente() + ")");
            }

            linea.setCantidadEntregada(linea.getCantidadEntregada().add(itemRequest.getCantidad()));
            EntregaItem item = new EntregaItem();
            item.setLineaId(linea.getLineaId());
            item.setIdProducto(linea.getProducto().getId());
            item.setProductoNombre(linea.getProducto().getNombre());
            item.setCantidad(itemRequest.getCantidad());
            items.add(item);
        }

        Entrega entrega = new Entrega();
        entrega.setId(UUID.randomUUID().toString());
        entrega.setNumeroRemito(secuenciaService.nextNumeroRemito());
        entrega.setFecha(Instant.now());
        entrega.setReceptor(request.getReceptor());
        entrega.setObservaciones(request.getObservaciones());
        entrega.setItems(items);
        pedido.getEntregas().add(entrega);

        boolean completa = pedido.getDetalle().stream()
                .allMatch(linea -> linea.getCantidadPendiente().signum() == 0);
        pedido.setEstado(completa ? EstadoPedido.ENTREGADO : EstadoPedido.ENTREGA_PARCIAL);
        auditar(pedido, actor, "ENTREGA_REGISTRADA",
                "Remito " + entrega.getNumeroRemito() + ", estado " + pedido.getEstado());
        return pedidoRepository.save(pedido);
    }

    public List<Entrega> getEntregas(String id) {
        Pedido pedido = pedidoRepository.findByIdActivo(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));
        return pedido.getEntregas() == null ? List.of() : pedido.getEntregas();
    }

    public Entrega getEntrega(String id, String entregaId) {
        return getEntregas(id).stream()
                .filter(entrega -> entregaId.equals(entrega.getId()))
                .findFirst()
                .orElseThrow(() -> new PedidoNotFoundException("Entrega " + entregaId + " no encontrada"));
    }

    private Pedido guardarConIdempotencia(Pedido pedido, String idempotencyKey) {
        try {
            return pedidoRepository.save(pedido);
        } catch (DuplicateKeyException e) {
            Optional<Pedido> existente = pedidoRepository.findByIdempotencyKey(idempotencyKey);
            if (existente.isPresent()) {
                log.info("Alta concurrente detectada; se devuelve pedido existente para key {}", idempotencyKey);
                if (pedido.getEstado() == EstadoPedido.EN_PREPARACION) {
                    try {
                        devolverStock(pedido);
                    } catch (RuntimeException releaseError) {
                        log.error("No se pudo compensar la reserva duplicada del pedido {}",
                                pedido.getNumeroPedido(), releaseError);
                    }
                }
                return existente.get();
            }
            throw e;
        } catch (RuntimeException e) {
            if (pedido.getEstado() == EstadoPedido.EN_PREPARACION) {
                try {
                    devolverStock(pedido);
                } catch (RuntimeException releaseError) {
                    log.error("No se pudo compensar el stock del pedido {} tras fallar el guardado",
                            pedido.getNumeroPedido(), releaseError);
                }
            }
            throw e;
        }
    }

    private void validarPedido(Pedido pedido) {
        if (pedido.getCliente() == null || pedido.getCliente().getId() == null) {
            throw new UnprocessableEntityException("El cliente es obligatorio");
        }
        if (pedido.getDetalle() == null || pedido.getDetalle().isEmpty()) {
            throw new UnprocessableEntityException("El pedido debe contener al menos un item");
        }
        for (DetallePedido detalle : pedido.getDetalle()) {
            if (detalle.getProducto() == null || detalle.getProducto().getId() == null) {
                throw new UnprocessableEntityException("Cada linea debe indicar un producto");
            }
            if (detalle.getCantidad() == null || detalle.getCantidad().signum() <= 0) {
                throw new UnprocessableEntityException(
                        "La cantidad del producto " + detalle.getProducto().getId() + " debe ser mayor a cero");
            }
        }
    }

    private void inicializarColecciones(Pedido pedido) {
        if (pedido.getEntregas() == null) {
            pedido.setEntregas(new ArrayList<>());
        }
        if (pedido.getAuditoria() == null) {
            pedido.setAuditoria(new ArrayList<>());
        }
    }

    private void asegurarLineas(Pedido pedido) {
        for (DetallePedido detalle : pedido.getDetalle()) {
            if (detalle.getLineaId() == null || detalle.getLineaId().isBlank()) {
                detalle.setLineaId(UUID.randomUUID().toString());
            }
            if (detalle.getCantidadEntregada() == null) {
                detalle.setCantidadEntregada(BigDecimal.ZERO);
            }
        }
    }

    private void auditar(Pedido pedido, String actor, String accion, String detalle) {
        AuditLog evento = new AuditLog();
        evento.setFecha(Instant.now());
        evento.setActor(actor);
        evento.setAccion(accion);
        evento.setDetalle(detalle);
        pedido.getAuditoria().add(evento);
    }

    private Producto obtenerProductoReal(Long productoId, String rol) {
        try {
            return productoFeignClient.getProducto(productoId, rolEfectivo(rol));
        } catch (FeignException.NotFound e) {
            throw new UnprocessableEntityException("El producto " + productoId + " no existe");
        } catch (Exception e) {
            throw new ExternalServiceUnavailableException(
                    "No se pudo consultar el producto " + productoId, e);
        }
    }

    private BigDecimal obtenerMaximoDescubierto(Integer clienteId) {
        try {
            return clienteFeignClient.getMaximoDescubierto(clienteId);
        } catch (FeignException.NotFound e) {
            throw new UnprocessableEntityException("El cliente " + clienteId + " no existe");
        } catch (Exception e) {
            throw new ExternalServiceUnavailableException(
                    "No se pudo validar el credito del cliente " + clienteId, e);
        }
    }

    private boolean checkSaldo(Integer clienteId, BigDecimal monto) {
        try {
            return Boolean.TRUE.equals(clienteFeignClient.checkSaldo(clienteId, monto));
        } catch (FeignException.NotFound e) {
            throw new UnprocessableEntityException("El cliente " + clienteId + " no existe");
        } catch (Exception e) {
            throw new ExternalServiceUnavailableException(
                    "No se pudo validar el credito del cliente " + clienteId, e);
        }
    }

    private String normalizarIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String key = idempotencyKey.trim();
        if (key.length() > 128) {
            throw new UnprocessableEntityException("Idempotency-Key no puede superar 128 caracteres");
        }
        return key;
    }

    private String rolEfectivo(String rol) {
        return rol == null || rol.isBlank() ? "ADMIN" : rol;
    }

    public List<Pedido> getAllPedidos() {
        return pedidoRepository.findAllActivos();
    }

    public Pedido getPedidoById(String id) {
        return pedidoRepository.findByIdActivo(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));
    }

    public Pedido updateEstado(String id, EstadoPedido nuevoEstado) {
        return updateEstado(id, nuevoEstado, "sistema");
    }

    public Pedido updateEstado(String id, EstadoPedido nuevoEstado, String actor) {
        if (nuevoEstado != EstadoPedido.ENTREGADO && nuevoEstado != EstadoPedido.CANCELADO) {
            throw new IllegalArgumentException("Solo se permite actualizar a ENTREGADO o CANCELADO");
        }
        Pedido pedido = pedidoRepository.findByIdActivo(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));
        inicializarColecciones(pedido);

        if (pedido.getEstado() == nuevoEstado) {
            return pedido;
        }

        if (nuevoEstado == EstadoPedido.CANCELADO) {
            if (pedido.getEstado() == EstadoPedido.ENTREGA_PARCIAL
                    || pedido.getEstado() == EstadoPedido.ENTREGADO) {
                throw new ConflictException("No se puede cancelar un pedido con entregas realizadas");
            }
            EstadoPedido estadoAnterior = pedido.getEstado();
            if (estadoAnterior == EstadoPedido.EN_PREPARACION) {
                devolverStock(pedido);
            }
            pedido.setEstado(EstadoPedido.CANCELADO);
            auditar(pedido, actor, "PEDIDO_CANCELADO", "Cancelado desde " + estadoAnterior);
            return pedidoRepository.save(pedido);
        }

        if (pedido.getEstado() != EstadoPedido.EN_PREPARACION
                && pedido.getEstado() != EstadoPedido.ENTREGA_PARCIAL) {
            throw new ConflictException(
                    "Solo se puede entregar un pedido con stock reservado (EN_PREPARACION o ENTREGA_PARCIAL)");
        }
        asegurarLineas(pedido);
        boolean completa = pedido.getDetalle().stream()
                .allMatch(linea -> linea.getCantidadPendiente().signum() == 0);
        if (!completa) {
            throw new ConflictException("El pedido tiene cantidades pendientes; registre una entrega");
        }
        pedido.setEstado(EstadoPedido.ENTREGADO);
        auditar(pedido, actor, "PEDIDO_ENTREGADO", "Todas las lineas entregadas");
        return pedidoRepository.save(pedido);
    }

    public void deletePedido(String id) {
        deletePedido(id, "sistema");
    }

    public void deletePedido(String id, String actor) {
        pedidoRepository.findByIdActivo(id).ifPresent(pedido -> {
            inicializarColecciones(pedido);
            if (pedido.getEstado() == EstadoPedido.ENTREGA_PARCIAL
                    || pedido.getEstado() == EstadoPedido.ENTREGADO) {
                throw new ConflictException("No se puede borrar un pedido con entregas realizadas");
            }
            if (pedido.getEstado() == EstadoPedido.EN_PREPARACION) {
                devolverStock(pedido);
            }
            pedido.setHabilitado(false);
            pedido.setFechaBorrado(Instant.now());
            auditar(pedido, actor, "PEDIDO_BORRADO", "Soft delete");
            pedidoRepository.save(pedido);
        });
    }

    private void devolverStock(Pedido pedido) {
        try {
            productoFeignClient.releaseStock(buildStockItems(pedido));
        } catch (Exception e) {
            throw new ExternalServiceUnavailableException(
                    "No se pudo liberar el stock del pedido " + pedido.getNumeroPedido(), e);
        }
    }

    private List<StockUpdateDTO> buildStockItems(Pedido pedido) {
        return pedido.getDetalle().stream()
                .map(d -> {
                    StockUpdateDTO item = new StockUpdateDTO();
                    item.setIdProducto(d.getProducto().getId());
                    item.setCantidad(d.getCantidad());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private BigDecimal clampDescuento(BigDecimal descuento) {
        if (descuento == null || descuento.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (descuento.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        return descuento;
    }

    public List<Pedido> searchPedidos(String clienteNombre, EstadoPedido estado, Integer clienteId) {
        Criteria criteria = new Criteria();
        criteria.and("habilitado").ne(false);
        if (clienteId != null) {
            criteria.and("cliente._id").is(clienteId);
        }
        if (clienteNombre != null && !clienteNombre.isBlank()) {
            criteria.and("cliente.nombre").regex(clienteNombre, "i");
        }
        if (estado != null) {
            criteria.and("estado").is(estado.name());
        }
        Query query = new Query(criteria);
        return mongoTemplate.find(query, Pedido.class);
    }
}
