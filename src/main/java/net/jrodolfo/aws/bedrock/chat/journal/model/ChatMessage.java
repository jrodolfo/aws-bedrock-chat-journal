package net.jrodolfo.aws.bedrock.chat.journal.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Chat message stored in Bedrock-friendly format.")
public class ChatMessage {

    @Schema(description = "Message role.", example = "assistant")
    private String role;
    @Schema(description = "Text content blocks.")
    private List<ContentBlock> content = new ArrayList<>();
    @Schema(description = "Optional metadata captured for assistant messages.")
    private ResponseMetadata metadata;

    public ChatMessage() {
    }

    public ChatMessage(String role, List<ContentBlock> content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage userText(String text) {
        return new ChatMessage("user", List.of(new ContentBlock(text)));
    }

    public static ChatMessage assistantText(String text) {
        return new ChatMessage("assistant", List.of(new ContentBlock(text)));
    }

    public static ChatMessage assistantText(String text, ResponseMetadata metadata) {
        ChatMessage message = new ChatMessage("assistant", List.of(new ContentBlock(text)));
        message.setMetadata(metadata);
        return message;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    public void setContent(List<ContentBlock> content) {
        this.content = content;
    }

    public ResponseMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ResponseMetadata metadata) {
        this.metadata = metadata;
    }
}
