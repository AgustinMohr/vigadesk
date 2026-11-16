package isi.dan.ms_productos.servicio;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import isi.dan.ms_productos.conf.RabbitMQConfig;
import isi.dan.ms_productos.dao.ProductoRepository;
import isi.dan.ms_productos.dto.DescuentoDTO;
import isi.dan.ms_productos.dto.ProvisionDTO;
import isi.dan.ms_productos.dto.StockUpdateDTO;
import isi.dan.ms_productos.exception.InsufficientStockException;
import isi.dan.ms_productos.exception.ProductoNotFoundException;
import isi.dan.ms_productos.modelo.Producto;
import isi.dan.ms_productos.modelo.UnidadMedida;

@Service
public class ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    Logger log = LoggerFactory.getLogger(ProductoService.class);

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.STOCK_UPDATE_QUEUE)
    public void handleStockUpdate(Message msg) {
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        log.info("Procesando descuento de stock: {}", body);

        try {
            StockUpdateDTO item = parseStockMessage(body);
            Producto producto = productoRepository.findByIdAndHabilitadoTrue(item.getIdProducto())
                    .orElseThrow(() -> new ProductoNotFoundException(item.getIdProducto()));

            if (producto.getStockActual().compareTo(item.getCantidad()) < 0) {
                throw new InsufficientStockException("Stock insuficiente para el producto ID " + item.getIdProducto()
                        + ". Actual: " + producto.getStockActual() + ", Solicitado: " + item.getCantidad());
            }

            producto.setStockActual(producto.getStockActual().subtract(item.getCantidad()));
            marcarAuditoria(producto, "ms-pedidos");
            productoRepository.save(producto);
            log.info("Stock descontado para producto {}. Nuevo stock: {}", item.getIdProducto(), producto.getStockActual());

        } catch (IllegalArgumentException | ProductoNotFoundException | InsufficientStockException e) {
            log.error("Error irrecuperable procesando descuento de stock: {}. Mensaje descartado.", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Mensaje descartado por datos invalidos", e);
        } catch (Exception e) {
            log.error("Error inesperado procesando descuento de stock: {}", body, e);
            throw e;
        }
    }

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.STOCK_RETURN_QUEUE)
    public void handleStockReturn(Message msg) {
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        log.info("Procesando devolucion de stock: {}", body);

        try {
            StockUpdateDTO item = parseStockMessage(body);
            Producto producto = productoRepository.findByIdAndHabilitadoTrue(item.getIdProducto())
                    .orElseThrow(() -> new ProductoNotFoundException(item.getIdProducto()));

            producto.setStockActual(producto.getStockActual().add(item.getCantidad()));
            marcarAuditoria(producto, "ms-pedidos");
            productoRepository.save(producto);
            log.info("Stock devuelto para producto {}. Nuevo stock: {}", item.getIdProducto(), producto.getStockActual());

        } catch (IllegalArgumentException | ProductoNotFoundException e) {
            log.error("Error irrecuperable procesando devolucion de stock: {}. Mensaje descartado.", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Mensaje descartado por datos invalidos", e);
        } catch (Exception e) {
            log.error("Error inesperado procesando devolucion de stock: {}", body, e);
            throw e;
        }
    }

    private StockUpdateDTO parseStockMessage(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("El cuerpo del mensaje esta vacio");
        }
        String[] parts = body.split(";");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Formato invalido. Se esperaba 'idProducto;cantidad'");
        }
        try {
            StockUpdateDTO item = new StockUpdateDTO();
            item.setIdProducto(Long.parseLong(parts[0].trim()));
            item.setCantidad(new BigDecimal(parts[1].trim()));
            validarItemStock(item);
            return item;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Error al parsear ID o cantidad en el mensaje: " + body, e);
        }
    }

    public boolean checkStock(List<StockUpdateDTO> items) {
        for (StockUpdateDTO item : items) {
            validarItemStock(item);
            Producto producto = productoRepository.findByIdAndHabilitadoTrue(item.getIdProducto())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + item.getIdProducto()));
            if (producto.getStockActual().compareTo(item.getCantidad()) < 0) {
                log.warn("Stock insuficiente para producto {}: disponible {}, requerido {}",
                        producto.getId(), producto.getStockActual(), item.getCantidad());
                return false;
            }
        }
        return true;
    }

    /**
     * Reserva atomica de stock para todas las lineas del pedido. Si una linea
     * no tiene stock suficiente, la transaccion revierte las anteriores.
     */
    @Transactional
    public boolean reserveStock(List<StockUpdateDTO> items) {
        for (StockUpdateDTO item : items) {
            validarItemStock(item);
            int actualizados = productoRepository.descontarStock(
                    item.getIdProducto(), item.getCantidad(), "ms-pedidos", Instant.now());
            if (actualizados == 0) {
                throw new InsufficientStockException(
                        "Stock insuficiente para el producto ID " + item.getIdProducto());
            }
        }
        return true;
    }

    @Transactional
    public void releaseStock(List<StockUpdateDTO> items) {
        for (StockUpdateDTO item : items) {
            validarItemStock(item);
            int actualizados = productoRepository.reponerStock(
                    item.getIdProducto(), item.getCantidad(), "ms-pedidos", Instant.now());
            if (actualizados == 0) {
                throw new ProductoNotFoundException(item.getIdProducto());
            }
        }
    }

    private void validarItemStock(StockUpdateDTO item) {
        if (item.getIdProducto() == null) {
            throw new IllegalArgumentException("El producto es obligatorio");
        }
        if (item.getCantidad() == null || item.getCantidad().signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }
    }

    public Producto saveProducto(Producto producto) {
        return saveProducto(producto, "sistema");
    }

    public Producto saveProducto(Producto producto, String actor) {
        producto.setStockActual(BigDecimal.ZERO);
        producto.setHabilitado(true);
        if (producto.getStockMinimo() == null) {
            producto.setStockMinimo(BigDecimal.ZERO);
        }
        if (producto.getUnidadMedida() == null) {
            producto.setUnidadMedida(UnidadMedida.UNIDAD);
        }
        if (producto.getDescuentoPromocional() == null) {
            producto.setDescuentoPromocional(BigDecimal.ZERO);
        }
        validarDescuento(producto.getDescuentoPromocional());
        marcarAuditoria(producto, actor);
        return productoRepository.save(producto);
    }

    @Transactional
    public Producto procesarProvision(ProvisionDTO provision) {
        return procesarProvision(provision, "sistema");
    }

    @Transactional
    public Producto procesarProvision(ProvisionDTO provision, String actor) {
        if (provision.getCantidad() == null || provision.getCantidad().signum() <= 0) {
            throw new IllegalArgumentException("La cantidad a reponer debe ser mayor a cero");
        }
        if (provision.getPrecio() != null && provision.getPrecio().signum() < 0) {
            throw new IllegalArgumentException("El precio debe ser mayor o igual a 0");
        }
        Producto producto = productoRepository.findByIdAndHabilitadoTrue(provision.getIdProducto())
                .orElseThrow(() -> new ProductoNotFoundException(provision.getIdProducto()));
        producto.setStockActual(producto.getStockActual().add(provision.getCantidad()));
        if (provision.getPrecio() != null) {
            producto.setPrecio(provision.getPrecio());
        }
        marcarAuditoria(producto, actor);
        return productoRepository.save(producto);
    }

    public Producto actualizarDescuento(Long id, DescuentoDTO descuento) {
        return actualizarDescuento(id, descuento, "sistema");
    }

    public Producto actualizarDescuento(Long id, DescuentoDTO descuento, String actor) {
        validarDescuento(descuento.getDescuentoPromocional());
        Producto producto = productoRepository.findByIdAndHabilitadoTrue(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + id));
        producto.setDescuentoPromocional(descuento.getDescuentoPromocional());
        marcarAuditoria(producto, actor);
        return productoRepository.save(producto);
    }

    public List<Producto> getAllProductos() {
        return productoRepository.findByHabilitadoTrue();
    }

    public Producto getProductoById(Long id) throws ProductoNotFoundException {
        return productoRepository.findByIdAndHabilitadoTrue(id)
                .orElseThrow(() -> new ProductoNotFoundException(id));
    }

    public void deleteProducto(Long id) {
        productoRepository.findByIdAndHabilitadoTrue(id).ifPresent(producto -> {
            producto.setHabilitado(false);
            producto.setFechaBorrado(Instant.now());
            productoRepository.save(producto);
        });
    }

    public Producto updateProducto(Long id, Producto productoActualizado) {
        return updateProducto(id, productoActualizado, "sistema");
    }

    public Producto updateProducto(Long id, Producto productoActualizado, String actor) {
        Producto producto = productoRepository.findByIdAndHabilitadoTrue(id)
                .orElseThrow(() -> new ProductoNotFoundException(id));
        producto.setCodigo(productoActualizado.getCodigo());
        producto.setNombre(productoActualizado.getNombre());
        producto.setDescripcion(productoActualizado.getDescripcion());
        producto.setPrecio(productoActualizado.getPrecio());
        producto.setStockMinimo(productoActualizado.getStockMinimo() == null
                ? BigDecimal.ZERO : productoActualizado.getStockMinimo());
        producto.setCategoria(productoActualizado.getCategoria());
        if (productoActualizado.getUnidadMedida() != null) {
            producto.setUnidadMedida(productoActualizado.getUnidadMedida());
        }
        producto.setCosto(productoActualizado.getCosto());
        if (productoActualizado.getDescuentoPromocional() != null) {
            validarDescuento(productoActualizado.getDescuentoPromocional());
            producto.setDescuentoPromocional(productoActualizado.getDescuentoPromocional());
        }
        marcarAuditoria(producto, actor);
        return productoRepository.save(producto);
    }

    private void marcarAuditoria(Producto producto, String actor) {
        producto.setActualizadoPor(actor);
        producto.setFechaActualizacion(Instant.now());
    }

    private void validarDescuento(BigDecimal descuento) {
        if (descuento == null) {
            throw new IllegalArgumentException("El descuento promocional es obligatorio");
        }
        if (descuento.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El descuento promocional debe ser mayor o igual a 0");
        }
        if (descuento.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("El descuento promocional debe ser menor o igual a 100");
        }
    }

    public List<Producto> searchProductos(String nombre, String codigo,
                                           BigDecimal precioMin, BigDecimal precioMax,
                                           BigDecimal stockMin, BigDecimal stockMax) {
        Specification<Producto> spec = Specification.where((root, query, cb) ->
                cb.isTrue(root.get("habilitado")));
        if (nombre != null && !nombre.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("nombre")), "%" + nombre.toLowerCase() + "%"));
        }
        if (codigo != null && !codigo.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("codigo")), "%" + codigo.toLowerCase() + "%"));
        }
        if (precioMin != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("precio"), precioMin));
        }
        if (precioMax != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("precio"), precioMax));
        }
        if (stockMin != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("stockActual"), stockMin));
        }
        if (stockMax != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("stockActual"), stockMax));
        }
        return productoRepository.findAll(spec);
    }
}
