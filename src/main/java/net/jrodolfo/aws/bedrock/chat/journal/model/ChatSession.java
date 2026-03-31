package net.jrodolfo.aws.bedrock.chat.journal.model;

import java.util.ArrayList;
import java.util.List;

public class ChatSession {

    private String sessionId;
    private String modelId;
    private String systemPrompt;
    private List<ChatMessage> messages = new ArrayList<>();

    public ChatSession() {
    }

    public ChatSession(String sessionId, String modelId, String systemPrompt, List<ChatMessage> messages) {
        this.sessionId = sessionId;
        this.modelId = modelId;
        this.systemPrompt = systemPrompt;
        this.messages = messages;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
}
