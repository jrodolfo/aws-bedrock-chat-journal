package net.jrodolfo.aws.bedrock.chat.journal.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response returned after a message is sent and the assistant reply is stored.")
public class SendMessageResponse {

    @Schema(description = "Session identifier.")
    private String sessionId;
    @Schema(description = "Bedrock model used for the reply.")
    private String modelId;
    @Schema(description = "Assistant reply text.", example = "The Converse API lets you send structured chat history to a Bedrock model.")
    private String reply;
    @Schema(description = "Assistant message stored in session format.")
    private ChatMessage assistantMessage;
    @Schema(description = "Metadata captured for the generated assistant response.")
    private ResponseMetadata metadata;

    public SendMessageResponse() {
    }

    public SendMessageResponse(String sessionId, String modelId, String reply, ChatMessage assistantMessage, ResponseMetadata metadata) {
        this.sessionId = sessionId;
        this.modelId = modelId;
        this.reply = reply;
        this.assistantMessage = assistantMessage;
        this.metadata = metadata;
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

    public ResponseMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ResponseMetadata metadata) {
        this.metadata = metadata;
    }
}
