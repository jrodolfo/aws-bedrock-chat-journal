package net.jrodolfo.aws.bedrock.chat.journal.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SendMessageRequest {

    @NotBlank(message = "text is required")
    @Size(max = 4000, message = "text must be at most 4000 characters")
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
