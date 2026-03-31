package net.jrodolfo.aws.bedrock.chat.journal.service;

import java.util.ArrayList;
import java.util.List;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BadRequestException;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BedrockInvocationException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatMessage;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.ContentBlock;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

@Service
public class BedrockChatService {

    private final BedrockRuntimeClient bedrockRuntimeClient;

    public BedrockChatService(BedrockRuntimeClient bedrockRuntimeClient) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
    }

    public String sendConversation(ChatSession session) {
        try {
            ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                    .modelId(session.getModelId())
                    .messages(toBedrockMessages(session.getMessages()));

            if (StringUtils.hasText(session.getSystemPrompt())) {
                requestBuilder.system(SystemContentBlock.builder()
                        .text(session.getSystemPrompt())
                        .build());
            }

            ConverseResponse response = bedrockRuntimeClient.converse(requestBuilder.build());
            return extractAssistantText(response);
        } catch (SdkClientException ex) {
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
        if (response.output() == null || response.output().message() == null) {
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
