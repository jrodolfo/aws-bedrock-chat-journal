package net.jrodolfo.aws.bedrock.chat.journal.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Summary returned when a new session is created.")
public class CreateSessionResponse {

    @Schema(description = "Unique session identifier.", example = "3d3fd3a4-6b6c-4a73-9ec5-63d8f2ec1234")
    private String sessionId;
    @Schema(description = "Configured Bedrock model identifier.", example = "amazon.nova-lite-v1:0")
    private String modelId;
    @Schema(description = "Configured system prompt.")
    private String systemPrompt;
    @Schema(description = "Effective inference settings stored with the session.")
    private InferenceConfig inferenceConfig;
    @Schema(description = "Current number of stored messages.", example = "0")
    private int messageCount;

    public CreateSessionResponse() {
    }

    public CreateSessionResponse(String sessionId, String modelId, String systemPrompt, InferenceConfig inferenceConfig, int messageCount) {
        this.sessionId = sessionId;
        this.modelId = modelId;
        this.systemPrompt = systemPrompt;
        this.inferenceConfig = inferenceConfig;
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

    public InferenceConfig getInferenceConfig() {
        return inferenceConfig;
    }

    public void setInferenceConfig(InferenceConfig inferenceConfig) {
        this.inferenceConfig = inferenceConfig;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
}
