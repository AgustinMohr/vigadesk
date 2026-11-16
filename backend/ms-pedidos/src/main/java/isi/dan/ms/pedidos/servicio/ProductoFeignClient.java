package isi.dan.ms.pedidos.servicio;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import isi.dan.ms.pedidos.dto.StockUpdateDTO;
import isi.dan.ms.pedidos.modelo.Producto;

@FeignClient("ms-productos")
public interface ProductoFeignClient {

    @GetMapping("/api/productos/{id}")
    Producto getProducto(@PathVariable("id") Long id, @RequestHeader("X-User-Rol") String rol);

    @PutMapping("/api/productos/stock/check")
    Boolean checkStock(@RequestBody List<StockUpdateDTO> items);

    @PutMapping("/api/productos/stock/reserve")
    Boolean reserveStock(@RequestBody List<StockUpdateDTO> items);

    @PutMapping("/api/productos/stock/release")
    void releaseStock(@RequestBody List<StockUpdateDTO> items);
}
