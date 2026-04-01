package net.jrodolfo.aws.bedrock.chat.journal.service;

import java.util.ArrayList;
import java.util.List;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BadRequestException;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BedrockInvocationException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatMessage;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.ContentBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

@Service
public class BedrockChatService {

    private static final Logger log = LoggerFactory.getLogger(BedrockChatService.class);

    private final BedrockRuntimeClient bedrockRuntimeClient;

    public BedrockChatService(BedrockRuntimeClient bedrockRuntimeClient) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
    }

    public String sendConversation(ChatSession session) {
        try {
            int messageCount = session.getMessages() != null ? session.getMessages().size() : 0;
            log.debug("Sending conversation to Bedrock for sessionId={}, modelId={}, messageCount={}",
                    session.getSessionId(),
                    session.getModelId(),
                    messageCount);

            ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                    .modelId(session.getModelId())
                    .messages(toBedrockMessages(session.getMessages()));

            if (StringUtils.hasText(session.getSystemPrompt())) {
                requestBuilder.system(SystemContentBlock.builder()
                        .text(session.getSystemPrompt())
                        .build());
            }

            ConverseResponse response = bedrockRuntimeClient.converse(requestBuilder.build());
            String assistantReply = extractAssistantText(response);
            log.debug("Received Bedrock reply for sessionId={}, modelId={}, replyLength={}",
                    session.getSessionId(),
                    session.getModelId(),
                    assistantReply.length());
            return assistantReply;
        } catch (SdkException ex) {
            log.warn("Bedrock invocation failed for sessionId={}, modelId={}: {}",
                    session.getSessionId(),
                    session.getModelId(),
                    ex.getMessage());
            throw new BedrockInvocationException("Failed to call Amazon Bedrock: " + ex.getMessage(), ex);
        }
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
}
