package net.jrodolfo.aws.bedrock.chat.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BadRequestException;
import net.jrodolfo.aws.bedrock.chat.journal.model.BedrockReply;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BedrockInvocationException;
import net.jrodolfo.aws.bedrock.chat.journal.exception.ResourceNotFoundException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.InferenceConfig;
import net.jrodolfo.aws.bedrock.chat.journal.model.ResponseMetadata;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageResponse;
import net.jrodolfo.aws.bedrock.chat.journal.model.UpdateSessionRequest;
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
        assertThat(session.getInferenceConfig()).isNotNull();
        assertThat(session.getInferenceConfig().getTemperature()).isEqualTo(0.7);
        assertThat(session.getInferenceConfig().getTopP()).isEqualTo(0.9);
        assertThat(session.getInferenceConfig().getMaxTokens()).isEqualTo(512);
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
    void createSessionMergesProvidedInferenceConfigWithDefaults() {
        ChatSessionService service = createService("Assistant reply");
        CreateSessionRequest request = new CreateSessionRequest();
        request.setInferenceConfig(new InferenceConfig(0.2, null, 1024));

        ChatSession session = service.createSession(request);

        assertThat(session.getInferenceConfig()).isNotNull();
        assertThat(session.getInferenceConfig().getTemperature()).isEqualTo(0.2);
        assertThat(session.getInferenceConfig().getTopP()).isEqualTo(0.9);
        assertThat(session.getInferenceConfig().getMaxTokens()).isEqualTo(1024);
    }

    @Test
    void createSessionHandlesNullRequest() {
        ChatSessionService service = createService("Assistant reply");

        ChatSession session = service.createSession(null);

        assertThat(session.getSessionId()).isNotBlank();
        assertThat(session.getModelId()).isEqualTo("amazon.nova-lite-v1:0");
    }

    @Test
    void updateSessionUpdatesModelAndSystemPrompt() {
        ChatSessionService service = createService("Assistant reply");
        ChatSession session = service.createSession(new CreateSessionRequest());
        UpdateSessionRequest request = new UpdateSessionRequest();
        request.setModelId(" updated-model ");
        request.setSystemPrompt("Updated prompt");

        ChatSession updated = service.updateSession(session.getSessionId(), request);

        assertThat(updated.getModelId()).isEqualTo("updated-model");
        assertThat(updated.getSystemPrompt()).isEqualTo("Updated prompt");
    }

    @Test
    void updateSessionMergesInferenceConfigWithExistingValues() {
        ChatSessionService service = createService("Assistant reply");
        CreateSessionRequest createRequest = new CreateSessionRequest();
        createRequest.setInferenceConfig(new InferenceConfig(0.1, 0.2, 300));
        ChatSession session = service.createSession(createRequest);

        UpdateSessionRequest updateRequest = new UpdateSessionRequest();
        updateRequest.setInferenceConfig(new InferenceConfig(null, 0.95, null));

        ChatSession updated = service.updateSession(session.getSessionId(), updateRequest);

        assertThat(updated.getInferenceConfig()).isNotNull();
        assertThat(updated.getInferenceConfig().getTemperature()).isEqualTo(0.1);
        assertThat(updated.getInferenceConfig().getTopP()).isEqualTo(0.95);
        assertThat(updated.getInferenceConfig().getMaxTokens()).isEqualTo(300);
    }

    @Test
    void updateSessionLeavesModelUnchangedWhenBlankModelIdProvided() {
        ChatSessionService service = createService("Assistant reply");
        CreateSessionRequest createRequest = new CreateSessionRequest();
        createRequest.setModelId("custom-model");
        ChatSession session = service.createSession(createRequest);
        UpdateSessionRequest updateRequest = new UpdateSessionRequest();
        updateRequest.setModelId("   ");
        updateRequest.setSystemPrompt("   ");

        ChatSession updated = service.updateSession(session.getSessionId(), updateRequest);

        assertThat(updated.getModelId()).isEqualTo("custom-model");
        assertThat(updated.getSystemPrompt()).isNull();
    }

    @Test
    void resetSessionClearsMessagesAndKeepsMetadata() {
        ChatSessionService service = createService("Assistant reply");
        ChatSession session = service.createSession(new CreateSessionRequest());
        SendMessageRequest sendRequest = new SendMessageRequest();
        sendRequest.setText("Hello");
        service.sendMessage(session.getSessionId(), sendRequest);

        ChatSession reset = service.resetSession(session.getSessionId());

        assertThat(reset.getSessionId()).isEqualTo(session.getSessionId());
        assertThat(reset.getModelId()).isEqualTo(session.getModelId());
        assertThat(reset.getMessages()).isEmpty();
    }

    @Test
    void deleteSessionRemovesStoredSession() {
        ChatSessionService service = createService("Assistant reply");
        ChatSession session = service.createSession(new CreateSessionRequest());

        service.deleteSession(session.getSessionId());

        assertThatThrownBy(() -> service.getSession(session.getSessionId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Session not found: " + session.getSessionId());
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
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().getStopReason()).isEqualTo("end_turn");
        assertThat(storedSession.getMessages()).hasSize(2);
        assertThat(storedSession.getMessages().get(0).getRole()).isEqualTo("user");
        assertThat(storedSession.getMessages().get(1).getRole()).isEqualTo("assistant");
        assertThat(storedSession.getMessages().get(1).getContent().get(0).getText()).isEqualTo("Bedrock answer");
        assertThat(storedSession.getMessages().get(1).getMetadata()).isNotNull();
        assertThat(storedSession.getMessages().get(1).getMetadata().getStopReason()).isEqualTo("end_turn");
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
        Mockito.when(bedrockChatService.sendConversation(any())).thenReturn(new BedrockReply(bedrockReply, sampleMetadata()));
        return createService(bedrockChatService, 100);
    }

    private ChatSessionService createService(RuntimeException bedrockException) {
        BedrockChatService bedrockChatService = Mockito.mock(BedrockChatService.class);
        Mockito.when(bedrockChatService.sendConversation(any())).thenThrow(bedrockException);
        return createService(bedrockChatService, 100);
    }

    private ChatSessionService createService(String bedrockReply, int maxMessagesPerSession) {
        BedrockChatService bedrockChatService = Mockito.mock(BedrockChatService.class);
        Mockito.when(bedrockChatService.sendConversation(any())).thenReturn(new BedrockReply(bedrockReply, sampleMetadata()));
        return createService(bedrockChatService, maxMessagesPerSession);
    }

    private ChatSessionService createService(BedrockChatService bedrockChatService, int maxMessagesPerSession) {
        AppProperties appProperties = new AppProperties();
        appProperties.getAws().setDefaultModelId("amazon.nova-lite-v1:0");
        appProperties.getAws().setRegion("us-east-1");
        appProperties.getStorage().setSessionsDirectory(tempDir.resolve("sessions").toString());
        appProperties.getLimits().setMaxMessagesPerSession(maxMessagesPerSession);

        SessionStore store = new FileSessionStore(new ObjectMapper().findAndRegisterModules(), appProperties);
        ((FileSessionStore) store).initializeStorage();

        return new ChatSessionService(store, bedrockChatService, appProperties);
    }

    private ResponseMetadata sampleMetadata() {
        ResponseMetadata metadata = new ResponseMetadata();
        metadata.setStopReason("end_turn");
        metadata.setModelId("amazon.nova-lite-v1:0");
        metadata.setDurationMs(123L);
        return metadata;
    }
}
