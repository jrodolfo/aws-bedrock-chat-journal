package net.jrodolfo.aws.bedrock.chat.journal.model;

public class CreateSessionResponse {

    private String sessionId;
    private String modelId;
    private String systemPrompt;
    private int messageCount;

    public CreateSessionResponse() {
    }

    public CreateSessionResponse(String sessionId, String modelId, String systemPrompt, int messageCount) {
        this.sessionId = sessionId;
        this.modelId = modelId;
        this.systemPrompt = systemPrompt;
        this.messageCount = messageCount;
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

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
}
