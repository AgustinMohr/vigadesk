package isi.dan.ms_productos.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;


import isi.dan.ms_productos.aop.LogExecutionTime;
import isi.dan.ms_productos.dto.DescuentoDTO;
import isi.dan.ms_productos.dto.ProvisionDTO;
import isi.dan.ms_productos.dto.StockUpdateDTO;
import isi.dan.ms_productos.exception.ProductoNotFoundException;
import isi.dan.ms_productos.modelo.Producto;
import isi.dan.ms_productos.servicio.ProductoService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {
    @Autowired
    private ProductoService productoService;

    Logger log = LoggerFactory.getLogger(ProductoController.class);


    @PostMapping
    @LogExecutionTime
    public ResponseEntity<Producto> createProducto(@RequestBody @Validated Producto producto) {
        Producto savedProducto = productoService.saveProducto(producto);
        return ResponseEntity.ok(savedProducto);
    }

    @GetMapping
    @LogExecutionTime
    public List<Producto> getAllProductos() {
        return productoService.getAllProductos();
    }

    @GetMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Producto> getProductoById(@PathVariable Long id) throws ProductoNotFoundException {
        return  ResponseEntity.ok(productoService.getProductoById(id));
    }

    @PutMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Producto> updateProducto(@PathVariable Long id, @RequestBody @Validated Producto producto) {
        return ResponseEntity.ok(productoService.updateProducto(id, producto));
    }

    @GetMapping("/search")
    @LogExecutionTime
    public List<Producto> searchProductos(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) BigDecimal precioMin,
            @RequestParam(required = false) BigDecimal precioMax,
            @RequestParam(required = false) Integer stockMin,
            @RequestParam(required = false) Integer stockMax) {
        return productoService.searchProductos(nombre, codigo, precioMin, precioMax, stockMin, stockMax);
    }

    @DeleteMapping("/{id}")
    @LogExecutionTime
    public ResponseEntity<Void> deleteProducto(@PathVariable Long id) {
        productoService.deleteProducto(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/stock/check")
    @LogExecutionTime
    public ResponseEntity<Boolean> checkStock(@RequestBody List<StockUpdateDTO> items) {
        return ResponseEntity.ok(productoService.checkStock(items));
    }

    @PutMapping("/provision")
    @LogExecutionTime
    public ResponseEntity<Producto> procesarProvision(@RequestBody @Validated ProvisionDTO provision) {
        return ResponseEntity.ok(productoService.procesarProvision(provision));
    }

    @PutMapping("/{id}/descuento")
    @LogExecutionTime
    public ResponseEntity<Producto> actualizarDescuento(@PathVariable Long id, @RequestBody @Validated DescuentoDTO descuento) {
        return ResponseEntity.ok(productoService.actualizarDescuento(id, descuento));
    }

}

