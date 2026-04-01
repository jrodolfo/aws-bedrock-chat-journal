package net.jrodolfo.aws.bedrock.chat.journal.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for updating session metadata.")
public class UpdateSessionRequest {

    @Schema(description = "Optional Bedrock model ID update.", example = "amazon.nova-pro-v1:0")
    @Size(max = 200, message = "modelId must be at most 200 characters")
    private String modelId;

    @Schema(description = "Optional system prompt update.")
    @Size(max = 4000, message = "systemPrompt must be at most 4000 characters")
    private String systemPrompt;

    @Schema(description = "Optional inference settings update. Provided fields override existing values.")
    @Valid
    private InferenceConfig inferenceConfig;

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
}
