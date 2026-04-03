package net.jrodolfo.aws.bedrock.chat.journal.scripts;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
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
    void checkBackendHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/check-backend.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Calls GET /api/health");
        assertThat(result.stdout()).contains("BASE_URL");
    }

    @Test
    void checkBackendFailsWhenUnavailable() throws Exception {
        ProcessResult result = runScript(
                Path.of("scripts/check-backend.sh"),
                Map.of("BASE_URL", "http://localhost:1")
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Request failed.");
        assertThat(result.stderr()).contains("Try ./scripts/run-local.sh");
    }

    @Test
    void checkBackendSucceedsWhenHealthEndpointReturnsOk() throws Exception {
        HttpServer server = startHealthServer(200, """
                {"status":"OK"}
                """);

        try {
            ProcessResult result = runScript(
                    Path.of("scripts/check-backend.sh"),
                    Map.of("BASE_URL", "http://127.0.0.1:" + server.getAddress().getPort())
            );

            assertThat(result.exitCode()).isZero();
            assertThat(result.stdout()).contains("Backend is up.");
            assertThat(result.stdout()).contains("Health status: OK");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void checkBackendFailsWhenHealthPayloadIsUnexpected() throws Exception {
        HttpServer server = startHealthServer(200, """
                {"status":"DOWN"}
                """);

        try {
            ProcessResult result = runScript(
                    Path.of("scripts/check-backend.sh"),
                    Map.of("BASE_URL", "http://127.0.0.1:" + server.getAddress().getPort())
            );

            assertThat(result.exitCode()).isNotZero();
            assertThat(result.stderr()).contains("Unexpected health response");
            assertThat(result.stderr()).contains("status=OK");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void findPortPidHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/find-port-pid.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Prints the PID of the process listening");
        assertThat(result.stdout()).contains("Default: 8080");
    }

    @Test
    void findPortPidFailsForInvalidPort() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/find-port-pid.sh"), Map.of(), "not-a-port");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Port must be a number");
    }

    @Test
    void compareModelsHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/compare-models.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Creates two temporary sessions");
        assertThat(result.stdout()).contains("MODEL_A");
        assertThat(result.stdout()).contains("MODEL_B");
        assertThat(result.stdout()).contains("PROMPT_PRESET");
        assertThat(result.stdout()).contains("PROMPT_PRESET_A");
        assertThat(result.stdout()).contains("SYSTEM_PROMPT_A");
        assertThat(result.stdout()).contains("TEMPERATURE_A");
        assertThat(result.stdout()).contains("Inference means the generation settings");
        assertThat(result.stdout()).contains("Prompt preset precedence");
    }

    @Test
    void compareModelsRequiresModelA() throws Exception {
        ProcessResult result = runScript(
                Path.of("scripts/compare-models.sh"),
                Map.of("MODEL_B", "amazon.nova-pro-v1:0", "MESSAGE_TEXT", "Compare")
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("MODEL_A is required.");
    }

    @Test
    void compareModelsRequiresModelB() throws Exception {
        ProcessResult result = runScript(
                Path.of("scripts/compare-models.sh"),
                Map.of("MODEL_A", "amazon.nova-lite-v1:0", "MESSAGE_TEXT", "Compare")
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("MODEL_B is required.");
    }

    @Test
    void compareModelsRequiresMessageText() throws Exception {
        ProcessResult result = runScript(
                Path.of("scripts/compare-models.sh"),
                Map.of("MODEL_A", "amazon.nova-lite-v1:0", "MODEL_B", "amazon.nova-pro-v1:0")
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("MESSAGE_TEXT is required.");
    }

    @Test
    void listComparisonsHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/list-comparisons.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Lists saved comparison reports");
    }

    @Test
    void comparisonStatsHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/comparison-stats.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Reads saved comparison reports");
        assertThat(result.stdout()).contains("average duration per model");
    }

    @Test
    void comparisonStatsHandlesEmptyDirectory() throws Exception {
        Path comparisonsDir = tempDir.resolve("comparisons");
        Files.createDirectories(comparisonsDir);

        ProcessResult result = runScript(
                Path.of("scripts/comparison-stats.sh"),
                Map.of("COMPARISONS_DIR", comparisonsDir.toString())
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("No comparison JSON files found");
    }

    @Test
    void comparisonStatsSummarizesComparisonReports() throws Exception {
        Path comparisonsDir = tempDir.resolve("comparisons");
        Files.createDirectories(comparisonsDir);
        Files.writeString(comparisonsDir.resolve("comparison-1.json"), """
                {
                  "comparisonId": "comparison-1",
                  "createdAt": "2026-04-01T22:00:00Z",
                  "prompt": "Explain Converse API",
                  "modelA": {
                    "modelId": "amazon.nova-lite-v1:0",
                    "metadata": {
                      "durationMs": 1000,
                      "totalTokens": 50
                    }
                  },
                  "modelB": {
                    "modelId": "amazon.nova-pro-v1:0",
                    "metadata": {
                      "durationMs": 1200,
                      "totalTokens": 70
                    }
                  },
                  "summary": {
                    "fasterModel": "amazon.nova-lite-v1:0",
                    "lowerTokenModel": "amazon.nova-lite-v1:0"
                  }
                }
                """);
        Files.writeString(comparisonsDir.resolve("comparison-2.json"), """
                {
                  "comparisonId": "comparison-2",
                  "createdAt": "2026-04-01T23:00:00Z",
                  "prompt": "Explain prompt engineering",
                  "modelA": {
                    "modelId": "amazon.nova-lite-v1:0",
                    "metadata": {
                      "durationMs": 900,
                      "totalTokens": 60
                    }
                  },
                  "modelB": {
                    "modelId": "amazon.nova-pro-v1:0",
                    "metadata": {
                      "durationMs": 1100,
                      "totalTokens": 80
                    }
                  },
                  "summary": {
                    "fasterModel": "amazon.nova-lite-v1:0",
                    "lowerTokenModel": "amazon.nova-lite-v1:0"
                  }
                }
                """);

        ProcessResult result = runScript(
                Path.of("scripts/comparison-stats.sh"),
                Map.of("COMPARISONS_DIR", comparisonsDir.toString())
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("overall:");
        assertThat(result.stdout()).contains("comparisons: 2");
        assertThat(result.stdout()).contains("distinctModels: 2");
        assertThat(result.stdout()).contains("model usage:");
        assertThat(result.stdout()).contains("amazon.nova-lite-v1:0: 2");
        assertThat(result.stdout()).contains("amazon.nova-pro-v1:0: 2");
        assertThat(result.stdout()).contains("performance summary:");
        assertThat(result.stdout()).contains("amazon.nova-lite-v1:0: avgDurationMs=950.0, fasterCount=2");
        assertThat(result.stdout()).contains("amazon.nova-pro-v1:0: avgDurationMs=1150.0, fasterCount=0");
        assertThat(result.stdout()).contains("token summary:");
        assertThat(result.stdout()).contains("amazon.nova-lite-v1:0: avgTotalTokens=55.0, lowerTokenCount=2");
        assertThat(result.stdout()).contains("amazon.nova-pro-v1:0: avgTotalTokens=75.0, lowerTokenCount=0");
    }

    @Test
    void listComparisonsReadsCustomDirectory() throws Exception {
        Path comparisonsDir = tempDir.resolve("comparisons");
        Files.createDirectories(comparisonsDir);
        Files.writeString(comparisonsDir.resolve("comparison-1.json"), """
                {
                  "comparisonId": "comparison-1",
                  "createdAt": "2026-04-01T22:00:00Z",
                  "prompt": "Explain Converse API",
                  "modelA": {
                    "modelId": "amazon.nova-lite-v1:0"
                  },
                  "modelB": {
                    "modelId": "amazon.nova-pro-v1:0"
                  }
                }
                """);

        ProcessResult result = runScript(
                Path.of("scripts/list-comparisons.sh"),
                Map.of("COMPARISONS_DIR", comparisonsDir.toString())
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("file: comparison-1.json");
        assertThat(result.stdout()).contains("comparisonId: comparison-1");
        assertThat(result.stdout()).contains("modelA: amazon.nova-lite-v1:0");
        assertThat(result.stdout()).contains("modelB: amazon.nova-pro-v1:0");
    }

    @Test
    void prettyPrintComparisonsHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/pretty-print-comparisons.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("--raw");
    }

    @Test
    void prettyPrintComparisonsRenderedModeShowsComparisonData() throws Exception {
        Path comparisonsDir = tempDir.resolve("comparisons");
        Files.createDirectories(comparisonsDir);
        Files.writeString(comparisonsDir.resolve("comparison-1.json"), """
                {
                  "comparisonId": "comparison-1",
                  "createdAt": "2026-04-01T22:00:00Z",
                  "prompt": "### Explain Converse API",
                  "promptPresetA": "exam-tutor",
                  "promptPresetB": "bedrock-accuracy",
                  "systemPromptA": "You are concise.",
                  "systemPromptB": "You are detailed.",
                  "inferenceConfigA": {
                    "temperature": 0.2,
                    "topP": 0.8,
                    "maxTokens": 256
                  },
                  "inferenceConfigB": {
                    "temperature": 0.7,
                    "maxTokens": 512
                  },
                  "summary": {
                    "fasterModel": "amazon.nova-lite-v1:0",
                    "durationDifferenceMs": 200,
                    "lowerTokenModel": "amazon.nova-lite-v1:0",
                    "tokenDifference": 15,
                    "shorterReplyModel": "amazon.nova-lite-v1:0",
                    "longerReplyModel": "amazon.nova-pro-v1:0",
                    "replyLengthDifference": 12,
                    "generatedByModelId": "amazon.nova-pro-v1:0",
                    "keyDifferences": "Model B is more detailed, while Model A is more concise."
                  },
                  "modelA": {
                    "modelId": "amazon.nova-lite-v1:0",
                    "reply": "**Fast** answer",
                    "metadata": {
                      "durationMs": 1000,
                      "totalTokens": 25
                    }
                  },
                  "modelB": {
                    "modelId": "amazon.nova-pro-v1:0",
                    "reply": "Detailed answer",
                    "metadata": {
                      "durationMs": 1200,
                      "totalTokens": 40
                    }
                  }
                }
                """);

        ProcessResult result = runScript(
                Path.of("scripts/pretty-print-comparisons.sh"),
                Map.of("COMPARISONS_DIR", comparisonsDir.toString())
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("comparisonId: comparison-1");
        assertThat(result.stdout()).contains("prompt:");
        assertThat(result.stdout()).contains("Explain Converse API");
        assertThat(result.stdout()).contains("setup:");
        assertThat(result.stdout()).contains("promptPreset: exam-tutor");
        assertThat(result.stdout()).contains("promptPreset: bedrock-accuracy");
        assertThat(result.stdout()).contains("systemPrompt: You are concise.");
        assertThat(result.stdout()).contains("systemPrompt: You are detailed.");
        assertThat(result.stdout()).contains("inference: temperature=0.2, topP=0.8, maxTokens=256");
        assertThat(result.stdout()).contains("inference: temperature=0.7, maxTokens=512");
        assertThat(result.stdout()).contains("summary:");
        assertThat(result.stdout()).contains("fasterModel: amazon.nova-lite-v1:0 (200 ms faster)");
        assertThat(result.stdout()).contains("generatedByModelId: amazon.nova-pro-v1:0");
        assertThat(result.stdout()).contains("keyDifferences:");
        assertThat(result.stdout()).contains("Model B is more detailed, while Model A is more concise.");
        assertThat(result.stdout()).contains("Model A");
        assertThat(result.stdout()).contains("Fast answer");
        assertThat(result.stdout()).contains("durationMs: 1000");
        assertThat(result.stdout()).contains("Model B");
        assertThat(result.stdout()).contains("Detailed answer");
        assertThat(result.stdout()).doesNotContain("###");
        assertThat(result.stdout()).doesNotContain("**");
    }

    @Test
    void buildAndTestHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/build-and-test.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Gradle verification/build tasks");
        assertThat(result.stdout()).contains("GRADLE_TASKS");
    }

    @Test
    void chatHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/chat.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Opens an interactive terminal chat loop");
        assertThat(result.stdout()).contains("/stream on");
        assertThat(result.stdout()).contains("/metadata on");
        assertThat(result.stdout()).contains("/history");
        assertThat(result.stdout()).contains("/show-config");
        assertThat(result.stdout()).contains("/temperature <value>");
        assertThat(result.stdout()).contains("/preset <name>");
        assertThat(result.stdout()).contains("exam-tutor");
        assertThat(result.stdout()).contains("Available prompt presets:");
        assertThat(result.stdout()).contains("Conservative wording that avoids invented AWS details");
    }

    @Test
    void chatInteractiveCommandsWorkWithExistingSession() throws Exception {
        ProcessResult result = runScriptWithInput(
                Path.of("scripts/chat.sh"),
                Map.of("SESSION_ID", "session-1", "STREAM", "true"),
                "/preset\n/session\n/stream off\n/metadata off\n/help\n/exit\n"
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Available prompt presets:");
        assertThat(result.stdout()).contains("bedrock-accuracy");
        assertThat(result.stdout()).contains("Session ID: session-1");
        assertThat(result.stdout()).contains("Mode: streaming");
        assertThat(result.stdout()).contains("Streaming disabled.");
        assertThat(result.stdout()).contains("Metadata disabled.");
        assertThat(result.stdout()).contains("Commands:");
        assertThat(result.stdout()).contains("Goodbye.");
    }

    @Test
    void sendMessageHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/send-message.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("./scripts/send-message.sh <session-id>");
        assertThat(result.stdout()).contains("prompts for message text interactively");
        assertThat(result.stdout()).contains("SESSION_ID=<session-id>");
    }

    @Test
    void sendMessageRequiresSessionId() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/send-message.sh"), Map.of());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("SESSION_ID is required.");
    }

    @Test
    void sendMessageRequiresMessageTextWhenRunningNonInteractively() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/send-message.sh"), Map.of(), "session-123");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("MESSAGE_TEXT is required when running non-interactively.");
    }

    @Test
    void sendMessageAcceptsMessageTextAsSecondPositionalArgument() throws Exception {
        ProcessResult result = runScript(
                Path.of("scripts/send-message.sh"),
                Map.of("BASE_URL", "http://localhost:1"),
                "session-123",
                "Compare Converse and InvokeModel."
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Request failed.");
    }

    @Test
    void sendMessageAcceptsInteractiveMessageInputWhenOnlySessionIdIsProvided() throws Exception {
        ProcessResult result = runScriptWithInput(
                Path.of("scripts/send-message.sh"),
                Map.of("BASE_URL", "http://localhost:1"),
                "Continue the previous explanation.\n",
                "session-123"
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stdout()).contains("message>");
        assertThat(result.stderr()).contains("Request failed.");
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
    void prettyPrintRenderedModeShowsAssistantMetadata() throws Exception {
        Path sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Files.writeString(sessionsDir.resolve("session-1.json"), """
                {
                  "sessionId": "session-1",
                  "modelId": "amazon.nova-lite-v1:0",
                  "messages": [
                    {
                      "role": "assistant",
                      "content": [
                        { "text": "Hello" }
                      ],
                      "metadata": {
                        "requestedAt": "2026-04-01T19:10:00Z",
                        "respondedAt": "2026-04-01T19:10:02Z",
                        "durationMs": 2000,
                        "totalTokens": 42,
                        "stopReason": "end_turn"
                      }
                    }
                  ]
                }
                """);

        ProcessResult result = runScript(
                Path.of("scripts/pretty-print-sessions.sh"),
                Map.of("SESSIONS_DIR", sessionsDir.toString())
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("metadata:");
        assertThat(result.stdout()).contains("requestedAt: 2026-04-01T19:10:00Z");
        assertThat(result.stdout()).contains("respondedAt: 2026-04-01T19:10:02Z");
        assertThat(result.stdout()).contains("durationMs: 2000");
        assertThat(result.stdout()).contains("totalTokens: 42");
        assertThat(result.stdout()).contains("stopReason: end_turn");
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
        assertThat(result.stdout()).contains("./scripts/reset-session.sh <session-id>");
        assertThat(result.stdout()).contains("Resets the message history");
    }

    @Test
    void resetSessionRequiresSessionId() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/reset-session.sh"), Map.of());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("SESSION_ID is required.");
    }

    @Test
    void resetSessionAcceptsSessionIdAsPositionalArgument() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/reset-session.sh"), Map.of(), "session-123");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Request failed.");
    }

    @Test
    void resetAllSessionsHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/reset-all-sessions.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Clears only the messages array");
        assertThat(result.stdout()).contains("--yes");
    }

    @Test
    void resetAllSessionsAbortsWithoutConfirmation() throws Exception {
        Path sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Path sessionFile = sessionsDir.resolve("session-1.json");
        Files.writeString(sessionFile, """
                {
                  "sessionId": "session-1",
                  "modelId": "amazon.nova-lite-v1:0",
                  "systemPrompt": "keep me",
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

        ProcessResult result = runScriptWithInput(Path.of("scripts/reset-all-sessions.sh"), Map.of("SESSIONS_DIR", sessionsDir.toString()), "no\n");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Type 'yes' to continue:");
        assertThat(result.stdout()).contains("Aborted. No session files were changed.");
        assertThat(Files.readString(sessionFile)).contains("\"messages\": [");
        assertThat(Files.readString(sessionFile)).contains("\"Hello\"");
    }

    @Test
    void resetAllSessionsClearsMessagesWithYesFlag() throws Exception {
        Path sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Path sessionFile = sessionsDir.resolve("session-1.json");
        Files.writeString(sessionFile, """
                {
                  "sessionId": "session-1",
                  "modelId": "amazon.nova-lite-v1:0",
                  "systemPrompt": "keep me",
                  "inferenceConfig": {
                    "temperature": 0.7
                  },
                  "messages": [
                    {
                      "role": "assistant",
                      "content": [
                        { "text": "Hello" }
                      ]
                    }
                  ]
                }
                """);

        ProcessResult result = runScriptWithInput(Path.of("scripts/reset-all-sessions.sh"), Map.of("SESSIONS_DIR", sessionsDir.toString()), "", "--yes");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Reset 1 session file(s).");
        String updated = Files.readString(sessionFile);
        assertThat(updated).contains("\"messages\": []");
        assertThat(updated).contains("\"sessionId\": \"session-1\"");
        assertThat(updated).contains("\"systemPrompt\": \"keep me\"");
        assertThat(updated).contains("\"inferenceConfig\"");
        assertThat(updated).doesNotContain("\"Hello\"");
    }

    @Test
    void deleteAllSessionsHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/delete-all-sessions.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Deletes those session files entirely");
        assertThat(result.stdout()).contains("--yes");
    }

    @Test
    void deleteAllSessionsAbortsWithoutDeleteConfirmation() throws Exception {
        Path sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Path sessionFile = sessionsDir.resolve("session-1.json");
        Files.writeString(sessionFile, """
                {
                  "sessionId": "session-1",
                  "messages": []
                }
                """);

        ProcessResult result = runScriptWithInput(Path.of("scripts/delete-all-sessions.sh"), Map.of("SESSIONS_DIR", sessionsDir.toString()), "no\n");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Type 'delete' to continue:");
        assertThat(result.stdout()).contains("Aborted. No session files were deleted.");
        assertThat(sessionFile).exists();
    }

    @Test
    void deleteAllSessionsRemovesFilesWithYesFlag() throws Exception {
        Path sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Path sessionOne = sessionsDir.resolve("session-1.json");
        Path sessionTwo = sessionsDir.resolve("session-2.json");
        Files.writeString(sessionOne, """
                {
                  "sessionId": "session-1",
                  "messages": []
                }
                """);
        Files.writeString(sessionTwo, """
                {
                  "sessionId": "session-2",
                  "messages": []
                }
                """);

        ProcessResult result = runScriptWithInput(Path.of("scripts/delete-all-sessions.sh"), Map.of("SESSIONS_DIR", sessionsDir.toString()), "", "--yes");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("Deleted 2 session file(s).");
        assertThat(sessionOne).doesNotExist();
        assertThat(sessionTwo).doesNotExist();
    }

    @Test
    void streamMessageHelpWorks() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/stream-message.sh"), Map.of(), "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("./scripts/stream-message.sh <session-id>");
        assertThat(result.stdout()).contains("Prints streamed assistant text as it arrives");
        assertThat(result.stdout()).contains("--raw");
    }

    @Test
    void streamMessageRequiresSessionId() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/stream-message.sh"), Map.of());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("SESSION_ID is required.");
    }

    @Test
    void streamMessageAcceptsSessionIdAsPositionalArgument() throws Exception {
        ProcessResult result = runScript(Path.of("scripts/stream-message.sh"), Map.of(), "session-123");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Request failed.");
    }

    @Test
    void commonScriptFallsBackToPyLauncherWhenWindowsStoreAliasesArePresent() throws Exception {
        assumeBashAvailable();

        Path windowsAppsDir = tempDir.resolve("WindowsApps");
        Files.createDirectories(windowsAppsDir);
        Path fakeBinDir = tempDir.resolve("bin");
        Files.createDirectories(fakeBinDir);

        writeScript(windowsAppsDir.resolve("python"), """
                #!/usr/bin/env bash
                echo "windows store alias" >&2
                exit 1
                """);
        writeScript(windowsAppsDir.resolve("python3"), """
                #!/usr/bin/env bash
                echo "windows store alias" >&2
                exit 1
                """);
        writeScript(fakeBinDir.resolve("py"), """
                #!/usr/bin/env bash
                if [[ "${1:-}" == "-3" ]]; then
                  shift
                fi

                if [[ "${1:-}" == "-c" && "${2:-}" == *"sys.version_info"* ]]; then
                  exit 0
                fi

                if [[ "${1:-}" == "-c" && "${2:-}" == "print('launcher-ok')" ]]; then
                  printf 'launcher-ok\\n'
                  exit 0
                fi

                echo "unexpected args: $*" >&2
                exit 1
                """);

        String bashPath = requireBashPath();
        String pathValue = windowsAppsDir + System.getProperty("path.separator")
                + fakeBinDir + System.getProperty("path.separator")
                + System.getenv("PATH");

        ProcessBuilder processBuilder = new ProcessBuilder(
                bashPath,
                "-lc",
                "source scripts/lib/common.sh && run_python -c \"print('launcher-ok')\""
        );
        processBuilder.directory(REPO_ROOT.toFile());
        processBuilder.environment().put("PATH", pathValue);

        Process process = processBuilder.start();
        String stdout = readStream(process.getInputStream());
        String stderr = readStream(process.getErrorStream());
        int exitCode = process.waitFor();

        assertThat(exitCode).isZero();
        assertThat(stdout).contains("launcher-ok");
        assertThat(stderr).doesNotContain("Python was not found");
    }

    private ProcessResult runScript(Path scriptPath, Map<String, String> env, String... args) throws Exception {
        return runScriptWithInput(scriptPath, env, "", args);
    }

    private HttpServer startHealthServer(int statusCode, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/health", exchange -> writeJsonResponse(exchange, statusCode, responseBody.strip()));
        server.start();
        return server;
    }

    private void writeJsonResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private ProcessResult runScriptWithInput(Path scriptPath, Map<String, String> env, String stdin, String... args) throws Exception {
        assumeBashAvailable();

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
        if (!stdin.isEmpty()) {
            process.getOutputStream().write(stdin.getBytes());
        }
        process.getOutputStream().close();
        String stdout = readStream(process.getInputStream());
        String stderr = readStream(process.getErrorStream());
        int exitCode = process.waitFor();

        return new ProcessResult(exitCode, stdout, stderr);
    }

    private void assumeBashAvailable() {
        try {
            Process process = new ProcessBuilder("bash", "--version").start();
            int exitCode = process.waitFor();
            Assumptions.assumeTrue(exitCode == 0, "bash is required for script smoke tests");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Assumptions.assumeTrue(false, "bash is required for script smoke tests");
        }
    }

    private String requireBashPath() throws Exception {
        Process process = new ProcessBuilder("bash", "-lc", "command -v bash").start();
        String stdout = readStream(process.getInputStream()).trim();
        int exitCode = process.waitFor();
        Assumptions.assumeTrue(exitCode == 0 && !stdout.isBlank(), "bash is required for script smoke tests");
        return stdout;
    }

    private void writeScript(Path path, String content) throws IOException {
        Files.writeString(path, content);
        path.toFile().setExecutable(true);
    }

    private String readStream(java.io.InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes());
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
