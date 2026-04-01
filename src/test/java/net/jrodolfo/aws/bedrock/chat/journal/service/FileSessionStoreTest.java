package net.jrodolfo.aws.bedrock.chat.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.exception.SessionStorageException;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatMessage;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSessionStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeStorageCreatesSessionsDirectory() {
        Path sessionsDir = tempDir.resolve("nested").resolve("sessions");
        FileSessionStore store = createStore(sessionsDir);

        store.initializeStorage();

        assertThat(Files.isDirectory(sessionsDir)).isTrue();
    }

    @Test
    void saveAndLoadSession() {
        Path sessionsDir = tempDir.resolve("sessions");
        FileSessionStore store = createStore(sessionsDir);
        store.initializeStorage();

        ChatSession session = new ChatSession("session-1", "amazon.nova-lite-v1:0",
                "You are a study assistant.",
                null,
                List.of(ChatMessage.userText("Hello")));

        store.save(session);
        ChatSession loadedSession = store.load("session-1").orElseThrow();

        assertThat(Files.exists(sessionsDir.resolve("session-1.json"))).isTrue();
        assertThat(loadedSession.getMessages()).hasSize(1);
        assertThat(loadedSession.getMessages().get(0).getRole()).isEqualTo("user");
    }

    @Test
    void loadReturnsEmptyWhenSessionFileDoesNotExist() {
        FileSessionStore store = createStore(tempDir.resolve("sessions"));
        store.initializeStorage();

        assertThat(store.load("missing-session")).isEmpty();
    }

    @Test
    void loadThrowsWhenJsonIsMalformed() throws Exception {
        Path sessionsDir = tempDir.resolve("sessions");
        FileSessionStore store = createStore(sessionsDir);
        store.initializeStorage();
        Files.writeString(sessionsDir.resolve("broken-session.json"), "{ not-valid-json");

        assertThatThrownBy(() -> store.load("broken-session"))
                .isInstanceOf(SessionStorageException.class)
                .hasMessage("Failed to read session: broken-session");
    }

    private FileSessionStore createStore(Path sessionsDir) {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setSessionsDirectory(sessionsDir.toString());
        return new FileSessionStore(new ObjectMapper().findAndRegisterModules(), appProperties);
    }
}
