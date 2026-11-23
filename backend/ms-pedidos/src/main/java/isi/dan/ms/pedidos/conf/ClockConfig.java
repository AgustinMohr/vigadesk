package isi.dan.ms.pedidos.conf;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj del servicio: se inyecta donde se necesita la fecha, para poder fijarlo en los tests. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
