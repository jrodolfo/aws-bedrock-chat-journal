package net.jrodolfo.aws.bedrock.chat.journal.exception;

public class BedrockInvocationException extends RuntimeException {

    public BedrockInvocationException(String message, Throwable cause) {
        super(message, cause);
    }

    public BedrockInvocationException(String message) {
        super(message);
    }
}
