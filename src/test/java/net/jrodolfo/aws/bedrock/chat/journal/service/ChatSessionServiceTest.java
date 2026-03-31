package net.jrodolfo.aws.bedrock.chat.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
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

    private ChatSessionService createService(String bedrockReply) {
        AppProperties appProperties = new AppProperties();
        appProperties.getAws().setDefaultModelId("amazon.nova-lite-v1:0");
        appProperties.getAws().setRegion("us-east-1");
        appProperties.getStorage().setSessionsDirectory(tempDir.resolve("sessions").toString());

        FileSessionStore store = new FileSessionStore(new ObjectMapper().findAndRegisterModules(), appProperties);
        store.initializeStorage();

        BedrockChatService bedrockChatService = Mockito.mock(BedrockChatService.class);
        Mockito.when(bedrockChatService.sendConversation(any())).thenReturn(bedrockReply);

        return new ChatSessionService(store, bedrockChatService, appProperties);
    }
}
