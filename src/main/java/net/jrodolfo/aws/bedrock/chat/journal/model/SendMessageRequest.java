package net.jrodolfo.aws.bedrock.chat.journal.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for sending a user message to an existing session.")
public class SendMessageRequest {

    @Schema(description = "User text message.", example = "Explain the Amazon Bedrock Converse API in simple terms.")
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
