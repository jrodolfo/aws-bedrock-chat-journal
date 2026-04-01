package net.jrodolfo.aws.bedrock.chat.journal.service;

import java.util.List;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BadRequestException;
import net.jrodolfo.aws.bedrock.chat.journal.model.BedrockReply;
import net.jrodolfo.aws.bedrock.chat.journal.exception.BedrockInvocationException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatMessage;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.ContentBlock;
import net.jrodolfo.aws.bedrock.chat.journal.model.InferenceConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class BedrockChatServiceTest {

    @Test
    void sendConversationBuildsRequestAndExtractsReply() {
        BedrockRuntimeClient client = Mockito.mock(BedrockRuntimeClient.class);
        BedrockRuntimeAsyncClient asyncClient = Mockito.mock(BedrockRuntimeAsyncClient.class);
        Mockito.when(client.converse(any(ConverseRequest.class))).thenReturn(converseResponse("First reply", "Second reply"));
        BedrockChatService service = new BedrockChatService(client, asyncClient);

        ChatSession session = new ChatSession(
                "session-1",
                "amazon.nova-lite-v1:0",
                "You are a study assistant.",
                new InferenceConfig(0.4, 0.8, 256),
                List.of(
                        ChatMessage.userText("Hello"),
                        ChatMessage.assistantText("Hi there")
                )
        );

        BedrockReply reply = service.sendConversation(session);

        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        Mockito.verify(client).converse(requestCaptor.capture());
        ConverseRequest request = requestCaptor.getValue();

        assertThat(reply.getText()).isEqualTo("First reply\nSecond reply");
        assertThat(reply.getMetadata()).isNotNull();
        assertThat(reply.getMetadata().getStopReason()).isEqualTo("end_turn");
        assertThat(reply.getMetadata().getInputTokens()).isEqualTo(10);
        assertThat(reply.getMetadata().getOutputTokens()).isEqualTo(20);
        assertThat(reply.getMetadata().getTotalTokens()).isEqualTo(30);
        assertThat(reply.getMetadata().getBedrockLatencyMs()).isEqualTo(111L);
        assertThat(reply.getMetadata().getInferenceConfig().getTemperature()).isEqualTo(0.4);
        assertThat(request.modelId()).isEqualTo("amazon.nova-lite-v1:0");
        assertThat(request.system()).hasSize(1);
        assertThat(request.system().get(0).text()).isEqualTo("You are a study assistant.");
        assertThat(request.inferenceConfig()).isNotNull();
        assertThat(request.inferenceConfig().temperature()).isEqualTo(0.4f);
        assertThat(request.inferenceConfig().topP()).isEqualTo(0.8f);
        assertThat(request.inferenceConfig().maxTokens()).isEqualTo(256);
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo(ConversationRole.USER);
        assertThat(request.messages().get(0).content()).hasSize(1);
        assertThat(request.messages().get(0).content().get(0).text()).isEqualTo("Hello");
        assertThat(request.messages().get(1).role()).isEqualTo(ConversationRole.ASSISTANT);
    }

    @Test
    void sendConversationOmitsBlankSystemPrompt() {
        BedrockRuntimeClient client = Mockito.mock(BedrockRuntimeClient.class);
        BedrockRuntimeAsyncClient asyncClient = Mockito.mock(BedrockRuntimeAsyncClient.class);
        Mockito.when(client.converse(any(ConverseRequest.class))).thenReturn(converseResponse("Reply"));
        BedrockChatService service = new BedrockChatService(client, asyncClient);

        ChatSession session = new ChatSession("session-1", "model-id", "   ", null, List.of(ChatMessage.userText("Hello")));
        service.sendConversation(session);

        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        Mockito.verify(client).converse(requestCaptor.capture());
        assertThat(requestCaptor.getValue().system()).isNullOrEmpty();
    }

    @Test
    void sendConversationRejectsUnsupportedRole() {
        BedrockRuntimeClient client = Mockito.mock(BedrockRuntimeClient.class);
        BedrockRuntimeAsyncClient asyncClient = Mockito.mock(BedrockRuntimeAsyncClient.class);
        BedrockChatService service = new BedrockChatService(client, asyncClient);
        ChatSession session = new ChatSession(
                "session-1",
                "model-id",
                null,
                null,
                List.of(new ChatMessage("system", List.of(new ContentBlock("Not supported"))))
        );

        assertThatThrownBy(() -> service.sendConversation(session))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unsupported message role: system");
    }

    @Test
    void sendConversationThrowsWhenResponseHasNoOutputMessage() {
        BedrockRuntimeClient client = Mockito.mock(BedrockRuntimeClient.class);
        BedrockRuntimeAsyncClient asyncClient = Mockito.mock(BedrockRuntimeAsyncClient.class);
        Mockito.when(client.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder().build());
        BedrockChatService service = new BedrockChatService(client, asyncClient);

        ChatSession session = new ChatSession("session-1", "model-id", null, null, List.of(ChatMessage.userText("Hello")));

        assertThatThrownBy(() -> service.sendConversation(session))
                .isInstanceOf(BedrockInvocationException.class)
                .hasMessage("Amazon Bedrock returned an empty response");
    }

    @Test
    void sendConversationThrowsWhenResponseDoesNotContainText() {
        BedrockRuntimeClient client = Mockito.mock(BedrockRuntimeClient.class);
        BedrockRuntimeAsyncClient asyncClient = Mockito.mock(BedrockRuntimeAsyncClient.class);
        ConverseResponse response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(List.of(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.builder().build()))
                                .build())
                        .build())
                .build();
        Mockito.when(client.converse(any(ConverseRequest.class))).thenReturn(response);
        BedrockChatService service = new BedrockChatService(client, asyncClient);

        ChatSession session = new ChatSession("session-1", "model-id", null, null, List.of(ChatMessage.userText("Hello")));

        assertThatThrownBy(() -> service.sendConversation(session))
                .isInstanceOf(BedrockInvocationException.class)
                .hasMessage("Amazon Bedrock did not return text content");
    }

    @Test
    void sendConversationWrapsSdkExceptions() {
        BedrockRuntimeClient client = Mockito.mock(BedrockRuntimeClient.class);
        BedrockRuntimeAsyncClient asyncClient = Mockito.mock(BedrockRuntimeAsyncClient.class);
        Mockito.when(client.converse(any(ConverseRequest.class))).thenThrow(SdkClientException.create("Boom"));
        BedrockChatService service = new BedrockChatService(client, asyncClient);

        ChatSession session = new ChatSession("session-1", "model-id", null, null, List.of(ChatMessage.userText("Hello")));

        assertThatThrownBy(() -> service.sendConversation(session))
                .isInstanceOf(BedrockInvocationException.class)
                .hasMessageContaining("Failed to call Amazon Bedrock: Boom");
    }

    private ConverseResponse converseResponse(String... parts) {
        List<software.amazon.awssdk.services.bedrockruntime.model.ContentBlock> contentBlocks = java.util.Arrays.stream(parts)
                .map(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock::fromText)
                .toList();

        return ConverseResponse.builder()
                .stopReason(StopReason.END_TURN)
                .usage(TokenUsage.builder()
                        .inputTokens(10)
                        .outputTokens(20)
                        .totalTokens(30)
                        .build())
                .metrics(ConverseMetrics.builder()
                        .latencyMs(111L)
                        .build())
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(contentBlocks)
                                .build())
                        .build())
                .build();
    }
}
