package net.jrodolfo.aws.bedrock.chat.journal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BadRequestException;
import net.jrodolfo.aws.bedrock.chat.journal.model.BedrockReply;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BedrockInvocationException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatMessage;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.ContentBlock;
import net.jrodolfo.aws.bedrock.chat.journal.model.InferenceConfig;
import net.jrodolfo.aws.bedrock.chat.journal.model.ResponseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

@Service
public class BedrockChatService {

    private static final Logger log = LoggerFactory.getLogger(BedrockChatService.class);
    private static final Logger payloadLog = LoggerFactory.getLogger("net.jrodolfo.aws.bedrock.chat.journal.bedrock.payload");

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;
    private final ObjectMapper objectMapper;
    private final AppProperties.BedrockPayloadMode bedrockPayloadMode;
    private final boolean redactBedrockContent;

    @Autowired
    public BedrockChatService(BedrockRuntimeClient bedrockRuntimeClient,
                              BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient,
                              ObjectMapper objectMapper,
                              AppProperties appProperties) {
        this(bedrockRuntimeClient,
                bedrockRuntimeAsyncClient,
                objectMapper,
                appProperties.getLogging().getBedrockPayloadMode(),
                appProperties.getLogging().isRedactBedrockContent());
    }

    BedrockChatService(BedrockRuntimeClient bedrockRuntimeClient,
                       BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient,
                       ObjectMapper objectMapper,
                       AppProperties.BedrockPayloadMode bedrockPayloadMode,
                       boolean redactBedrockContent) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.bedrockRuntimeAsyncClient = bedrockRuntimeAsyncClient;
        this.objectMapper = objectMapper;
        this.bedrockPayloadMode = bedrockPayloadMode;
        this.redactBedrockContent = redactBedrockContent;
    }

    BedrockChatService(BedrockRuntimeClient bedrockRuntimeClient,
                       BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient) {
        this(bedrockRuntimeClient, bedrockRuntimeAsyncClient, new ObjectMapper(), AppProperties.BedrockPayloadMode.OFF, true);
    }

    public BedrockReply sendConversation(ChatSession session) {
        try {
            int messageCount = session.getMessages() != null ? session.getMessages().size() : 0;
            log.debug("Sending conversation to Bedrock for sessionId={}, modelId={}, messageCount={}",
                    session.getSessionId(),
                    session.getModelId(),
                    messageCount);
            Instant requestedAt = Instant.now();
            long startedAt = System.currentTimeMillis();

            ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                    .modelId(session.getModelId())
                    .messages(toBedrockMessages(session.getMessages()));

            if (session.getInferenceConfig() != null) {
                requestBuilder.inferenceConfig(toInferenceConfiguration(session.getInferenceConfig()));
            }

            if (StringUtils.hasText(session.getSystemPrompt())) {
                requestBuilder.system(SystemContentBlock.builder()
                        .text(session.getSystemPrompt())
                        .build());
            }

            ConverseRequest request = requestBuilder.build();
            logPayload("converse request", request);

            ConverseResponse response = bedrockRuntimeClient.converse(request);
            logPayload("converse response", response);
            Instant respondedAt = Instant.now();
            long durationMs = System.currentTimeMillis() - startedAt;
            String assistantReply = extractAssistantText(response);
            ResponseMetadata metadata = toResponseMetadata(session, response, requestedAt, respondedAt, durationMs);
            log.debug("Received Bedrock reply for sessionId={}, modelId={}, replyLength={}",
                    session.getSessionId(),
                    session.getModelId(),
                    assistantReply.length());
            return new BedrockReply(assistantReply, metadata);
        } catch (SdkException ex) {
            log.warn("Bedrock invocation failed for sessionId={}, modelId={}: {}",
                    session.getSessionId(),
                    session.getModelId(),
                    ex.getMessage());
            throw new BedrockInvocationException("Failed to call Amazon Bedrock: " + ex.getMessage(), ex);
        }
    }

    public CompletableFuture<BedrockReply> streamConversation(ChatSession session, Consumer<String> chunkConsumer) {
        int messageCount = session.getMessages() != null ? session.getMessages().size() : 0;
        log.debug("Streaming conversation to Bedrock for sessionId={}, modelId={}, messageCount={}",
                session.getSessionId(),
                session.getModelId(),
                messageCount);

        Instant requestedAt = Instant.now();
        long startedAt = System.currentTimeMillis();
        StringBuilder reply = new StringBuilder();
        AtomicReference<String> stopReason = new AtomicReference<>();
        AtomicReference<TokenUsage> usage = new AtomicReference<>();
        AtomicReference<ConverseStreamMetrics> metrics = new AtomicReference<>();
        CompletableFuture<BedrockReply> result = new CompletableFuture<>();

        ConverseStreamRequest.Builder requestBuilder = ConverseStreamRequest.builder()
                .modelId(session.getModelId())
                .messages(toBedrockMessages(session.getMessages()));

        if (session.getInferenceConfig() != null) {
            requestBuilder.inferenceConfig(toInferenceConfiguration(session.getInferenceConfig()));
        }

        if (StringUtils.hasText(session.getSystemPrompt())) {
            requestBuilder.system(SystemContentBlock.builder()
                    .text(session.getSystemPrompt())
                    .build());
        }

        ConverseStreamRequest request = requestBuilder.build();
        logPayload("converse stream request", request);

        ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                        .onContentBlockDelta(event -> handleContentBlockDelta(event, reply, chunkConsumer))
                        .onMessageStop(event -> handleMessageStop(event, stopReason))
                        .onMetadata(event -> handleMetadata(event, usage, metrics))
                        .build())
                .onError(error -> {
                    log.warn("Bedrock streaming invocation failed for sessionId={}, modelId={}: {}",
                            session.getSessionId(),
                            session.getModelId(),
                            error.getMessage());
                    result.completeExceptionally(new BedrockInvocationException("Failed to stream from Amazon Bedrock: " + error.getMessage(), error));
                })
                .onComplete(() -> {
                    if (!StringUtils.hasText(reply.toString())) {
                        result.completeExceptionally(new BedrockInvocationException("Amazon Bedrock streaming did not return text content"));
                        return;
                    }

                    Instant respondedAt = Instant.now();
                    long durationMs = System.currentTimeMillis() - startedAt;
                    ResponseMetadata metadata = toStreamResponseMetadata(session, requestedAt, respondedAt, durationMs, stopReason.get(), usage.get(), metrics.get());
                    logPayload("converse stream completion", Map.of(
                            "sessionId", session.getSessionId(),
                            "modelId", session.getModelId(),
                            "replyText", reply.toString(),
                            "metadata", metadata
                    ));
                    result.complete(new BedrockReply(reply.toString(), metadata));
                })
                .build();

        try {
            bedrockRuntimeAsyncClient.converseStream(request, handler);
        } catch (SdkException ex) {
            return CompletableFuture.failedFuture(new BedrockInvocationException("Failed to stream from Amazon Bedrock: " + ex.getMessage(), ex));
        }

        return result;
    }

    private InferenceConfiguration toInferenceConfiguration(InferenceConfig inferenceConfig) {
        InferenceConfiguration.Builder builder = InferenceConfiguration.builder();

        if (inferenceConfig.getTemperature() != null) {
            builder.temperature(inferenceConfig.getTemperature().floatValue());
        }

        if (inferenceConfig.getTopP() != null) {
            builder.topP(inferenceConfig.getTopP().floatValue());
        }

        if (inferenceConfig.getMaxTokens() != null) {
            builder.maxTokens(inferenceConfig.getMaxTokens());
        }

        return builder.build();
    }

    private void handleContentBlockDelta(ContentBlockDeltaEvent event, StringBuilder reply, Consumer<String> chunkConsumer) {
        if (event.delta() == null || !StringUtils.hasText(event.delta().text())) {
            return;
        }

        String chunk = event.delta().text();
        reply.append(chunk);
        chunkConsumer.accept(chunk);
    }

    private void handleMessageStop(MessageStopEvent event, AtomicReference<String> stopReason) {
        if (event.stopReason() != null) {
            stopReason.set(event.stopReasonAsString());
        }
    }

    private void handleMetadata(ConverseStreamMetadataEvent event,
                                AtomicReference<TokenUsage> usage,
                                AtomicReference<ConverseStreamMetrics> metrics) {
        if (event.usage() != null) {
            usage.set(event.usage());
        }
        if (event.metrics() != null) {
            metrics.set(event.metrics());
        }
    }

    private ResponseMetadata toResponseMetadata(ChatSession session,
                                                ConverseResponse response,
                                                Instant requestedAt,
                                                Instant respondedAt,
                                                long durationMs) {
        ResponseMetadata metadata = new ResponseMetadata();
        metadata.setRequestedAt(requestedAt);
        metadata.setRespondedAt(respondedAt);
        metadata.setDurationMs(durationMs);
        metadata.setModelId(session.getModelId());
        metadata.setInferenceConfig(session.getInferenceConfig());

        if (response.stopReason() != null) {
            metadata.setStopReason(response.stopReasonAsString());
        }

        if (response.usage() != null) {
            metadata.setInputTokens(response.usage().inputTokens());
            metadata.setOutputTokens(response.usage().outputTokens());
            metadata.setTotalTokens(response.usage().totalTokens());
        }

        if (response.metrics() != null) {
            metadata.setBedrockLatencyMs(response.metrics().latencyMs());
        }

        return metadata;
    }

    private ResponseMetadata toStreamResponseMetadata(ChatSession session,
                                                      Instant requestedAt,
                                                      Instant respondedAt,
                                                      long durationMs,
                                                      String stopReason,
                                                      TokenUsage usage,
                                                      ConverseStreamMetrics metrics) {
        ResponseMetadata metadata = new ResponseMetadata();
        metadata.setRequestedAt(requestedAt);
        metadata.setRespondedAt(respondedAt);
        metadata.setDurationMs(durationMs);
        metadata.setModelId(session.getModelId());
        metadata.setInferenceConfig(session.getInferenceConfig());
        metadata.setStopReason(stopReason);

        if (usage != null) {
            metadata.setInputTokens(usage.inputTokens());
            metadata.setOutputTokens(usage.outputTokens());
            metadata.setTotalTokens(usage.totalTokens());
        }

        if (metrics != null) {
            metadata.setBedrockLatencyMs(metrics.latencyMs());
        }

        return metadata;
    }

    private List<Message> toBedrockMessages(List<ChatMessage> messages) {
        List<Message> bedrockMessages = new ArrayList<>();

        for (ChatMessage message : messages) {
            bedrockMessages.add(Message.builder()
                    .role(toConversationRole(message.getRole()))
                    .content(toBedrockContent(message.getContent()))
                    .build());
        }

        return bedrockMessages;
    }

    private List<software.amazon.awssdk.services.bedrockruntime.model.ContentBlock> toBedrockContent(List<ContentBlock> contentBlocks) {
        List<software.amazon.awssdk.services.bedrockruntime.model.ContentBlock> content = new ArrayList<>();

        for (ContentBlock block : contentBlocks) {
            content.add(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.fromText(block.getText()));
        }

        return content;
    }

    private ConversationRole toConversationRole(String role) {
        if ("user".equalsIgnoreCase(role)) {
            return ConversationRole.USER;
        }

        if ("assistant".equalsIgnoreCase(role)) {
            return ConversationRole.ASSISTANT;
        }

        throw new BadRequestException("Unsupported message role: " + role);
    }

    private String extractAssistantText(ConverseResponse response) {
        if (response.output() == null
                || response.output().message() == null
                || response.output().message().content() == null) {
            throw new BedrockInvocationException("Amazon Bedrock returned an empty response");
        }

        StringBuilder reply = new StringBuilder();

        for (software.amazon.awssdk.services.bedrockruntime.model.ContentBlock block : response.output().message().content()) {
            if (StringUtils.hasText(block.text())) {
                if (!reply.isEmpty()) {
                    reply.append('\n');
                }
                reply.append(block.text());
            }
        }

        if (!StringUtils.hasText(reply.toString())) {
            throw new BedrockInvocationException("Amazon Bedrock did not return text content");
        }

        return reply.toString();
    }

    private void logPayload(String label, Object payload) {
        if (bedrockPayloadMode == AppProperties.BedrockPayloadMode.OFF || !payloadLog.isDebugEnabled()) {
            return;
        }

        try {
            Object payloadToLog = toLogPayload(payload);
            payloadLog.debug("{}:{}{}", label, System.lineSeparator(),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payloadToLog));
        } catch (Exception ex) {
            payloadLog.warn("Failed to serialize {} for payload logging: {}", label, ex.getMessage());
        }
    }

    Object toLogPayload(Object payload) {
        return switch (bedrockPayloadMode) {
            case OFF -> null;
            case SUMMARY -> summarizePayload(payload);
            case RAW -> rawPayload(payload);
        };
    }

    private Object summarizePayload(Object payload) {
        if (payload instanceof ConverseRequest request) {
            return summarizeConverseRequest(request);
        }
        if (payload instanceof ConverseResponse response) {
            return summarizeConverseResponse(response);
        }
        if (payload instanceof ConverseStreamRequest request) {
            return summarizeConverseStreamRequest(request);
        }
        if (payload instanceof Map<?, ?> map) {
            return summarizeMapPayload(map);
        }
        return payload;
    }

    private Map<String, Object> summarizeConverseRequest(ConverseRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", "converseRequest");
        summary.put("modelId", request.modelId());
        summary.put("messageCount", request.messages() != null ? request.messages().size() : 0);
        summary.put("messages", summarizeMessages(request.messages()));
        if (request.system() != null && !request.system().isEmpty()) {
            summary.put("system", summarizeSystemBlocks(request.system()));
        }
        if (request.inferenceConfig() != null) {
            summary.put("inferenceConfig", summarizeInferenceConfiguration(request.inferenceConfig()));
        }
        return summary;
    }

    private Map<String, Object> summarizeConverseResponse(ConverseResponse response) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", "converseResponse");
        if (response.stopReason() != null) {
            summary.put("stopReason", response.stopReasonAsString());
        }
        if (response.usage() != null) {
            summary.put("usage", summarizeUsage(response.usage()));
        }
        if (response.metrics() != null) {
            summary.put("metrics", Map.of("latencyMs", response.metrics().latencyMs()));
        }
        if (response.output() != null && response.output().message() != null) {
            summary.put("outputMessage", summarizeMessage(response.output().message()));
        }
        return summary;
    }

    private Map<String, Object> summarizeConverseStreamRequest(ConverseStreamRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", "converseStreamRequest");
        summary.put("modelId", request.modelId());
        summary.put("messageCount", request.messages() != null ? request.messages().size() : 0);
        summary.put("messages", summarizeMessages(request.messages()));
        if (request.system() != null && !request.system().isEmpty()) {
            summary.put("system", summarizeSystemBlocks(request.system()));
        }
        if (request.inferenceConfig() != null) {
            summary.put("inferenceConfig", summarizeInferenceConfiguration(request.inferenceConfig()));
        }
        return summary;
    }

    private Map<String, Object> summarizeMapPayload(Map<?, ?> map) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", "streamCompletion");
        summary.put("sessionId", map.get("sessionId"));
        summary.put("modelId", map.get("modelId"));
        if (map.containsKey("replyText")) {
            summary.put("reply", summarizeTextValue((String) map.get("replyText")));
        }
        if (map.containsKey("metadata")) {
            summary.put("metadata", map.get("metadata"));
        }
        return summary;
    }

    private List<Map<String, Object>> summarizeMessages(List<Message> messages) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        if (messages == null) {
            return summaries;
        }
        for (Message message : messages) {
            summaries.add(summarizeMessage(message));
        }
        return summaries;
    }

    private Map<String, Object> summarizeMessage(Message message) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("role", message.role() != null ? message.role().toString() : null);
        List<Map<String, Object>> contents = new ArrayList<>();
        if (message.content() != null) {
            for (software.amazon.awssdk.services.bedrockruntime.model.ContentBlock block : message.content()) {
                Map<String, Object> contentSummary = new LinkedHashMap<>();
                contentSummary.put("text", summarizeTextValue(block.text()));
                contents.add(contentSummary);
            }
        }
        summary.put("content", contents);
        return summary;
    }

    private List<Map<String, Object>> summarizeSystemBlocks(List<SystemContentBlock> blocks) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (SystemContentBlock block : blocks) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("text", summarizeTextValue(block.text()));
            summaries.add(summary);
        }
        return summaries;
    }

    private Map<String, Object> summarizeInferenceConfiguration(InferenceConfiguration inferenceConfig) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("temperature", inferenceConfig.temperature());
        summary.put("topP", inferenceConfig.topP());
        summary.put("maxTokens", inferenceConfig.maxTokens());
        return summary;
    }

    private Map<String, Object> summarizeUsage(TokenUsage usage) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("inputTokens", usage.inputTokens());
        summary.put("outputTokens", usage.outputTokens());
        summary.put("totalTokens", usage.totalTokens());
        return summary;
    }

    private Object summarizeTextValue(String text) {
        if (!StringUtils.hasText(text)) {
            return Map.of("length", text == null ? 0 : text.length(), "preview", "");
        }
        if (redactBedrockContent) {
            return Map.of("length", text.length(), "preview", "[redacted]");
        }
        return Map.of("length", text.length(), "preview", preview(text));
    }

    private Object redactPayload(Object payload) {
        JsonNode tree = objectMapper.valueToTree(payload);
        return redactJsonNode(tree);
    }

    private Object rawPayload(Object payload) {
        JsonNode tree = objectMapper.valueToTree(payload);
        return redactBedrockContent ? redactJsonNode(tree) : tree;
    }

    private JsonNode redactJsonNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode redacted = ((ObjectNode) node).deepCopy();
            redacted.fieldNames().forEachRemaining(fieldName -> {
                JsonNode child = redacted.get(fieldName);
                if (isSensitiveTextField(fieldName) && child != null && child.isTextual()) {
                    redacted.put(fieldName, "[redacted]");
                } else {
                    redacted.set(fieldName, redactJsonNode(child));
                }
            });
            return redacted;
        }
        if (node.isArray()) {
            ArrayNode redacted = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                redacted.add(redactJsonNode(child));
            }
            return redacted;
        }
        return node;
    }

    private boolean isSensitiveTextField(String fieldName) {
        return "text".equals(fieldName) || "replyText".equals(fieldName);
    }

    private String preview(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        int maxLength = 120;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
