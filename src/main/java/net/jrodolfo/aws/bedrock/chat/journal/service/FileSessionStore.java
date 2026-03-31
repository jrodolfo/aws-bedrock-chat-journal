package net.jrodolfo.aws.bedrock.chat.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.exception.SessionStorageException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import org.springframework.stereotype.Service;

@Service
public class FileSessionStore {

    private final ObjectMapper objectMapper;
    private final Path sessionsDirectory;

    public FileSessionStore(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.sessionsDirectory = Path.of(appProperties.getStorage().getSessionsDirectory());
    }

    @PostConstruct
    public void initializeStorage() {
        try {
            Files.createDirectories(sessionsDirectory);
        } catch (IOException ex) {
            throw new SessionStorageException("Failed to create session storage directory: " + sessionsDirectory, ex);
        }
    }

    public ChatSession save(ChatSession session) {
        Path sessionFile = getSessionFile(session.getSessionId());

        try {
            Files.createDirectories(sessionsDirectory);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(sessionFile.toFile(), session);
            return session;
        } catch (IOException ex) {
            throw new SessionStorageException("Failed to save session: " + session.getSessionId(), ex);
        }
    }

    public Optional<ChatSession> load(String sessionId) {
        Path sessionFile = getSessionFile(sessionId);
        if (!Files.exists(sessionFile)) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(sessionFile.toFile(), ChatSession.class));
        } catch (IOException ex) {
            throw new SessionStorageException("Failed to read session: " + sessionId, ex);
        }
    }

    Path getSessionFile(String sessionId) {
        return sessionsDirectory.resolve(sessionId + ".json");
    }
}
