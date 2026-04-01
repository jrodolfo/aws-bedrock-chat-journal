package net.jrodolfo.aws.bedrock.chat.journal.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Server-sent event payload used by the streaming message endpoint.")
public class StreamEvent {

    @Schema(description = "Event type.", example = "chunk")
    private String type;

    @Schema(description = "Text chunk for chunk events.")
    private String text;

    @Schema(description = "Error message for error events.")
    private String message;

    @Schema(description = "Final assistant response for complete events.")
    private SendMessageResponse response;

    public StreamEvent() {
    }

    public StreamEvent(String type, String text, String message, SendMessageResponse response) {
        this.type = type;
        this.text = text;
        this.message = message;
        this.response = response;
    }

    public static StreamEvent start() {
        return new StreamEvent("start", null, null, null);
    }

    public static StreamEvent chunk(String text) {
        return new StreamEvent("chunk", text, null, null);
    }

    public static StreamEvent complete(SendMessageResponse response) {
        return new StreamEvent("complete", null, null, response);
    }

    public static StreamEvent error(String message) {
        return new StreamEvent("error", null, message, null);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public SendMessageResponse getResponse() {
        return response;
    }

    public void setResponse(SendMessageResponse response) {
        this.response = response;
    }
}
