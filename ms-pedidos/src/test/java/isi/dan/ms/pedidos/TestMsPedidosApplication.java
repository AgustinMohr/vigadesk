package isi.dan.ms.pedidos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

// Configuración de test que levanta MongoDB y RabbitMQ reales con Testcontainers
@TestConfiguration(proxyBeanMethods = false)
public class TestMsPedidosApplication {

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDbContainer() {
		return new MongoDBContainer(DockerImageName.parse("mongo:latest"));
	}

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:latest"));
	}

	// Punto de entrada alternativo para correr la app con contenedores de test
	public static void main(String[] args) {
		SpringApplication.from(MsPedidosApplication::main).with(TestMsPedidosApplication.class).run(args);
	}

}
