package net.jrodolfo.aws.bedrock.chat.journal.service;

import java.util.ArrayList;
import java.util.UUID;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BadRequestException;
import net.jrodolfo.aws.bedrock.chat.journal.exception.ResourceNotFoundException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatMessage;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.BedrockReply;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageResponse;
import net.jrodolfo.aws.bedrock.chat.journal.model.InferenceConfig;
import net.jrodolfo.aws.bedrock.chat.journal.model.UpdateSessionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    private final SessionStore sessionStore;
    private final BedrockChatService bedrockChatService;
    private final AppProperties appProperties;

    public ChatSessionService(SessionStore sessionStore,
                              BedrockChatService bedrockChatService,
                              AppProperties appProperties) {
        this.sessionStore = sessionStore;
        this.bedrockChatService = bedrockChatService;
        this.appProperties = appProperties;
    }

    public ChatSession createSession(CreateSessionRequest request) {
        CreateSessionRequest payload = request != null ? request : new CreateSessionRequest();

        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setModelId(resolveModelId(payload));
        session.setSystemPrompt(normalizeText(payload.getSystemPrompt()));
        session.setInferenceConfig(resolveCreateInferenceConfig(payload.getInferenceConfig()));
        session.setMessages(new ArrayList<>());

        log.debug("Creating session sessionId={}, modelId={}, hasSystemPrompt={}, inferenceConfigPresent={}",
                session.getSessionId(),
                session.getModelId(),
                session.getSystemPrompt() != null,
                session.getInferenceConfig() != null);
        return sessionStore.save(session);
    }

    public ChatSession getSession(String sessionId) {
        return sessionStore.load(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
    }

    public ChatSession updateSession(String sessionId, UpdateSessionRequest request) {
        ChatSession session = getSession(sessionId);
        UpdateSessionRequest payload = request != null ? request : new UpdateSessionRequest();

        if (payload.getModelId() != null) {
            session.setModelId(resolveOptionalModelId(payload.getModelId(), session.getModelId()));
        }

        if (payload.getSystemPrompt() != null) {
            session.setSystemPrompt(normalizeText(payload.getSystemPrompt()));
        }

        if (payload.getInferenceConfig() != null) {
            session.setInferenceConfig(mergeInferenceConfig(session.getInferenceConfig(), payload.getInferenceConfig()));
        }

        log.debug("Updated session metadata sessionId={}, modelId={}, hasSystemPrompt={}, inferenceConfigPresent={}",
                session.getSessionId(),
                session.getModelId(),
                session.getSystemPrompt() != null,
                session.getInferenceConfig() != null);
        return sessionStore.save(session);
    }

    public ChatSession resetSession(String sessionId) {
        ChatSession session = getSession(sessionId);
        session.setMessages(new ArrayList<>());
        log.debug("Reset session messages sessionId={}, modelId={}", session.getSessionId(), session.getModelId());
        return sessionStore.save(session);
    }

    public void deleteSession(String sessionId) {
        getSession(sessionId);
        sessionStore.delete(sessionId);
        log.debug("Deleted session sessionId={}", sessionId);
    }

    public SendMessageResponse sendMessage(String sessionId, SendMessageRequest request) {
        ChatSession session = getSession(sessionId);
        ensureSessionCanAcceptMoreMessages(session);

        String messageText = normalizeRequiredText(request != null ? request.getText() : null, "text is required");
        ChatMessage userMessage = ChatMessage.userText(messageText);
        session.getMessages().add(userMessage);

        BedrockReply bedrockReply = bedrockChatService.sendConversation(session);
        ChatMessage assistantMessage = ChatMessage.assistantText(bedrockReply.getText(), bedrockReply.getMetadata());
        session.getMessages().add(assistantMessage);

        sessionStore.save(session);
        log.debug("Updated session sessionId={}, modelId={}, messageCount={}",
                session.getSessionId(),
                session.getModelId(),
                session.getMessages().size());

        return new SendMessageResponse(session.getSessionId(), session.getModelId(), bedrockReply.getText(), assistantMessage, bedrockReply.getMetadata());
    }

    private void ensureSessionCanAcceptMoreMessages(ChatSession session) {
        int existingMessages = session.getMessages() != null ? session.getMessages().size() : 0;
        int maxMessages = appProperties.getLimits().getMaxMessagesPerSession();

        if (existingMessages + 2 > maxMessages) {
            log.debug("Rejecting session update for sessionId={} because messageCount={} would exceed maxMessages={}",
                    session.getSessionId(),
                    existingMessages,
                    maxMessages);
            throw new BadRequestException("Session has reached the maximum number of stored messages: " + maxMessages);
        }
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new BadRequestException(message);
        }

        return normalized;
    }

    private String resolveModelId(CreateSessionRequest request) {
        if (StringUtils.hasText(request.getModelId())) {
            return request.getModelId().trim();
        }

        return appProperties.getAws().getDefaultModelId();
    }

    private String resolveOptionalModelId(String requestedModelId, String currentModelId) {
        if (StringUtils.hasText(requestedModelId)) {
            return requestedModelId.trim();
        }

        return currentModelId;
    }

    private InferenceConfig resolveCreateInferenceConfig(InferenceConfig requested) {
        AppProperties.Inference defaults = appProperties.getInference();
        InferenceConfig base = new InferenceConfig(defaults.getTemperature(), defaults.getTopP(), defaults.getMaxTokens());
        return mergeInferenceConfig(base, requested);
    }

    private InferenceConfig mergeInferenceConfig(InferenceConfig base, InferenceConfig requested) {
        if (requested == null) {
            return base;
        }

        InferenceConfig merged = new InferenceConfig();
        merged.setTemperature(requested.getTemperature() != null ? requested.getTemperature() : base != null ? base.getTemperature() : null);
        merged.setTopP(requested.getTopP() != null ? requested.getTopP() : base != null ? base.getTopP() : null);
        merged.setMaxTokens(requested.getMaxTokens() != null ? requested.getMaxTokens() : base != null ? base.getMaxTokens() : null);
        return merged;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }
}
