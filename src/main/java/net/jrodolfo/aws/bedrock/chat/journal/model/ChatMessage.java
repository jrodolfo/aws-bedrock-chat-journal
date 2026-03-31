package net.jrodolfo.aws.bedrock.chat.journal.model;

import java.util.ArrayList;
import java.util.List;

public class ChatMessage {

    private String role;
    private List<ContentBlock> content = new ArrayList<>();

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
}
