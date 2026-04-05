package net.jrodolfo.aws.bedrock.chat.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.exception.SessionStorageException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FileSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(FileSessionStore.class);

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
            log.debug("Initialized session storage directory at {}", sessionsDirectory.toAbsolutePath());
        } catch (IOException ex) {
            throw new SessionStorageException("Failed to create session storage directory: " + sessionsDirectory, ex);
        }
    }

    @Override
    public ChatSession save(ChatSession session) {
        Path sessionFile = getSessionFile(session.getSessionId());

        try {
            Files.createDirectories(sessionsDirectory);
            try (OutputStream outputStream = Files.newOutputStream(sessionFile)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, session);
            }
            log.debug("Saved session {} to {}", session.getSessionId(), sessionFile.toAbsolutePath());
            return session;
        } catch (IOException ex) {
            throw new SessionStorageException("Failed to save session: " + session.getSessionId(), ex);
        }
    }

    @Override
    public Optional<ChatSession> load(String sessionId) {
        Path sessionFile = getSessionFile(sessionId);
        if (!Files.exists(sessionFile)) {
            log.debug("Session file not found for sessionId {} at {}", sessionId, sessionFile.toAbsolutePath());
            return Optional.empty();
        }

        try {
            ChatSession session;
            try (InputStream inputStream = Files.newInputStream(sessionFile)) {
                session = objectMapper.readValue(inputStream, ChatSession.class);
            }
            log.debug("Loaded session {} with {} messages from {}",
                    sessionId,
                    session.getMessages() != null ? session.getMessages().size() : 0,
                    sessionFile.toAbsolutePath());
            return Optional.of(session);
        } catch (IOException ex) {
            throw new SessionStorageException("Failed to read session: " + sessionId, ex);
        }
    }

    @Override
    public void delete(String sessionId) {
        Path sessionFile = getSessionFile(sessionId);
        if (!Files.exists(sessionFile)) {
            log.debug("Session file not found for delete sessionId {} at {}", sessionId, sessionFile.toAbsolutePath());
            return;
        }

        try {
            Files.delete(sessionFile);
            log.debug("Deleted session {} at {}", sessionId, sessionFile.toAbsolutePath());
        } catch (IOException ex) {
            throw new SessionStorageException("Failed to delete session: " + sessionId, ex);
        }
    }

    Path getSessionFile(String sessionId) {
        return sessionsDirectory.resolve(sessionId + ".json");
    }
}
