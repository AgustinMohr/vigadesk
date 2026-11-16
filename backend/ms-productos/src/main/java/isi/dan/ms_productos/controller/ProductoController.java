package isi.dan.ms_productos.controller;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import isi.dan.ms_productos.aop.LogExecutionTime;
import isi.dan.ms_productos.dto.DescuentoDTO;
import isi.dan.ms_productos.dto.ImportResult;
import isi.dan.ms_productos.dto.ProvisionDTO;
import isi.dan.ms_productos.dto.StockUpdateDTO;
import isi.dan.ms_productos.exception.ProductoNotFoundException;
import isi.dan.ms_productos.modelo.Producto;
import isi.dan.ms_productos.servicio.ProductoImportService;
import isi.dan.ms_productos.servicio.ProductoService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {
    @Autowired
    private ProductoService productoService;

    @Autowired
    private ProductoImportService productoImportService;

    @PostMapping
    @LogExecutionTime
    public ResponseEntity<Producto> createProducto(
            @RequestBody @Validated Producto producto,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        Producto savedProducto = productoService.saveProducto(producto, actor(userNombre, userEmail));
        return ResponseEntity.ok(savedProducto);
    }

    @PostMapping("/import")
    @LogExecutionTime
    public ResponseEntity<ImportResult> importarCsv(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo CSV es obligatorio");
        }
        return ResponseEntity.ok(productoImportService.importar(
                file.getInputStream(), actor(userNombre, userEmail)));
    }

    @GetMapping
    @LogExecutionTime
    public List<Producto> getAllProductos(
            @RequestHeader(value = "X-User-Rol", required = false) String rol) {
        return productoService.getAllProductos().stream()
                .map(producto -> vistaSegunRol(producto, rol))
                .toList();
    }

    @GetMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Producto> getProductoById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Rol", required = false) String rol) throws ProductoNotFoundException {
        return ResponseEntity.ok(vistaSegunRol(productoService.getProductoById(id), rol));
    }

    @PutMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Producto> updateProducto(
            @PathVariable Long id,
            @RequestBody @Validated Producto producto,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return ResponseEntity.ok(productoService.updateProducto(id, producto, actor(userNombre, userEmail)));
    }

    @GetMapping("/search")
    @LogExecutionTime
    public List<Producto> searchProductos(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) BigDecimal precioMin,
            @RequestParam(required = false) BigDecimal precioMax,
            @RequestParam(required = false) BigDecimal stockMin,
            @RequestParam(required = false) BigDecimal stockMax,
            @RequestHeader(value = "X-User-Rol", required = false) String rol) {
        return productoService.searchProductos(nombre, codigo, precioMin, precioMax, stockMin, stockMax).stream()
                .map(producto -> vistaSegunRol(producto, rol))
                .toList();
    }

    @DeleteMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Void> deleteProducto(@PathVariable Long id) throws ProductoNotFoundException {
        productoService.getProductoById(id);
        productoService.deleteProducto(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/stock/check")
    @LogExecutionTime
    public ResponseEntity<Boolean> checkStock(@RequestBody List<StockUpdateDTO> items) {
        return ResponseEntity.ok(productoService.checkStock(items));
    }

    @PutMapping("/stock/reserve")
    @LogExecutionTime
    public ResponseEntity<Boolean> reserveStock(@RequestBody List<StockUpdateDTO> items) {
        return ResponseEntity.ok(productoService.reserveStock(items));
    }

    @PutMapping("/stock/release")
    @LogExecutionTime
    public ResponseEntity<Void> releaseStock(@RequestBody List<StockUpdateDTO> items) {
        productoService.releaseStock(items);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/provision")
    @LogExecutionTime
    public ResponseEntity<Producto> procesarProvision(
            @RequestBody @Validated ProvisionDTO provision,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return ResponseEntity.ok(productoService.procesarProvision(provision, actor(userNombre, userEmail)));
    }

    @PutMapping("/{id}/descuento")
    @LogExecutionTime
    public ResponseEntity<Producto> actualizarDescuento(
            @PathVariable Long id,
            @RequestBody @Validated DescuentoDTO descuento,
            @RequestHeader(value = "X-User-Nombre", required = false) String userNombre,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return ResponseEntity.ok(productoService.actualizarDescuento(id, descuento, actor(userNombre, userEmail)));
    }

    /**
     * El costo (margen del corralon) y el usuario que modifico el producto son datos
     * internos: solo los ve un ADMIN. Para cualquier otro rol (o sin rol) se devuelve una
     * COPIA sin esos campos. Nunca se modifica la entidad original: si estuviera
     * gestionada por Hibernate, el null podria terminar guardado en la base.
     */
    private Producto vistaSegunRol(Producto producto, String rol) {
        if ("ADMIN".equalsIgnoreCase(rol)) {
            return producto;
        }
        Producto vista = new Producto();
        BeanUtils.copyProperties(producto, vista);
        vista.setCosto(null);
        vista.setActualizadoPor(null);
        return vista;
    }

    private String actor(String userNombre, String userEmail) {
        if (userNombre != null && !userNombre.isBlank()) {
            return userNombre.trim();
        }
        if (userEmail != null && !userEmail.isBlank()) {
            return userEmail.trim();
        }
        return "sistema";
    }
}

