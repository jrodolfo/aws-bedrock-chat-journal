package net.jrodolfo.aws.bedrock.chat.journal.model;

public class ContentBlock {

    private String text;

    public ContentBlock() {
    }

    public ContentBlock(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
