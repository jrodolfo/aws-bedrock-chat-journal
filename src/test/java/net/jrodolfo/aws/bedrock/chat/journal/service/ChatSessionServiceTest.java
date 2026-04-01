package net.jrodolfo.aws.bedrock.chat.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BadRequestException;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BedrockInvocationException;
import net.jrodolfo.aws.bedrock.chat.journal.exception.ResourceNotFoundException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class ChatSessionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createSessionUsesDefaultModelWhenRequestDoesNotProvideOne() {
        ChatSessionService service = createService("Assistant reply");

        ChatSession session = service.createSession(new CreateSessionRequest());

        assertThat(session.getSessionId()).isNotBlank();
        assertThat(session.getModelId()).isEqualTo("amazon.nova-lite-v1:0");
        assertThat(session.getMessages()).isEmpty();
    }

    @Test
    void createSessionUsesTrimmedCustomModelAndNormalizesBlankSystemPrompt() {
        ChatSessionService service = createService("Assistant reply");
        CreateSessionRequest request = new CreateSessionRequest();
        request.setModelId(" custom-model ");
        request.setSystemPrompt("   ");

        ChatSession session = service.createSession(request);

        assertThat(session.getModelId()).isEqualTo("custom-model");
        assertThat(session.getSystemPrompt()).isNull();
    }

    @Test
    void createSessionHandlesNullRequest() {
        ChatSessionService service = createService("Assistant reply");

        ChatSession session = service.createSession(null);

        assertThat(session.getSessionId()).isNotBlank();
        assertThat(session.getModelId()).isEqualTo("amazon.nova-lite-v1:0");
    }

    @Test
    void sendMessageAppendsUserAndAssistantMessages() {
        ChatSessionService service = createService("Bedrock answer");
        ChatSession session = service.createSession(new CreateSessionRequest());

        SendMessageRequest request = new SendMessageRequest();
        request.setText("What is the Converse API?");

        SendMessageResponse response = service.sendMessage(session.getSessionId(), request);
        ChatSession storedSession = service.getSession(session.getSessionId());

        assertThat(response.getReply()).isEqualTo("Bedrock answer");
        assertThat(storedSession.getMessages()).hasSize(2);
        assertThat(storedSession.getMessages().get(0).getRole()).isEqualTo("user");
        assertThat(storedSession.getMessages().get(1).getRole()).isEqualTo("assistant");
        assertThat(storedSession.getMessages().get(1).getContent().get(0).getText()).isEqualTo("Bedrock answer");
    }

    @Test
    void sendMessageTrimsUserInputBeforeSaving() {
        ChatSessionService service = createService("Trimmed reply");
        ChatSession session = service.createSession(new CreateSessionRequest());
        SendMessageRequest request = new SendMessageRequest();
        request.setText("   Explain Bedrock   ");

        service.sendMessage(session.getSessionId(), request);

        ChatSession storedSession = service.getSession(session.getSessionId());
        assertThat(storedSession.getMessages().get(0).getContent().get(0).getText()).isEqualTo("Explain Bedrock");
    }

    @Test
    void sendMessageThrowsWhenSessionDoesNotExist() {
        ChatSessionService service = createService("Assistant reply");
        SendMessageRequest request = new SendMessageRequest();
        request.setText("Hello");

        assertThatThrownBy(() -> service.sendMessage("missing-session", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Session not found: missing-session");
    }

    @Test
    void sendMessageRejectsBlankText() {
        ChatSessionService service = createService("Assistant reply");
        ChatSession session = service.createSession(new CreateSessionRequest());
        SendMessageRequest request = new SendMessageRequest();
        request.setText("   ");

        assertThatThrownBy(() -> service.sendMessage(session.getSessionId(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("text is required");
    }

    @Test
    void sendMessagePropagatesBedrockFailureWithoutSavingAssistantReply() {
        ChatSessionService service = createService(new BedrockInvocationException("Bedrock failed"));
        ChatSession session = service.createSession(new CreateSessionRequest());
        SendMessageRequest request = new SendMessageRequest();
        request.setText("Hello");

        assertThatThrownBy(() -> service.sendMessage(session.getSessionId(), request))
                .isInstanceOf(BedrockInvocationException.class)
                .hasMessage("Bedrock failed");

        ChatSession storedSession = service.getSession(session.getSessionId());
        assertThat(storedSession.getMessages()).isEmpty();
    }

    @Test
    void sendMessageRejectsWhenSessionWouldExceedMessageLimit() {
        ChatSessionService service = createService("Assistant reply", 2);
        ChatSession session = service.createSession(new CreateSessionRequest());

        SendMessageRequest firstRequest = new SendMessageRequest();
        firstRequest.setText("First");
        service.sendMessage(session.getSessionId(), firstRequest);

        SendMessageRequest secondRequest = new SendMessageRequest();
        secondRequest.setText("Second");

        assertThatThrownBy(() -> service.sendMessage(session.getSessionId(), secondRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Session has reached the maximum number of stored messages: 2");
    }

    private ChatSessionService createService(String bedrockReply) {
        BedrockChatService bedrockChatService = Mockito.mock(BedrockChatService.class);
        Mockito.when(bedrockChatService.sendConversation(any())).thenReturn(bedrockReply);
        return createService(bedrockChatService, 100);
    }

    private ChatSessionService createService(RuntimeException bedrockException) {
        BedrockChatService bedrockChatService = Mockito.mock(BedrockChatService.class);
        Mockito.when(bedrockChatService.sendConversation(any())).thenThrow(bedrockException);
        return createService(bedrockChatService, 100);
    }

    private ChatSessionService createService(String bedrockReply, int maxMessagesPerSession) {
        BedrockChatService bedrockChatService = Mockito.mock(BedrockChatService.class);
        Mockito.when(bedrockChatService.sendConversation(any())).thenReturn(bedrockReply);
        return createService(bedrockChatService, maxMessagesPerSession);
    }

    private ChatSessionService createService(BedrockChatService bedrockChatService, int maxMessagesPerSession) {
        AppProperties appProperties = new AppProperties();
        appProperties.getAws().setDefaultModelId("amazon.nova-lite-v1:0");
        appProperties.getAws().setRegion("us-east-1");
        appProperties.getStorage().setSessionsDirectory(tempDir.resolve("sessions").toString());
        appProperties.getLimits().setMaxMessagesPerSession(maxMessagesPerSession);

        FileSessionStore store = new FileSessionStore(new ObjectMapper().findAndRegisterModules(), appProperties);
        store.initializeStorage();

        return new ChatSessionService(store, bedrockChatService, appProperties);
    }
}
