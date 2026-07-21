package isi.dan.ms.pedidos.servicio;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import isi.dan.ms.pedidos.dto.StockUpdateDTO;

@FeignClient("ms-productos")
public interface ProductoFeignClient {

    @PutMapping("/api/productos/stock/check")
    Boolean checkStock(@RequestBody List<StockUpdateDTO> items);
}
