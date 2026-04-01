package net.jrodolfo.aws.bedrock.chat.journal.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

@Schema(description = "Inference parameters passed to Amazon Bedrock Converse.")
public class InferenceConfig {

    @Schema(description = "Sampling temperature between 0.0 and 1.0.", example = "0.7")
    @DecimalMin(value = "0.0", message = "temperature must be at least 0.0")
    @DecimalMax(value = "1.0", message = "temperature must be at most 1.0")
    private Double temperature;

    @Schema(description = "Top-p sampling value between 0.0 and 1.0.", example = "0.9")
    @DecimalMin(value = "0.0", message = "topP must be at least 0.0")
    @DecimalMax(value = "1.0", message = "topP must be at most 1.0")
    private Double topP;

    @Schema(description = "Maximum number of generated tokens.", example = "512")
    @Min(value = 1, message = "maxTokens must be at least 1")
    private Integer maxTokens;

    public InferenceConfig() {
    }

    public InferenceConfig(Double temperature, Double topP, Integer maxTokens) {
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}
