package net.jrodolfo.aws.bedrock.chat.journal.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Metadata captured for an assistant response.")
public class ResponseMetadata {

    @Schema(description = "Time when the Bedrock request started.")
    private Instant requestedAt;

    @Schema(description = "Time when the Bedrock response was received.")
    private Instant respondedAt;

    @Schema(description = "Locally measured request duration in milliseconds.", example = "412")
    private Long durationMs;

    @Schema(description = "Stop reason returned by Bedrock.", example = "end_turn")
    private String stopReason;

    @Schema(description = "Input token count returned by Bedrock when available.", example = "132")
    private Integer inputTokens;

    @Schema(description = "Output token count returned by Bedrock when available.", example = "221")
    private Integer outputTokens;

    @Schema(description = "Total token count returned by Bedrock when available.", example = "353")
    private Integer totalTokens;

    @Schema(description = "Latency returned by Bedrock metrics when available, in milliseconds.", example = "398")
    private Long bedrockLatencyMs;

    @Schema(description = "Model used for the assistant reply.", example = "amazon.nova-lite-v1:0")
    private String modelId;

    @Schema(description = "Effective inference configuration used for the request.")
    private InferenceConfig inferenceConfig;

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Long getBedrockLatencyMs() {
        return bedrockLatencyMs;
    }

    public void setBedrockLatencyMs(Long bedrockLatencyMs) {
        this.bedrockLatencyMs = bedrockLatencyMs;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public InferenceConfig getInferenceConfig() {
        return inferenceConfig;
    }

    public void setInferenceConfig(InferenceConfig inferenceConfig) {
        this.inferenceConfig = inferenceConfig;
    }
}
