package isi.dan.ms.pedidos.servicio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import feign.FeignException;
import isi.dan.ms.pedidos.conf.RabbitMQConfig;
import isi.dan.ms.pedidos.dao.PedidoRepository;
import isi.dan.ms.pedidos.dto.StockUpdateDTO;
import isi.dan.ms.pedidos.exception.PedidoNotFoundException;
import isi.dan.ms.pedidos.modelo.DetallePedido;
import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Pedido;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ClienteFeignClient clienteFeignClient;

    @Autowired
    private ProductoFeignClient productoFeignClient;

    Logger log = LoggerFactory.getLogger(PedidoService.class);

    public Pedido savePedido(Pedido pedido) {
        // Monto de cada línea y total del pedido: por línea se calcula
        // precioFinal = precioUnitario * cantidad * (1 - descuento/100).
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedido detalle : pedido.getDetalle()) {
            BigDecimal precioUnitario = detalle.getProducto().getPrecio();
            BigDecimal descuento = detalle.getProducto().getDescuentoPromocional() != null
                    ? detalle.getProducto().getDescuentoPromocional()
                    : BigDecimal.ZERO;
            BigDecimal precioFinal = precioUnitario
                    .multiply(BigDecimal.valueOf(detalle.getCantidad()))
                    .multiply(BigDecimal.ONE.subtract(descuento.divide(BigDecimal.valueOf(100))));

            detalle.setPrecioUnitario(precioUnitario);
            detalle.setDescuento(descuento);
            detalle.setPrecioFinal(precioFinal);
            total = total.add(precioFinal);
        }
        pedido.setTotal(total);
        pedido.setFecha(Instant.now());
        pedido.setNumeroPedido(Math.abs((int) (Instant.now().toEpochMilli() % 1000000)));

        BigDecimal maximoDescubierto;
        try {
            maximoDescubierto = clienteFeignClient.getMaximoDescubierto(pedido.getCliente().getId());
        } catch (Exception e) {
            log.error("Error al consultar maximoDescubierto del cliente {}", pedido.getCliente().getId(), e);
            pedido.setEstado(EstadoPedido.RECIBIDO);
            return pedidoRepository.save(pedido);
        }

        List<Pedido> existentes = pedidoRepository.findByCliente_IdAndEstadoInActivos(
                pedido.getCliente().getId(),
                Arrays.asList(EstadoPedido.ACEPTADO, EstadoPedido.EN_PREPARACION));

        BigDecimal gastado = existentes.stream()
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
		
		try {
			//Checkeo de que la suma de los pedidos ACEPTADOS y EN_PREPARACION mas el total del pedido actual no supere el MAXIMO DESCUBIERTO
			if (!(clienteFeignClient.checkSaldo(pedido.getCliente().getId(), gastado.add(total)))){
			log.warn("Saldo insuficiente: gastado {}, nuevo {}, maximoDescubierto {}", gastado, total, maximoDescubierto);
			pedido.setEstado(EstadoPedido.RECHAZADO);
			return pedidoRepository.save(pedido);
		}
		} catch (FeignException.NotFound e) {
			// Si ms-clientes devuelve 404 porque el cliente no existe
			throw new IllegalArgumentException("El cliente " + pedido.getCliente().getId() + " no existe.");
		} catch (FeignException e) {
			// Si ms-clientes cayó o devolvió 500
			throw new IllegalStateException("Error al comunicarse con el servicio de clientes: " + e.getMessage());
		}

        pedido.setEstado(EstadoPedido.ACEPTADO);

        List<StockUpdateDTO> stockItems = pedido.getDetalle().stream()
                .map(d -> {
                    StockUpdateDTO item = new StockUpdateDTO();
                    item.setIdProducto(d.getProducto().getId());
                    item.setCantidad(d.getCantidad());
                    return item;
                })
                .collect(Collectors.toList());

        try {
            if (productoFeignClient.checkStock(stockItems)) {
                for (DetallePedido dp : pedido.getDetalle()) {
                    String msg = dp.getProducto().getId() + ";" + dp.getCantidad();
                    log.info("Enviando actualización de stock {}", msg);
                    rabbitTemplate.convertAndSend(RabbitMQConfig.STOCK_UPDATE_QUEUE, msg);
                }
                pedido.setEstado(EstadoPedido.EN_PREPARACION);
            } else {
                log.warn("Stock insuficiente, pedido queda en ACEPTADO");
            }
        } catch (Exception e) {
            log.error("Error al verificar stock para pedido {}", pedido.getNumeroPedido(), e);
        }

        return pedidoRepository.save(pedido);
    }

    public List<Pedido> getAllPedidos() {
        return pedidoRepository.findAllActivos();
    }

    public Pedido getPedidoById(String id) {
        return pedidoRepository.findByIdActivo(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));
    }

    public Pedido updateEstado(String id, EstadoPedido nuevoEstado) {
        if (nuevoEstado != EstadoPedido.ENTREGADO && nuevoEstado != EstadoPedido.CANCELADO) {
            throw new IllegalArgumentException("Solo se permite actualizar a ENTREGADO o CANCELADO");
        }
        Pedido pedido = pedidoRepository.findByIdActivo(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));
        if (nuevoEstado == EstadoPedido.CANCELADO) {
            for (DetallePedido dp : pedido.getDetalle()) {
                String msg = dp.getProducto().getId() + ";" + dp.getCantidad();
                log.info("Enviando devolucion de stock {}", msg);
                rabbitTemplate.convertAndSend(RabbitMQConfig.STOCK_RETURN_QUEUE, msg);
            }
        }
        pedido.setEstado(nuevoEstado);
        return pedidoRepository.save(pedido);
    }

    public void deletePedido(String id) {
        pedidoRepository.findByIdActivo(id).ifPresent(pedido -> {
            pedido.setHabilitado(false);
            pedido.setFechaBorrado(Instant.now());
            pedidoRepository.save(pedido);
        });
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
