package net.jrodolfo.aws.bedrock.chat.journal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BedrockInvocationException;
import net.jrodolfo.aws.bedrock.chat.journal.exception.GlobalExceptionHandler;
import net.jrodolfo.aws.bedrock.chat.journal.exception.ResourceNotFoundException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatMessage;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageResponse;
import net.jrodolfo.aws.bedrock.chat.journal.service.ChatSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void createSessionReturnsCreatedSession() throws Exception {
        ChatSession session = new ChatSession("session-1", "amazon.nova-lite-v1:0", "You are a tutor.", new ArrayList<>());
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
                .andExpect(jsonPath("$.modelId").value("amazon.nova-lite-v1:0"));
    }

    @Test
    void getSessionReturnsStoredSession() throws Exception {
        ChatSession session = new ChatSession("session-1", "amazon.nova-lite-v1:0", null,
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
    void sendMessageReturnsAssistantReply() throws Exception {
        SendMessageResponse response = new SendMessageResponse(
                "session-1",
                "amazon.nova-lite-v1:0",
                "Bedrock answer",
                ChatMessage.assistantText("Bedrock answer")
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
                .andExpect(jsonPath("$.assistantMessage.role").value("assistant"));
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
