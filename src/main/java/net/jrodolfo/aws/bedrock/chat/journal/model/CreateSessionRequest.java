package net.jrodolfo.aws.bedrock.chat.journal.model;

import jakarta.validation.constraints.Size;

public class CreateSessionRequest {

    @Size(max = 200, message = "modelId must be at most 200 characters")
    private String modelId;

    @Size(max = 4000, message = "systemPrompt must be at most 4000 characters")
    private String systemPrompt;

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
}
