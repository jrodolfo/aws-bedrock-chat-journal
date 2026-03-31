package net.jrodolfo.aws.bedrock.chat.journal.exception;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
