package net.jrodolfo.aws.bedrock.chat.journal;

import java.nio.file.Files;
import java.nio.file.Path;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

        ResponseEntity<ChatSession> createResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/sessions",
                new HttpEntity<>("""
                        {
                          "modelId": "amazon.nova-lite-v1:0",
                          "systemPrompt": "You are a helpful AWS study assistant."
                        }
                        """, headers),
                ChatSession.class
        );

        assertThat(createResponse.getStatusCode().is2xxSuccessful()).isTrue();
        ChatSession createdSession = createResponse.getBody();
        assertThat(createdSession).isNotNull();

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
    }

    @Test
    void sendMessageFailureReturnsServerErrorAndDoesNotPersistNewMessages() throws Exception {
        reset(bedrockRuntimeClient);
        when(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .thenThrow(software.amazon.awssdk.core.exception.SdkClientException.create("Bedrock unavailable"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<ChatSession> createResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/sessions",
                new HttpEntity<>("""
                        {
                          "modelId": "amazon.nova-lite-v1:0",
                          "systemPrompt": "You are a helpful AWS study assistant."
                        }
                        """, headers),
                ChatSession.class
        );

        ChatSession createdSession = createResponse.getBody();
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
}
