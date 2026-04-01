package net.jrodolfo.aws.bedrock.chat.journal.scripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class RequestScriptsSmokeTest {

    private static final Path REPO_ROOT = Path.of(System.getProperty("user.dir"));

    @TempDir
    Path tempDir;

    @Test
    void curlExamplesHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/curl-examples.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Usage:");
        assertThat(result.stdout()).contains("curl-examples.sh");
    }

    @Test
    void sendMessageHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/send-message.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("SESSION_ID=<session-id>");
    }

    @Test
    void sendMessageRequiresSessionId() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/send-message.sh"), Map.of());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("SESSION_ID is required.");
    }

    @Test
    void prettyPrintHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/pretty-print-sessions.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("--raw");
    }

    @Test
    void prettyPrintRenderedModeUsesCustomSessionsDir() throws Exception {
        Path sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Files.writeString(sessionsDir.resolve("session-1.json"), """
                {
                  "sessionId": "session-1",
                  "modelId": "amazon.nova-lite-v1:0",
                  "systemPrompt": "You are a helper.",
                  "messages": [
                    {
                      "role": "assistant",
                      "content": [
                        { "text": "### Heading\\n**Tip:** hello" }
                      ]
                    }
                  ]
                }
                """);

        ProcessResult result = runScript(
                Path.of("scripts/pretty-print-sessions.sh"),
                Map.of("SESSIONS_DIR", sessionsDir.toString())
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("sessionId: session-1");
        assertThat(result.stdout()).contains("Heading");
        assertThat(result.stdout()).contains("Tip: hello");
        assertThat(result.stdout()).doesNotContain("###");
        assertThat(result.stdout()).doesNotContain("**");
    }

    @Test
    void listSessionsHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/list-sessions.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Lists stored session files");
    }

    @Test
    void listSessionsReadsCustomSessionsDir() throws Exception {
        Path sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Files.writeString(sessionsDir.resolve("session-1.json"), """
                {
                  "sessionId": "session-1",
                  "modelId": "amazon.nova-lite-v1:0",
                  "systemPrompt": "You are a helpful AWS study assistant.",
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        { "text": "Hello" }
                      ]
                    }
                  ]
                }
                """);

        ProcessResult result = runScript(
                Path.of("scripts/list-sessions.sh"),
                Map.of("SESSIONS_DIR", sessionsDir.toString())
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("file: session-1.json");
        assertThat(result.stdout()).contains("sessionId: session-1");
        assertThat(result.stdout()).contains("messageCount: 1");
    }

    @Test
    void resetSessionHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/reset-session.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Resets the message history");
    }

    @Test
    void resetSessionRequiresSessionId() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/reset-session.sh"), Map.of());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("SESSION_ID is required.");
    }

    private ProcessResult runScript(Path scriptPath, Map<String, String> env, String... args) throws Exception {
        Path absoluteScript = REPO_ROOT.resolve(scriptPath);
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("bash");
        command.add(absoluteScript.toString());
        command.addAll(java.util.List.of(args));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(REPO_ROOT.toFile());

        Map<String, String> environment = processBuilder.environment();
        environment.putAll(new LinkedHashMap<>(env));

        Process process = processBuilder.start();
        String stdout = readStream(process.getInputStream());
        String stderr = readStream(process.getErrorStream());
        int exitCode = process.waitFor();

        return new ProcessResult(exitCode, stdout, stderr);
    }

    private String readStream(java.io.InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes());
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
