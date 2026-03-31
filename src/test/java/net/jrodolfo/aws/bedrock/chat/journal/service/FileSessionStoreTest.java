package net.jrodolfo.aws.bedrock.chat.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.jrodolfo.aws.bedrock.chat.journal.config.AppProperties;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatMessage;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FileSessionStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadSession() throws Exception {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setSessionsDirectory(tempDir.resolve("sessions").toString());

        FileSessionStore store = new FileSessionStore(new ObjectMapper().findAndRegisterModules(), appProperties);
        store.initializeStorage();

        ChatSession session = new ChatSession("session-1", "amazon.nova-lite-v1:0",
                "You are a study assistant.",
                List.of(ChatMessage.userText("Hello")));

        store.save(session);

        assertThat(Files.exists(tempDir.resolve("sessions").resolve("session-1.json"))).isTrue();
        assertThat(store.load("session-1")).isPresent();
        assertThat(store.load("session-1").orElseThrow().getMessages()).hasSize(1);
        assertThat(store.load("session-1").orElseThrow().getMessages().get(0).getRole()).isEqualTo("user");
    }
}
