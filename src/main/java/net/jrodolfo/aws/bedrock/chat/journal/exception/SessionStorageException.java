package net.jrodolfo.aws.bedrock.chat.journal.exception;

public class SessionStorageException extends RuntimeException {

    public SessionStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
