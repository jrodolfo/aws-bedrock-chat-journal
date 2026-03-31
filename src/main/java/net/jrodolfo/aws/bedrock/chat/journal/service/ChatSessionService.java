package net.jrodolfo.aws.bedrock.chat.journal.service;

import java.util.ArrayList;
import java.util.UUID;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.exception.ResourceNotFoundException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatMessage;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatSessionService {

    private final FileSessionStore fileSessionStore;
    private final BedrockChatService bedrockChatService;
    private final AppProperties appProperties;

    public ChatSessionService(FileSessionStore fileSessionStore,
                              BedrockChatService bedrockChatService,
                              AppProperties appProperties) {
        this.fileSessionStore = fileSessionStore;
        this.bedrockChatService = bedrockChatService;
        this.appProperties = appProperties;
    }

    public ChatSession createSession(CreateSessionRequest request) {
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setModelId(resolveModelId(request));
        session.setSystemPrompt(normalizeText(request.getSystemPrompt()));
        session.setMessages(new ArrayList<>());

        return fileSessionStore.save(session);
    }

    public ChatSession getSession(String sessionId) {
        return fileSessionStore.load(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
    }

    public SendMessageResponse sendMessage(String sessionId, SendMessageRequest request) {
        ChatSession session = getSession(sessionId);

        ChatMessage userMessage = ChatMessage.userText(request.getText().trim());
        session.getMessages().add(userMessage);

        String assistantReply = bedrockChatService.sendConversation(session);
        ChatMessage assistantMessage = ChatMessage.assistantText(assistantReply);
        session.getMessages().add(assistantMessage);

        fileSessionStore.save(session);

        return new SendMessageResponse(session.getSessionId(), session.getModelId(), assistantReply, assistantMessage);
    }

    private String resolveModelId(CreateSessionRequest request) {
        if (StringUtils.hasText(request.getModelId())) {
            return request.getModelId().trim();
        }

        return appProperties.getAws().getDefaultModelId();
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }
}
