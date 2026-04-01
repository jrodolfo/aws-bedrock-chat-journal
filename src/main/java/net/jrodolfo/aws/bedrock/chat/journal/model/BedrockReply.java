package net.jrodolfo.aws.bedrock.chat.journal.model;

public class BedrockReply {

    private final String text;
    private final ResponseMetadata metadata;

    public BedrockReply(String text, ResponseMetadata metadata) {
        this.text = text;
        this.metadata = metadata;
    }

    public String getText() {
        return text;
    }

    public ResponseMetadata getMetadata() {
        return metadata;
    }
}
