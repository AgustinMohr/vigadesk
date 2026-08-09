package isi.dan.ms_productos.servicio;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
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
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("El cuerpo del mensaje está vacío");
        }

        String[] parts = body.split(";");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Formato de mensaje inválido. Se esperaba 'idProducto;cantidad' pero se recibió: " + body);
        }

        Long idProducto;
        int cantidad;

        try {
            idProducto = Long.parseLong(parts[0].trim());
            cantidad = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Error al parsear ID o cantidad en el mensaje: " + body, e);
        }

        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a descontar debe ser mayor a cero. Cantidad recibida: " + cantidad);
        }

        Producto producto = productoRepository.findByIdAndHabilitadoTrue(idProducto)
                .orElseThrow(() -> new ProductoNotFoundException(idProducto));

        // Validación de negocio: Stock suficiente
        if (producto.getStockActual() < cantidad) {
            throw new InsufficientStockException("Stock insuficiente para el producto ID " + idProducto 
                    + ". Actual: " + producto.getStockActual() + ", Solicitado: " + cantidad);
        }

        producto.setStockActual(producto.getStockActual() - cantidad);
        productoRepository.save(producto);

        log.info("Stock descontado exitosamente para el producto ID {}. Nuevo stock: {}", idProducto, producto.getStockActual());

    } catch (IllegalArgumentException | ProductoNotFoundException | InsufficientStockException e) {
        log.error("Error irrecuperable procesando descuento de stock: {}. El mensaje será descartado.", e.getMessage());
        // Se lanza AmqpRejectAndDontRequeueException para descartar el mensaje y no entrar en loop infinito
        throw new AmqpRejectAndDontRequeueException("Mensaje descartado por datos inválidos, producto no encontrado o stock insuficiente", e);

    } catch (Exception e) {
        log.error("Error inesperado en la base de datos o sistema procesando el mensaje: {}", body, e);
        // Si es un error transitorio de la BD, se relanza la excepción para que actúen los reintentos
        throw e;
    }
}

    @Transactional
@RabbitListener(queues = RabbitMQConfig.STOCK_RETURN_QUEUE)
public void handleStockReturn(Message msg) {
    String body = new String(msg.getBody(), StandardCharsets.UTF_8);
    log.info("Procesando devolución de stock: {}", body);

    try {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("El cuerpo del mensaje está vacío");
        }

        String[] parts = body.split(";");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Formato de mensaje inválido. Se esperaba 'idProducto;cantidad' pero se recibió: " + body);
        }

        Long idProducto;
        int cantidad;

        try {
            idProducto = Long.parseLong(parts[0].trim());
            cantidad = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Error al parsear ID o cantidad en el mensaje: " + body, e);
        }

        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a devolver debe ser mayor a cero. Cantidad recibida: " + cantidad);
        }

        Producto producto = productoRepository.findByIdAndHabilitadoTrue(idProducto)
                .orElseThrow(() -> new ProductoNotFoundException(idProducto));

        producto.setStockActual(producto.getStockActual() + cantidad);
        productoRepository.save(producto);
        
        log.info("Stock actualizado exitosamente para el producto ID {}. Nuevo stock: {}", idProducto, producto.getStockActual());

    } catch (IllegalArgumentException | ProductoNotFoundException e) {
        log.error("Error irrecuperable procesando mensaje de devolución de stock: {}. El mensaje será descartado.", e.getMessage());
        // Lanza esta excepción para que RabbitMQ NO vuelva a encolar un mensaje corrupto o de un producto inexistente
        throw new AmqpRejectAndDontRequeueException("Mensaje descartado por datos inválidos o producto no encontrado", e);
        
    } catch (Exception e) {
        log.error("Error inesperado en la base de datos o sistema procesando el mensaje: {}", body, e);
        // Si lanzás cualquier otra excepción (o no la capturás con AmqpRejectAndDontRequeueException),
        // Spring ejecutará el mecanismo de reintentos configurado en tu application.yml
        throw e; 
    }
}

    public boolean checkStock(List<StockUpdateDTO> items) {
        for (StockUpdateDTO item : items) {
            Producto producto = productoRepository.findByIdAndHabilitadoTrue(item.getIdProducto())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + item.getIdProducto()));
            if (producto.getStockActual() < item.getCantidad()) {
                log.warn("Stock insuficiente para producto {}: disponible {}, requerido {}",
                        producto.getId(), producto.getStockActual(), item.getCantidad());
                return false;
            }
        }
        return true;
    }

    public Producto saveProducto(Producto producto) {
        producto.setStockActual(0);
        producto.setHabilitado(true);
        return productoRepository.save(producto);
    }

    @Transactional
    public Producto procesarProvision(ProvisionDTO provision) {
        Producto producto = productoRepository.findByIdAndHabilitadoTrue(provision.getIdProducto())
                .orElseThrow(() -> new ProductoNotFoundException(provision.getIdProducto()));
        producto.setStockActual(producto.getStockActual() + provision.getCantidad());
		if (provision.getPrecio() != null)
     		producto.setPrecio(provision.getPrecio());
        return productoRepository.save(producto);
    }

    public Producto actualizarDescuento(Long id, DescuentoDTO descuento) {
        Producto producto = productoRepository.findByIdAndHabilitadoTrue(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + id));
        producto.setDescuentoPromocional(descuento.getDescuentoPromocional());
        return productoRepository.save(producto);
    }

    public List<Producto> getAllProductos() {
        return productoRepository.findByHabilitadoTrue();
    }

    public Producto getProductoById(Long id) throws ProductoNotFoundException{
        return productoRepository.findByIdAndHabilitadoTrue(id).orElseThrow(() -> new ProductoNotFoundException(id));
    }

    public void deleteProducto(Long id) {
        productoRepository.findByIdAndHabilitadoTrue(id).ifPresent(producto -> {
            producto.setHabilitado(false);
            producto.setFechaBorrado(Instant.now());
            productoRepository.save(producto);
        });
    }

    public Producto updateProducto(Long id, Producto productoActualizado) {
        Producto producto = productoRepository.findByIdAndHabilitadoTrue(id)
                .orElseThrow(() -> new ProductoNotFoundException(id));
        producto.setCodigo(productoActualizado.getCodigo());
        producto.setNombre(productoActualizado.getNombre());
        producto.setDescripcion(productoActualizado.getDescripcion());
        producto.setPrecio(productoActualizado.getPrecio());
        producto.setStockMinimo(productoActualizado.getStockMinimo());
        producto.setCategoria(productoActualizado.getCategoria());
        producto.setDescuentoPromocional(productoActualizado.getDescuentoPromocional());
        return productoRepository.save(producto);
    }

    public List<Producto> searchProductos(String nombre, String codigo,
                                           BigDecimal precioMin, BigDecimal precioMax,
                                           Integer stockMin, Integer stockMax) {
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

