package dan.ms.eurekasrv;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "server.port=8761",
        "eureka.instance.hostname=localhost",
        "eureka.client.serviceUrl.defaultZone=http://localhost:8761/eureka/"
})
class EurekaSrvApplicationTests {

    @Test
    void contextLoads() {
    }
}
