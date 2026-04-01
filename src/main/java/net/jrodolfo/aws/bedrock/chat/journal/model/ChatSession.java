package net.jrodolfo.aws.bedrock.chat.journal.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Stored Bedrock chat session persisted as a local JSON file.")
public class ChatSession {

    @Schema(description = "Unique session identifier.", example = "3d3fd3a4-6b6c-4a73-9ec5-63d8f2ec1234")
    private String sessionId;
    @Schema(description = "Amazon Bedrock model identifier.", example = "amazon.nova-lite-v1:0")
    private String modelId;
    @Schema(description = "System prompt applied to the session.", example = "You are a helpful AWS study assistant.")
    private String systemPrompt;
    @Schema(description = "Inference settings stored with the session.")
    private InferenceConfig inferenceConfig;
    @Schema(description = "Conversation history in Bedrock-friendly message format.")
    private List<ChatMessage> messages = new ArrayList<>();

    public ChatSession() {
    }

    public ChatSession(String sessionId, String modelId, String systemPrompt, InferenceConfig inferenceConfig, List<ChatMessage> messages) {
        this.sessionId = sessionId;
        this.modelId = modelId;
        this.systemPrompt = systemPrompt;
        this.inferenceConfig = inferenceConfig;
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

    public InferenceConfig getInferenceConfig() {
        return inferenceConfig;
    }

    public void setInferenceConfig(InferenceConfig inferenceConfig) {
        this.inferenceConfig = inferenceConfig;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
}
