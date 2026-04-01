package net.jrodolfo.aws.bedrock.chat.journal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void openApiJsonEndpointIsAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/v3/api-docs",
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("AWS Bedrock Chat Journal API");
        assertThat(response.getBody()).contains("/api/sessions");
    }

    @Test
    void swaggerUiEndpointIsAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/swagger-ui.html",
                String.class
        );

        assertThat(response.getStatusCode().is3xxRedirection() || response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
