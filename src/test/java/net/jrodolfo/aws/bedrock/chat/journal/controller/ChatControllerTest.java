package net.jrodolfo.aws.bedrock.chat.journal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import net.jrodolfo.aws.bedrock.chat.journal.config.RequestCorrelationFilter;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BedrockInvocationException;
import net.jrodolfo.aws.bedrock.chat.journal.exception.GlobalExceptionHandler;
import net.jrodolfo.aws.bedrock.chat.journal.exception.ResourceNotFoundException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatMessage;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.InferenceConfig;
import net.jrodolfo.aws.bedrock.chat.journal.model.ResponseMetadata;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageResponse;
import net.jrodolfo.aws.bedrock.chat.journal.model.UpdateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.service.ChatSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ChatController.class, HealthController.class})
@Import(GlobalExceptionHandler.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatSessionService chatSessionService;

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void healthEndpointPreservesIncomingRequestIdHeader() throws Exception {
        mockMvc.perform(get("/api/health")
                        .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "req-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestCorrelationFilter.REQUEST_ID_HEADER, "req-123"));
    }

    @Test
    void createSessionReturnsCreatedSessionSummary() throws Exception {
        ChatSession session = new ChatSession("session-1", "amazon.nova-lite-v1:0", "You are a tutor.", new InferenceConfig(0.3, 0.8, 256), new ArrayList<>());
        Mockito.when(chatSessionService.createSession(any(CreateSessionRequest.class))).thenReturn(session);

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelId": "amazon.nova-lite-v1:0",
                                  "systemPrompt": "You are a tutor."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.modelId").value("amazon.nova-lite-v1:0"))
                .andExpect(jsonPath("$.systemPrompt").value("You are a tutor."))
                .andExpect(jsonPath("$.inferenceConfig.temperature").value(0.3))
                .andExpect(jsonPath("$.inferenceConfig.topP").value(0.8))
                .andExpect(jsonPath("$.inferenceConfig.maxTokens").value(256))
                .andExpect(jsonPath("$.messageCount").value(0));
    }

    @Test
    void createSessionReturnsBadRequestWhenInferenceConfigIsInvalid() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inferenceConfig": {
                                    "temperature": 1.5
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("inferenceConfig.temperature: temperature must be at most 1.0"));
    }

    @Test
    void createSessionReturnsBadRequestWhenSystemPromptIsTooLong() throws Exception {
        String longPrompt = "a".repeat(4001);

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "systemPrompt": "%s"
                                }
                                """.formatted(longPrompt)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("systemPrompt: systemPrompt must be at most 4000 characters"));
    }

    @Test
    void getSessionReturnsStoredSession() throws Exception {
        ChatSession session = new ChatSession("session-1", "amazon.nova-lite-v1:0", null, null,
                new ArrayList<>(java.util.List.of(ChatMessage.userText("Hello"))));
        Mockito.when(chatSessionService.getSession("session-1")).thenReturn(session);

        mockMvc.perform(get("/api/sessions/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content[0].text").value("Hello"));
    }

    @Test
    void getSessionReturnsNotFoundWhenSessionIsMissing() throws Exception {
        Mockito.when(chatSessionService.getSession("missing"))
                .thenThrow(new ResourceNotFoundException("Session not found: missing"));

        mockMvc.perform(get("/api/sessions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Session not found: missing"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateSessionReturnsUpdatedSession() throws Exception {
        ChatSession session = new ChatSession("session-1", "updated-model", "Updated prompt", new InferenceConfig(0.5, 0.7, 300), new ArrayList<>());
        Mockito.when(chatSessionService.updateSession(eq("session-1"), any(UpdateSessionRequest.class))).thenReturn(session);

        mockMvc.perform(patch("/api/sessions/session-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelId": "updated-model",
                                  "systemPrompt": "Updated prompt",
                                  "inferenceConfig": {
                                    "temperature": 0.5,
                                    "topP": 0.7,
                                    "maxTokens": 300
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("updated-model"))
                .andExpect(jsonPath("$.systemPrompt").value("Updated prompt"))
                .andExpect(jsonPath("$.inferenceConfig.maxTokens").value(300));
    }

    @Test
    void updateSessionReturnsBadRequestWhenModelIdTooLong() throws Exception {
        String longModelId = "a".repeat(201);

        mockMvc.perform(patch("/api/sessions/session-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelId": "%s"
                                }
                                """.formatted(longModelId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("modelId: modelId must be at most 200 characters"));
    }

    @Test
    void resetSessionReturnsResetSession() throws Exception {
        ChatSession session = new ChatSession("session-1", "amazon.nova-lite-v1:0", "prompt", null, new ArrayList<>());
        Mockito.when(chatSessionService.resetSession("session-1")).thenReturn(session);

        mockMvc.perform(post("/api/sessions/session-1/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.messages").isEmpty());
    }

    @Test
    void deleteSessionReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/sessions/session-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void sendMessageReturnsAssistantReply() throws Exception {
        ResponseMetadata metadata = new ResponseMetadata();
        metadata.setStopReason("end_turn");
        metadata.setDurationMs(123L);
        SendMessageResponse response = new SendMessageResponse(
                "session-1",
                "amazon.nova-lite-v1:0",
                "Bedrock answer",
                ChatMessage.assistantText("Bedrock answer", metadata),
                metadata
        );
        Mockito.when(chatSessionService.sendMessage(eq("session-1"), any())).thenReturn(response);

        mockMvc.perform(post("/api/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Explain Converse API"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Bedrock answer"))
                .andExpect(jsonPath("$.assistantMessage.role").value("assistant"))
                .andExpect(jsonPath("$.metadata.stopReason").value("end_turn"));
    }

    @Test
    void streamMessageReturnsServerSentEvents() throws Exception {
        ResponseMetadata metadata = new ResponseMetadata();
        metadata.setStopReason("end_turn");
        SendMessageResponse response = new SendMessageResponse(
                "session-1",
                "amazon.nova-lite-v1:0",
                "Bedrock stream answer",
                ChatMessage.assistantText("Bedrock stream answer", metadata),
                metadata
        );
        Mockito.when(chatSessionService.streamMessage(eq("session-1"), any(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));

        MvcResult result = mockMvc.perform(post("/api/sessions/session-1/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "text": "Stream this"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("event:complete")));
    }

    @Test
    void sendMessageReturnsBadRequestForBlankText() throws Exception {
        mockMvc.perform(post("/api/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("text: text is required"));
    }

    @Test
    void sendMessageReturnsBadRequestWhenTextIsTooLong() throws Exception {
        String longText = "a".repeat(4001);

        mockMvc.perform(post("/api/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "%s"
                                }
                                """.formatted(longText)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("text: text must be at most 4000 characters"));
    }

    @Test
    void sendMessageReturnsServerErrorWhenBedrockFails() throws Exception {
        Mockito.when(chatSessionService.sendMessage(eq("session-1"), any()))
                .thenThrow(new BedrockInvocationException("Bedrock failed"));

        mockMvc.perform(post("/api/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("text", "hello"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Bedrock failed"))
                .andExpect(jsonPath("$.status").value(500));
    }
}
