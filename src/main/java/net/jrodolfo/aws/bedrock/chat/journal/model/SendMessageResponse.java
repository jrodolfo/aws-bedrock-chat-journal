package net.jrodolfo.aws.bedrock.chat.journal.model;

public class SendMessageResponse {

    private String sessionId;
    private String modelId;
    private String reply;
    private ChatMessage assistantMessage;

    public SendMessageResponse() {
    }

    public SendMessageResponse(String sessionId, String modelId, String reply, ChatMessage assistantMessage) {
        this.sessionId = sessionId;
        this.modelId = modelId;
        this.reply = reply;
        this.assistantMessage = assistantMessage;
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

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public ChatMessage getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(ChatMessage assistantMessage) {
        this.assistantMessage = assistantMessage;
    }
}
