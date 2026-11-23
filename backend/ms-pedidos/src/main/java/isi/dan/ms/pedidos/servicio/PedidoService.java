package isi.dan.ms.pedidos.servicio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import feign.FeignException;
import dan.common.web.ParametrosConsulta;
import isi.dan.ms.pedidos.dao.PedidoRepository;
import isi.dan.ms.pedidos.dto.ContextoPedidoDTO;
import isi.dan.ms.pedidos.dto.EntregaItemRequest;
import isi.dan.ms.pedidos.dto.EntregaRequest;
import isi.dan.ms.pedidos.dto.StockUpdateDTO;
import isi.dan.ms.pedidos.exception.ConflictException;
import isi.dan.ms.pedidos.exception.ExternalServiceUnavailableException;
import isi.dan.ms.pedidos.exception.PedidoNotFoundException;
import isi.dan.ms.pedidos.exception.UnprocessableEntityException;
import isi.dan.ms.pedidos.modelo.AuditLog;
import isi.dan.ms.pedidos.modelo.Cliente;
import isi.dan.ms.pedidos.modelo.DetallePedido;
import isi.dan.ms.pedidos.modelo.Entrega;
import isi.dan.ms.pedidos.modelo.EntregaItem;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Obra;
import isi.dan.ms.pedidos.modelo.Pedido;
import isi.dan.ms.pedidos.modelo.Producto;

@Service
public class PedidoService {

    private static final List<EstadoPedido> ESTADOS_QUE_CONSUMEN_CREDITO = List.of(
            EstadoPedido.ACEPTADO,
            EstadoPedido.EN_PREPARACION,
            EstadoPedido.ENTREGA_PARCIAL,
            EstadoPedido.ENTREGADO);

    private final PedidoRepository pedidoRepository;
    private final MongoTemplate mongoTemplate;
    private final SecuenciaService secuenciaService;
    private final CreditoService creditoService;
    private final ClienteFeignClient clienteFeignClient;
    private final ProductoFeignClient productoFeignClient;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidoRepository, MongoTemplate mongoTemplate,
            SecuenciaService secuenciaService, CreditoService creditoService,
            ClienteFeignClient clienteFeignClient, ProductoFeignClient productoFeignClient, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.mongoTemplate = mongoTemplate;
        this.secuenciaService = secuenciaService;
        this.creditoService = creditoService;
        this.clienteFeignClient = clienteFeignClient;
        this.productoFeignClient = productoFeignClient;
        this.clock = clock;
    }

    Logger log = LoggerFactory.getLogger(PedidoService.class);

    public Pedido savePedido(Pedido pedido) {
        return savePedido(pedido, "ADMIN", null, "sistema");
    }

    public Pedido savePedido(Pedido pedido, String rol, String idempotencyKey, String actor) {
        String effectiveKey = normalizarIdempotencyKey(idempotencyKey);
        validarPedido(pedido);
        // La huella se calcula sobre lo que mando quien pide, antes de que el servidor complete datos.
        String huella = huellaDe(pedido);
        Optional<Pedido> existente = pedidoRepository
                .findByClienteIdAndIdempotencyKey(pedido.getCliente().getId(), effectiveKey);
        if (existente.isPresent()) {
            return reutilizar(existente.get(), huella, effectiveKey);
        }

        completarClienteYObra(pedido);
        pedido.setIdempotencyHash(huella);
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
        pedido.setFecha(Instant.now(clock));
        pedido.setNumeroPedido(secuenciaService.nextNumeroPedido());

        // El control de credito y el guardado van bajo el bloqueo del cliente: dos pedidos
        // simultaneos no pueden leer el mismo consumo y superar el maximo.
        final BigDecimal totalPedido = total;
        return creditoService.conBloqueo(pedido.getCliente().getId(),
                () -> evaluarCreditoYGuardar(pedido, actor, effectiveKey, totalPedido));
    }

    private Pedido evaluarCreditoYGuardar(Pedido pedido, String actor, String effectiveKey, BigDecimal total) {
        BigDecimal maximoDescubierto = obtenerMaximoDescubierto(pedido.getCliente().getId());
        BigDecimal gastado = creditoService.consumo(pedido.getCliente().getId(), ESTADOS_QUE_CONSUMEN_CREDITO);

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

    public Pedido reintentarReserva(String id, String actor) {
        Pedido pedido = pedidoRepository.findByIdActivo(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));

        if (pedido.getEstado() != EstadoPedido.ACEPTADO) {
            throw new ConflictException("Solo se puede reintentar la reserva de un pedido ACEPTADO (pendiente de stock)");
        }

        inicializarColecciones(pedido);
        try {
            productoFeignClient.reserveStock(buildStockItems(pedido));
        } catch (FeignException.NotFound e) {
            throw new UnprocessableEntityException("No se encontro uno de los productos del pedido");
        } catch (FeignException.Conflict e) {
            auditar(pedido, actor, "RESERVA_REINTENTO_SIN_STOCK", "Stock insuficiente al reintentar la reserva");
            pedidoRepository.save(pedido);
            throw new ConflictException("Stock insuficiente: el pedido sigue pendiente de stock");
        } catch (Exception e) {
            log.error("Error al reintentar la reserva del pedido {}", pedido.getNumeroPedido(), e);
            throw new ExternalServiceUnavailableException(
                    "No se pudo reservar el stock del pedido " + pedido.getNumeroPedido(), e);
        }

        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        auditar(pedido, actor, "RESERVA_CONFIRMADA", "Stock reservado, estado EN_PREPARACION");
        return pedidoRepository.save(pedido);
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
        entrega.setFecha(Instant.now(clock));
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
            Optional<Pedido> existente = pedidoRepository
                    .findByClienteIdAndIdempotencyKey(pedido.getCliente().getId(), idempotencyKey);
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
                return reutilizar(existente.get(), pedido.getIdempotencyHash(), idempotencyKey);
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

    /**
     * El cliente y la obra los decide el servidor: se consultan en ms-clientes, la obra debe existir,
     * estar HABILITADA y pertenecer al cliente. Nombre y CUIT del cliente salen de ahi, no del request.
     */
    private void completarClienteYObra(Pedido pedido) {
        Integer clienteId = pedido.getCliente().getId();
        if (pedido.getObra() == null || pedido.getObra().getId() == null) {
            throw new UnprocessableEntityException("La obra es obligatoria");
        }
        Integer obraId = pedido.getObra().getId();

        ContextoPedidoDTO contexto;
        try {
            contexto = clienteFeignClient.getContextoPedido(clienteId, obraId);
        } catch (FeignException.NotFound e) {
            throw new UnprocessableEntityException("El cliente " + clienteId + " no existe");
        } catch (Exception e) {
            log.error("No se pudo validar el cliente {} y la obra {}", clienteId, obraId, e);
            throw new ExternalServiceUnavailableException(
                    "No se pudo validar el cliente " + clienteId + " y la obra " + obraId, e);
        }
        if (contexto == null || contexto.getObraId() == null) {
            throw new UnprocessableEntityException("La obra " + obraId + " no existe");
        }
        if (!clienteId.equals(contexto.getObraClienteId())) {
            throw new UnprocessableEntityException("La obra " + obraId + " no pertenece al cliente " + clienteId);
        }
        if (!"HABILITADA".equals(contexto.getObraEstado())) {
            throw new UnprocessableEntityException("La obra " + obraId + " no esta habilitada");
        }

        Cliente cliente = new Cliente();
        cliente.setId(contexto.getClienteId());
        cliente.setNombre(contexto.getClienteNombre());
        cliente.setCorreoElectronico(contexto.getClienteCorreoElectronico());
        cliente.setCuit(contexto.getClienteCuit());
        pedido.setCliente(cliente);

        Obra obra = new Obra();
        obra.setId(contexto.getObraId());
        obra.setDireccion(contexto.getObraDireccion());
        pedido.setObra(obra);
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
        evento.setFecha(Instant.now(clock));
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

    /** Misma clave y mismo payload: devuelve el pedido ya creado. Misma clave y otro payload: 409. */
    private Pedido reutilizar(Pedido existente, String huella, String idempotencyKey) {
        // Los pedidos anteriores a la huella no la tienen: se reutilizan como antes.
        if (existente.getIdempotencyHash() != null && !existente.getIdempotencyHash().equals(huella)) {
            throw new ConflictException(
                    "La Idempotency-Key ya se uso con un pedido distinto. Use una clave nueva para un pedido nuevo");
        }
        log.info("Pedido idempotente reutilizado con key {}", idempotencyKey);
        return existente;
    }

    /** Huella SHA-256 del payload (cliente, obra, observaciones y lineas), independiente del orden de lineas. */
    private String huellaDe(Pedido pedido) {
        List<String> lineas = pedido.getDetalle().stream()
                .map(d -> d.getProducto().getId() + ":" + d.getCantidad().stripTrailingZeros().toPlainString())
                .sorted()
                .collect(Collectors.toList());
        String canonico = pedido.getCliente().getId()
                + "|" + (pedido.getObra() == null ? "" : pedido.getObra().getId())
                + "|" + (pedido.getObservaciones() == null ? "" : pedido.getObservaciones().trim())
                + "|" + String.join(",", lineas);
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
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
            pedido.setFechaBorrado(Instant.now(clock));
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

    /**
     * Busca pedidos activos con paginacion. El texto del nombre se trata como literal (se escapa para que no
     * actue como expresion regular) y tiene longitud maxima. El orden es fecha descendente.
     */
    public Page<Pedido> buscarPedidos(String clienteNombre, EstadoPedido estado, Integer clienteId,
            Integer page, Integer size) {
        String nombre = ParametrosConsulta.texto(clienteNombre, "clienteNombre");
        Pageable pageable = PageRequest.of(ParametrosConsulta.pagina(page), ParametrosConsulta.tamanio(size),
                Sort.by(Sort.Direction.DESC, "fecha").and(Sort.by("id")));

        Criteria criteria = new Criteria();
        criteria.and("habilitado").ne(false);
        if (clienteId != null) {
            criteria.and("cliente._id").is(clienteId);
        }
        if (nombre != null) {
            criteria.and("cliente.nombre").regex(Pattern.quote(nombre), "i");
        }
        if (estado != null) {
            criteria.and("estado").is(estado.name());
        }
        long total = mongoTemplate.count(new Query(criteria), Pedido.class);
        List<Pedido> contenido = mongoTemplate.find(new Query(criteria).with(pageable), Pedido.class);
        return new PageImpl<>(contenido, pageable, total);
    }
}
