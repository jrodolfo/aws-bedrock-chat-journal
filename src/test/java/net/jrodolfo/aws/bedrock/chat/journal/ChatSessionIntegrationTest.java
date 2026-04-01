package net.jrodolfo.aws.bedrock.chat.journal;

import java.nio.file.Files;
import java.nio.file.Path;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatSessionIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.sessions-directory", () -> tempDir.resolve("sessions").toString());
        registry.add("app.aws.region", () -> "us-east-1");
        registry.add("app.aws.default-model-id", () -> "amazon.nova-lite-v1:0");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private BedrockRuntimeClient bedrockRuntimeClient;

    @Test
    void createAndSendMessagePersistsConversationToDisk() throws Exception {
        reset(bedrockRuntimeClient);
        when(bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenReturn(
                ConverseResponse.builder()
                        .output(ConverseOutput.builder()
                                .message(Message.builder()
                                        .role(ConversationRole.ASSISTANT)
                                        .content(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.fromText("Mocked Bedrock reply"))
                                        .build())
                                .build())
                        .build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<CreateSessionResponse> createResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/sessions",
                new HttpEntity<>("""
                        {
                          "modelId": "amazon.nova-lite-v1:0",
                          "systemPrompt": "You are a helpful AWS study assistant.",
                          "inferenceConfig": {
                            "temperature": 0.4,
                            "topP": 0.8,
                            "maxTokens": 256
                          }
                        }
                        """, headers),
                CreateSessionResponse.class
        );

        assertThat(createResponse.getStatusCode().is2xxSuccessful()).isTrue();
        CreateSessionResponse createdSession = createResponse.getBody();
        assertThat(createdSession).isNotNull();
        assertThat(createdSession.getMessageCount()).isZero();
        assertThat(createdSession.getInferenceConfig()).isNotNull();
        assertThat(createdSession.getInferenceConfig().getTemperature()).isEqualTo(0.4);

        ResponseEntity<String> sendResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/sessions/" + createdSession.getSessionId() + "/messages",
                new HttpEntity<>("""
                        {
                          "text": "Explain Converse API"
                        }
                        """, headers),
                String.class
        );

        assertThat(sendResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(sendResponse.getBody()).contains("Mocked Bedrock reply");

        Path sessionFile = tempDir.resolve("sessions").resolve(createdSession.getSessionId() + ".json");
        assertThat(Files.exists(sessionFile)).isTrue();

        String storedJson = Files.readString(sessionFile);
        assertThat(storedJson).contains(createdSession.getSessionId());
        assertThat(storedJson).contains("Explain Converse API");
        assertThat(storedJson).contains("Mocked Bedrock reply");
        assertThat(storedJson).contains("\"temperature\" : 0.4");
    }

    @Test
    void sendMessageFailureReturnsServerErrorAndDoesNotPersistNewMessages() throws Exception {
        reset(bedrockRuntimeClient);
        when(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .thenThrow(software.amazon.awssdk.core.exception.SdkClientException.create("Bedrock unavailable"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<CreateSessionResponse> createResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/sessions",
                new HttpEntity<>("""
                        {
                          "modelId": "amazon.nova-lite-v1:0",
                          "systemPrompt": "You are a helpful AWS study assistant."
                        }
                        """, headers),
                CreateSessionResponse.class
        );

        CreateSessionResponse createdSession = createResponse.getBody();
        assertThat(createdSession).isNotNull();

        ResponseEntity<String> sendResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/sessions/" + createdSession.getSessionId() + "/messages",
                new HttpEntity<>("""
                        {
                          "text": "This should fail"
                        }
                        """, headers),
                String.class
        );

        assertThat(sendResponse.getStatusCode().value()).isEqualTo(500);
        assertThat(sendResponse.getBody()).contains("Failed to call Amazon Bedrock");

        Path sessionFile = tempDir.resolve("sessions").resolve(createdSession.getSessionId() + ".json");
        String storedJson = Files.readString(sessionFile);
        assertThat(storedJson).doesNotContain("This should fail");
        assertThat(storedJson).doesNotContain("Failed to call Amazon Bedrock");
        assertThat(storedJson).contains("\"messages\" : [ ]");
    }

    @Test
    void updateResetAndDeleteSessionWorkThroughHttp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<CreateSessionResponse> createResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/sessions",
                new HttpEntity<>("""
                        {
                          "modelId": "amazon.nova-lite-v1:0",
                          "systemPrompt": "Original prompt"
                        }
                        """, headers),
                CreateSessionResponse.class
        );

        CreateSessionResponse createdSession = createResponse.getBody();
        assertThat(createdSession).isNotNull();

        ResponseEntity<ChatSession> updateResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/sessions/" + createdSession.getSessionId(),
                HttpMethod.PATCH,
                new HttpEntity<>("""
                        {
                          "modelId": "updated-model",
                          "systemPrompt": "Updated prompt"
                        }
                        """, headers),
                ChatSession.class
        );

        assertThat(updateResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(updateResponse.getBody()).isNotNull();
        assertThat(updateResponse.getBody().getModelId()).isEqualTo("updated-model");
        assertThat(updateResponse.getBody().getSystemPrompt()).isEqualTo("Updated prompt");

        ResponseEntity<ChatSession> resetResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/sessions/" + createdSession.getSessionId() + "/reset",
                HttpEntity.EMPTY,
                ChatSession.class
        );

        assertThat(resetResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resetResponse.getBody()).isNotNull();
        assertThat(resetResponse.getBody().getMessages()).isEmpty();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/sessions/" + createdSession.getSessionId(),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );

        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> getDeleted = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/sessions/" + createdSession.getSessionId(),
                String.class
        );

        assertThat(getDeleted.getStatusCode().value()).isEqualTo(404);
    }
}
