package net.jrodolfo.aws.bedrock.chat.journal.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

public class InferenceConfig {

    @DecimalMin(value = "0.0", message = "temperature must be at least 0.0")
    @DecimalMax(value = "1.0", message = "temperature must be at most 1.0")
    private Double temperature;

    @DecimalMin(value = "0.0", message = "topP must be at least 0.0")
    @DecimalMax(value = "1.0", message = "topP must be at most 1.0")
    private Double topP;

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
