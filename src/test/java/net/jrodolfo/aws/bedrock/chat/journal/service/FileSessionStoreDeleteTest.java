package net.jrodolfo.aws.bedrock.chat.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FileSessionStoreDeleteTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteRemovesExistingSessionFile() {
        AppProperties appProperties = new AppProperties();
        Path sessionsDir = tempDir.resolve("sessions");
        appProperties.getStorage().setSessionsDirectory(sessionsDir.toString());
        FileSessionStore store = new FileSessionStore(new ObjectMapper().findAndRegisterModules(), appProperties);
        store.initializeStorage();

        ChatSession session = new ChatSession();
        session.setSessionId("session-1");
        session.setModelId("model-id");
        store.save(session);

        store.delete("session-1");

        assertThat(Files.exists(sessionsDir.resolve("session-1.json"))).isFalse();
    }
}
