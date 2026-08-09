package isi.dan.ms.pedidos.servicio;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("ms-clientes")
public interface ClienteFeignClient {

    @GetMapping("/api/clientes/{id}/maximo-descubierto")
    BigDecimal getMaximoDescubierto(@PathVariable("id") Integer idCliente);
	
	@GetMapping("/api/clientes/{id}/saldo")
    Boolean checkSaldo(@PathVariable("id") Integer id, @RequestParam("monto") BigDecimal monto);
	
}
