![java](https://img.shields.io/badge/java-25-orange)
![shell](https://img.shields.io/badge/shell-bash-89e051)
![spring boot](https://img.shields.io/badge/spring%20boot-3.5.13-6db33f)
![aws bedrock](https://img.shields.io/badge/aws-bedrock-ff9900)
![license](https://img.shields.io/badge/license-MIT-blue)

# AWS Bedrock Chat Journal

Small Spring Boot REST API for learning the Amazon Bedrock Converse API with Java.

## Stack

- Java 25
- Gradle (Groovy)
- Spring Boot 3.5.13
- AWS SDK for Java 2.x
- Local JSON file storage under `data/sessions`

## What the app does

- Creates chat sessions
- Stores each session in one JSON file
- Reloads prior conversation history from disk
- Sends the full conversation to Amazon Bedrock using the Converse API
- Saves the assistant reply back into the same session file
- Runs side-by-side comparisons of different Bedrock models, including saved reports and semantic summaries

Each session file stores:

- `sessionId`
- `modelId`
- `systemPrompt`
- `inferenceConfig`
- `messages`

Assistant messages can also include optional `metadata`, such as:

- request/response timestamps
- duration in milliseconds
- Bedrock stop reason
- token counts when available
- Bedrock latency when available

## Configuration

Example `src/main/resources/application.yml`:

```yaml
app:
  aws:
    region: us-east-1
    default-model-id: amazon.nova-lite-v1:0
  storage:
    sessions-directory: data/sessions
  inference:
    temperature: 0.7
    top-p: 0.9
    max-tokens: 512
```

The application uses the normal AWS default credential chain. Before calling Bedrock, make sure your local environment already has credentials and Bedrock model access configured.

## Architecture

The code follows a small, direct flow:

- `controller/` exposes the REST endpoints
- `service/` handles session lifecycle and Bedrock calls
- `FileSessionStore` reads and writes one JSON file per session
- `BedrockChatService` converts stored messages into AWS SDK Converse requests and extracts the assistant reply

The runtime flow for `POST /api/sessions/{sessionId}/messages` is:

1. Load the session JSON from disk
2. Validate limits and append the user message in memory
3. Send the full conversation history to Amazon Bedrock
4. Append the assistant reply
5. Save the updated session JSON back to disk

The session lifecycle endpoints also let you:

- update `modelId` and `systemPrompt`
- reset only the stored message history
- delete a session file when you no longer need it

There is also a streaming endpoint for assistant replies:

- `POST /api/sessions/{sessionId}/messages/stream`
- response content type: `text/event-stream`
- emits `start`, `chunk`, `complete`, and `error` events
- persists the final assistant reply only after the stream completes successfully

## Build and test

```bash
./gradlew test build
```

On Windows PowerShell or Command Prompt, use:

```powershell
gradlew.bat test build
```

You can also use the helper script:

```bash
./scripts/build-and-test.sh
```

The helper scripts in `scripts/` are Bash scripts. They work on macOS and Linux, and on Windows when run from Git Bash.

For a native PowerShell entrypoint on Windows, use:

```powershell
./scripts/build-and-test.ps1
```

If you want a different Gradle task set for one run:

```bash
GRADLE_TASKS="clean test build" ./scripts/build-and-test.sh
```

## Run locally

```bash
./gradlew bootRun
```

On Windows PowerShell or Command Prompt, use:

```powershell
gradlew.bat bootRun
```

The API starts on `http://localhost:8080`.

You can also use the helper script:

```bash
./scripts/run-local.sh
```

For a native PowerShell entrypoint on Windows, use:

```powershell
./scripts/run-local.ps1
```

If port `8080` is busy, override it for that run:

```bash
PORT=8081 ./scripts/run-local.sh
```

## API documentation

Swagger UI is available when the application is running:

- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/swagger-ui/index.html`

Raw OpenAPI docs are available at:

- `http://localhost:8080/v3/api-docs`
- `http://localhost:8080/v3/api-docs.yaml`

The Swagger UI includes the maintainer contact information from this project:

- Rod Oliveira
- jrodolfo@gmail.com
- https://jrodolfo.net

## Local requirements

- Java 25
- AWS credentials available through the default AWS credential chain
- Bedrock model access enabled for the configured model in the configured region

If a helper script fails because Java 25 is not the active version, set `JAVA_HOME` to a Java 25 installation and put it first on `PATH`.

Common examples:

- macOS
  `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`
  `export PATH="$JAVA_HOME/bin:$PATH"`
- Windows PowerShell
  `$env:JAVA_HOME="C:\Program Files\Java\jdk-25"`
  `$env:Path="$env:JAVA_HOME\bin;$env:Path"`
- Windows Git Bash
  `export JAVA_HOME="/c/Program Files/Java/jdk-25"`
  `export PATH="$JAVA_HOME/bin:$PATH"`
- Amazon Linux 2023
  `sudo dnf install -y java-25-amazon-corretto-devel`
  `export JAVA_HOME=/usr/lib/jvm/java-25-amazon-corretto.x86_64`
  `export PATH="$JAVA_HOME/bin:$PATH"`

## Helper script requirements

The helper scripts in `scripts/` do not all have the same local dependencies.

If you are browsing the `scripts/` folder directly and want the shortest possible guide, start with:

- `scripts/_start-here.txt`

## Summary of scripts

The project includes a collection of Bash and PowerShell scripts for:

- environment checks and setup: verifying Java and Python requirements for local usage
- API interaction: creating sessions, sending messages in synchronous or streaming mode, and resetting sessions
- model comparison: comparing Bedrock models side by side, including saved reports and semantic summaries
- data management: listing, pretty-printing, resetting, and deleting saved sessions or comparison reports

## Windows quick start

If you are running this project on Windows, the simplest setup is:

1. Install Java 25
2. Install Git Bash
3. Install Python 3
4. Verify Python with `py -3 --version`
5. Use Git Bash for the `.sh` helper scripts
6. Optionally use PowerShell for the main workflow wrappers `./scripts/build-and-test.ps1` and `./scripts/run-local.ps1`

## Windows shell support

On Windows, the support model is intentionally split:

- Use Git Bash for the Bash helper scripts under `scripts/*.sh`
- Use PowerShell for the main workflow wrappers `scripts/build-and-test.ps1` and `scripts/run-local.ps1`
- Use PowerShell for standalone Windows helpers such as `scripts/find-port-pid.ps1` and `scripts/stop-port-process.ps1`

The project does not include a PowerShell version of every helper script. The richer helper workflows are documented and supported through Git Bash.

If you only need to inspect which process is listening on a local port, use the dedicated helpers:

- `./scripts/find-port-pid.sh` for macOS and Linux
- `./scripts/find-port-pid.ps1` for Windows PowerShell
- `./scripts/stop-port-process.ps1` for Windows PowerShell when you want to stop that listener

These workflows do not require Python:

- `./scripts/build-and-test.sh`
- `./scripts/run-local.sh`
- `./scripts/build-and-test.ps1`
- `./scripts/run-local.ps1`

These Bash helper scripts require Python 3 in addition to Bash:

- `./scripts/list-sessions.sh`
- `./scripts/pretty-print-sessions.sh`
- `./scripts/list-comparisons.sh`
- `./scripts/pretty-print-comparisons.sh`
- `./scripts/comparison-stats.sh`
- `./scripts/reset-all-sessions.sh`
- `./scripts/stream-message.sh`
- `./scripts/chat.sh`
- `./scripts/compare-models.sh`

On Windows, the scripts accept any of these working launch commands:

- `python3`
- `python`
- `py -3`

The Microsoft Store aliases for `python` and `python3` are not enough by themselves. If those aliases are enabled but Python is not actually installed, the scripts will stop with an explicit error message.

If you want to use the helper scripts on Windows, install Git Bash and run them from a Git Bash session.

The repository also includes native Windows PowerShell wrappers for the main local workflows:

- `scripts/build-and-test.ps1`
- `scripts/run-local.ps1`

The default configuration uses:

- region: `us-east-1`
- model: `amazon.nova-lite-v1:0`
- sessions directory: `data/sessions`
- max stored messages per session: `100`
- default temperature: `0.7`
- default topP: `0.9`
- default maxTokens: `512`

If you want to troubleshoot locally, useful debug logging targets are:

- `net.jrodolfo.aws.bedrock.chat.journal`
- `software.amazon.awssdk`

Example logging configuration:

```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} %-5level [%X{requestId}] %logger{36} - %msg%n"
  level:
    net.jrodolfo.aws.bedrock.chat.journal: DEBUG
    software.amazon.awssdk: INFO
```

Each HTTP response includes an `X-Request-Id` header. If the client sends one, the application reuses it. Otherwise it generates one. The same ID is available to application logs through MDC as `requestId`.

## Endpoints

### Health check

```bash
curl http://localhost:8080/api/health
```

### Create a session

```bash
curl -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "modelId": "amazon.nova-lite-v1:0",
    "systemPrompt": "You are a helpful AWS study assistant.",
    "inferenceConfig": {
      "temperature": 0.4,
      "topP": 0.8,
      "maxTokens": 256
    }
  }'
```

Example response:

```json
{
  "sessionId": "your-session-id",
  "modelId": "amazon.nova-lite-v1:0",
  "systemPrompt": "You are a helpful AWS study assistant.",
  "inferenceConfig": {
    "temperature": 0.4,
    "topP": 0.8,
    "maxTokens": 256
  },
  "messageCount": 0
}
```

Contract note:

- `POST /api/sessions` returns a session summary, not the full stored session JSON
- `GET /api/sessions/{sessionId}` returns the full stored session JSON
- `PATCH /api/sessions/{sessionId}` returns the updated full session JSON
- `POST /api/sessions/{sessionId}/messages` returns the assistant reply plus the assistant message payload
- `POST /api/sessions/{sessionId}/reset` returns the reset full session JSON
- `DELETE /api/sessions/{sessionId}` returns `204 No Content`

### Get a session

```bash
curl http://localhost:8080/api/sessions/<sessionId>
```

### Update a session

```bash
curl -X PATCH http://localhost:8080/api/sessions/<sessionId> \
  -H "Content-Type: application/json" \
  -d '{
    "modelId": "amazon.nova-pro-v1:0",
    "systemPrompt": "You are a concise AWS study assistant.",
    "inferenceConfig": {
      "temperature": 0.2,
      "topP": 0.95,
      "maxTokens": 512
    }
  }'
```

### Send a message

```bash
curl -X POST http://localhost:8080/api/sessions/<sessionId>/messages \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Explain the Amazon Bedrock Converse API in simple terms."
  }'
```

Example response:

```json
{
  "sessionId": "your-session-id",
  "modelId": "amazon.nova-lite-v1:0",
  "reply": "The Converse API lets you send structured chat history to a Bedrock model.",
  "assistantMessage": {
    "role": "assistant",
    "content": [
      {
        "text": "The Converse API lets you send structured chat history to a Bedrock model."
      }
    ],
    "metadata": {
      "requestedAt": "2026-04-01T17:30:00Z",
      "respondedAt": "2026-04-01T17:30:00Z",
      "durationMs": 412,
      "stopReason": "end_turn",
      "inputTokens": 132,
      "outputTokens": 221,
      "totalTokens": 353,
      "bedrockLatencyMs": 398,
      "modelId": "amazon.nova-lite-v1:0",
      "inferenceConfig": {
        "temperature": 0.4,
        "topP": 0.8,
        "maxTokens": 256
      }
    }
  },
  "metadata": {
    "requestedAt": "2026-04-01T17:30:00Z",
    "respondedAt": "2026-04-01T17:30:00Z",
    "durationMs": 412,
    "stopReason": "end_turn",
    "inputTokens": 132,
    "outputTokens": 221,
    "totalTokens": 353,
    "bedrockLatencyMs": 398,
    "modelId": "amazon.nova-lite-v1:0",
    "inferenceConfig": {
      "temperature": 0.4,
      "topP": 0.8,
      "maxTokens": 256
    }
  }
}
```

### Stream a message

```bash
curl --no-buffer \
  -X POST http://localhost:8080/api/sessions/<sessionId>/messages/stream \
  -H "Accept: text/event-stream" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Explain the Amazon Bedrock Converse API using streaming."
  }'
```

Example SSE output:

```text
event:start
data:{"type":"start"}

event:chunk
data:{"type":"chunk","text":"The "}

event:chunk
data:{"type":"chunk","text":"Converse "}

event:chunk
data:{"type":"chunk","text":"stream API ..."}

event:complete
data:{"type":"complete","response":{...}}
```

Testing note:

- Swagger UI documents this endpoint, but `curl --no-buffer` or the helper script is the easiest way to test it
- if the stream fails before completion, the partial assistant reply is not persisted

### Reset a session

```bash
curl -X POST http://localhost:8080/api/sessions/<sessionId>/reset
```

### Delete a session

```bash
curl -X DELETE http://localhost:8080/api/sessions/<sessionId>
```

### Example error responses

Validation failure for a blank message:

```bash
curl -X POST http://localhost:8080/api/sessions/<sessionId>/messages \
  -H "Content-Type: application/json" \
  -d '{
    "text": "   "
  }'
```

Example response:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": [
    "text: text is required"
  ]
}
```

Missing session:

```bash
curl http://localhost:8080/api/sessions/missing-session-id
```

Example response:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Session not found: missing-session-id",
  "details": []
}
```

Bedrock access or runtime failure:

```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "Failed to call Amazon Bedrock: ...",
  "details": []
}
```

Session limit exceeded:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Session has reached the maximum number of stored messages: 100",
  "details": []
}
```

## Operational limits

- Max user message size: `4000` characters
- Max system prompt size: `4000` characters
- Max model ID size: `200` characters
- Temperature range: `0.0` to `1.0`
- TopP range: `0.0` to `1.0`
- Max tokens minimum: `1`
- Max stored messages per session: `100` by default
- Session files are stored under `data/sessions`
- If a Bedrock call fails, the new message exchange is not persisted to disk
- Some Bedrock metadata fields may be absent depending on model behavior and SDK response content

## Notes

- `ChatSessionService` depends on a `SessionStore` abstraction, with `FileSessionStore` as the default implementation
- This keeps the local file-based design simple now while leaving room for other storage implementations later

## Curl collection

On Windows, run the scripts in this section from Git Bash unless the command explicitly uses one of the PowerShell wrappers.

A small runnable curl collection is included here:

[`scripts/curl-examples.sh`](scripts/curl-examples.sh)
[`scripts/check-backend.sh`](scripts/check-backend.sh)
[`scripts/find-port-pid.sh`](scripts/find-port-pid.sh)
[`scripts/find-port-pid.ps1`](scripts/find-port-pid.ps1)
[`scripts/stop-port-process.ps1`](scripts/stop-port-process.ps1)
[`scripts/list-sessions.sh`](scripts/list-sessions.sh)
[`scripts/reset-session.sh`](scripts/reset-session.sh)
[`scripts/run-local.sh`](scripts/run-local.sh)
[`scripts/send-message.sh`](scripts/send-message.sh)
[`scripts/stream-message.sh`](scripts/stream-message.sh)
[`scripts/pretty-print-sessions.sh`](scripts/pretty-print-sessions.sh)

Run it:

```bash
./scripts/curl-examples.sh
```

### Check backend health

Use this script when you only want to confirm that the Spring Boot backend is reachable before running the more API-heavy helpers:

```bash
./scripts/check-backend.sh
```

Show help:

```bash
./scripts/check-backend.sh --help
```

What the script does:

- Calls `GET /api/health`
- Confirms that the response JSON contains `status=OK`
- Exits non-zero with a short hint when the backend is unavailable

Optional environment variables:

- `BASE_URL`
  Default: `http://localhost:8080`

### Find the PID listening on a port

Use these scripts when you want to identify the process listening on a local TCP port.

macOS and Linux:

```bash
./scripts/find-port-pid.sh
./scripts/find-port-pid.sh 8081
```

Windows PowerShell:

```powershell
./scripts/find-port-pid.ps1
./scripts/find-port-pid.ps1 8081
```

On Windows, prefer the PowerShell script. The Bash version is intended for macOS and Linux rather than Git Bash.

If you want to stop the listener on a port from Windows PowerShell:

```powershell
./scripts/stop-port-process.ps1
./scripts/stop-port-process.ps1 8081
```

Example:

```bash
BASE_URL=http://localhost:8081 ./scripts/check-backend.sh
```

### Usage

Show the built-in help:

```bash
./scripts/curl-examples.sh --help
```

What the script does:

- Calls `GET /api/health`
- Creates a new session with `POST /api/sessions`
- Fetches that session with `GET /api/sessions/{sessionId}`
- Sends one message with `POST /api/sessions/{sessionId}/messages`
- Fetches the updated session again

Prerequisites:

- The Spring Boot app must already be running
- Your AWS credentials must be available through the default AWS credential chain
- Your configured Bedrock model must be enabled in the configured AWS region

Supported environment variables:

- `BASE_URL`
  Default: `http://localhost:8080`
- `MODEL_ID`
  Default: `amazon.nova-lite-v1:0`
- `SYSTEM_PROMPT`
  Default: `You are a helpful AWS study assistant.`
- `MESSAGE_TEXT`
  Default: `Explain the Amazon Bedrock Converse API in simple terms.`

Examples:

```bash
BASE_URL=http://localhost:8080 MODEL_ID=amazon.nova-lite-v1:0 ./scripts/curl-examples.sh
```

```bash
SYSTEM_PROMPT="You are an AWS certification coach." \
MESSAGE_TEXT="Give me 3 exam-focused Bedrock Converse API tips." \
./scripts/curl-examples.sh
```

### List stored sessions

Use this script to get a quick overview of saved sessions before inspecting or continuing one:

```bash
./scripts/list-sessions.sh
```

Show help:

```bash
./scripts/list-sessions.sh --help
```

What it does:

- Reads every `*.json` file under `data/sessions`
- Prints the session file name
- Prints `sessionId`, `modelId`, and message count
- Prints a short `systemPrompt` preview

Optional environment variables:

- `SESSIONS_DIR`
  Default: `<repo-root>/data/sessions`

### Continue an existing session

Use this script when you already have a `sessionId` and want to send another message:

```bash
./scripts/send-message.sh --help
```

Example:

```bash
./scripts/send-message.sh your-session-id
```

That form prompts you for the message text interactively.

If you want to send the message non-interactively, pass it explicitly:

```bash
./scripts/send-message.sh \
  your-session-id \
  "Continue the previous explanation and compare Converse with InvokeModel."
```

Preferred positional arguments:

- `session-id`
- `message-text` (optional)

This script follows the current helper-script convention:

- prefer positional arguments for simple session-oriented commands
- keep environment variables as a backward-compatible scripting option
- prompt interactively when required input is omitted in an interactive terminal

Optional environment variables:

- `SESSION_ID`
  Used when no positional `session-id` is provided
- `BASE_URL`
  Default: `http://localhost:8080`
- `MESSAGE_TEXT`
  Used when no positional `message-text` is provided

The older environment-variable form still works:

```bash
SESSION_ID=your-session-id \
MESSAGE_TEXT="Continue the previous explanation and compare Converse with InvokeModel." \
./scripts/send-message.sh
```

`send-message.sh` and `stream-message.sh` are similar in purpose because both send one message and then exit. The difference is in how the reply is delivered:

- `send-message.sh` waits for the full assistant reply and prints it once at the end
- `stream-message.sh` prints the assistant reply incrementally as Bedrock streams it back

Use `send-message.sh` for quick checks and shorter replies. Use `stream-message.sh` when you want faster feedback, a more conversational feel, or you want to observe Bedrock streaming behavior directly.

When the backend is unavailable, the API-focused helper scripts print a short hint that points you back to `./scripts/run-local.sh`.

### Compare two models

Use this script when you want to send the same prompt to two Bedrock models and keep only the comparison result, not the temporary chat sessions:

```bash
./scripts/compare-models.sh --help
```

Example:

```bash
MODEL_A=amazon.nova-lite-v1:0 \
MODEL_B=amazon.nova-pro-v1:0 \
MESSAGE_TEXT="Explain the Bedrock Converse API in simple terms." \
./scripts/compare-models.sh
```

You can also compare with different prompts or different inference settings on each side:

```bash
MODEL_A=amazon.nova-lite-v1:0 \
MODEL_B=amazon.nova-pro-v1:0 \
SYSTEM_PROMPT_A="You are concise and direct." \
SYSTEM_PROMPT_B="You are detailed and exam-oriented." \
TEMPERATURE_A=0.2 \
TEMPERATURE_B=0.8 \
MESSAGE_TEXT="Explain how Converse differs from InvokeModel." \
./scripts/compare-models.sh
```

You can also use the same named prompt preset idea from `chat.sh`:

```bash
MODEL_A=amazon.nova-lite-v1:0 \
MODEL_B=amazon.nova-pro-v1:0 \
PROMPT_PRESET=exam-tutor \
MESSAGE_TEXT="Explain how Converse differs from InvokeModel." \
./scripts/compare-models.sh
```

Or mix presets side by side:

```bash
MODEL_A=amazon.nova-lite-v1:0 \
MODEL_B=amazon.nova-pro-v1:0 \
PROMPT_PRESET_A=exam-tutor \
PROMPT_PRESET_B=bedrock-accuracy \
MESSAGE_TEXT="Explain guardrails in Amazon Bedrock." \
./scripts/compare-models.sh
```

In this comparison tooling, `inference` means the generation settings that shape the reply. In practice, that means values such as:

- `temperature` for randomness
- `topP` for probability-based token filtering
- `maxTokens` for response length limits

The script supports shared and per-side settings:

- `PROMPT_PRESET` for one shared named prompt preset
- `PROMPT_PRESET_A` and `PROMPT_PRESET_B` for side-specific preset overrides
- `SYSTEM_PROMPT` for one shared prompt
- `SYSTEM_PROMPT_A` and `SYSTEM_PROMPT_B` for side-specific prompts
- `TEMPERATURE_A` and `TEMPERATURE_B`
- `TOP_P_A` and `TOP_P_B`
- `MAX_TOKENS_A` and `MAX_TOKENS_B`
- `SUMMARY_MODEL` if you want a different model to write the semantic key-differences summary

Prompt precedence is:

1. `SYSTEM_PROMPT_A` or `SYSTEM_PROMPT_B`
2. `PROMPT_PRESET_A` or `PROMPT_PRESET_B`
3. shared `PROMPT_PRESET`
4. shared `SYSTEM_PROMPT`

Available prompt presets:

- `exam-tutor`
  Certification-style explanations, distinctions, and tradeoffs
- `quiz-me`
  One question at a time, then brief grading and correction
- `bedrock-accuracy`
  Conservative wording that avoids invented AWS details
- `compare-services`
  Structured side-by-side comparisons of AWS options

Behavior:

- creates two temporary sessions, one for each model
- sends the same prompt to both models
- saves one comparison JSON report under `data/comparisons/`
- records the exact per-side setup, including prompt and inference overrides
- records preset names when presets were used
- computes a small summary showing things like faster model, lower token usage, and shorter reply
- optionally adds a Bedrock-generated semantic summary of the key differences between the two replies
- prints both replies and their metadata
- deletes the temporary sessions after the comparison

This keeps your normal session history clean while still preserving the useful comparison artifact.

### List saved comparisons

Use this script when you want to see which comparison reports are stored under `data/comparisons`:

```bash
./scripts/list-comparisons.sh --help
```

Example:

```bash
./scripts/list-comparisons.sh
```

### Pretty-print saved comparisons

Use this script when you want to inspect saved comparison reports in a more readable terminal view:

```bash
./scripts/pretty-print-comparisons.sh --help
```

Examples:

```bash
./scripts/pretty-print-comparisons.sh
```

```bash
./scripts/pretty-print-comparisons.sh --raw
```

Rendered comparison output includes:

- the original prompt
- per-side prompt and inference setup when present
- per-side prompt preset names when present
- the structured summary
- the optional semantic key-differences summary
- both model replies and their metadata

### Comparison stats

Use this script when you want a quick aggregate view of the comparison reports you have already saved:

```bash
./scripts/comparison-stats.sh --help
```

Example:

```bash
./scripts/comparison-stats.sh
```

It summarizes things like:

- total comparison count
- distinct models used
- how often each model appears
- average duration per model
- average total tokens per model
- how often each model was faster
- how often each model used fewer tokens

Prompt presets are predefined system prompts with short names. Instead of typing a long study-oriented system prompt every time, you can choose a preset such as `exam-tutor` or `bedrock-accuracy` and let the script apply that system prompt to the session. This is useful for experimenting with how different prompt styles change model behavior while keeping the workflow fast and consistent.

### Chat interactively

Use this script when you want to stay in a terminal chat loop instead of sending one message at a time:

```bash
./scripts/chat.sh --help
```

Examples:

```bash
./scripts/chat.sh
```

```bash
SESSION_ID=your-session-id ./scripts/chat.sh
```

```bash
STREAM=false SESSION_ID=your-session-id ./scripts/chat.sh
```

```bash
PROMPT_PRESET=exam-tutor ./scripts/chat.sh
```

Behavior:

- reuses `SESSION_ID` when provided
- otherwise creates a new session automatically
- defaults to streaming mode
- supports `/help`, `/session`, `/preset`, `/preset <name>`, `/model`, `/model <id>`, `/prompt`, `/prompt <text>`, `/history`, `/show-config`, `/temperature <value>`, `/top-p <value>`, `/max-tokens <n>`, `/stream on`, `/stream off`, `/metadata on`, `/metadata off`, `/reset`, and `/exit`
- can hide or show response metadata during the chat loop
- can tune the current session model, prompt, and inference settings without leaving the terminal chat
- can apply built-in prompt presets for different study modes

Built-in prompt presets:

- `exam-tutor`
  Focuses on certification-style explanations, distinctions, and tradeoffs.
- `quiz-me`
  Asks one study question at a time, waits for your answer, then grades it.
- `bedrock-accuracy`
  Uses more conservative wording, separates facts from assumptions, and avoids invented AWS details.
- `compare-services`
  Uses a structured comparison style for AWS services, models, or approaches.

### Reset an existing session

Use this script when you want to clear the stored messages for a session but keep its metadata:

```bash
./scripts/reset-session.sh --help
```

Example:

```bash
./scripts/reset-session.sh your-session-id
```

This script also prefers a positional `session-id`, while still accepting `SESSION_ID=...` for backward compatibility.

The older environment-variable form still works:

```bash
SESSION_ID=your-session-id ./scripts/reset-session.sh
```

### Reset all sessions

Use this script when you want to clear the stored messages for every saved session but keep the session files and metadata:

```bash
./scripts/reset-all-sessions.sh --help
```

Interactive example:

```bash
./scripts/reset-all-sessions.sh
```

Non-interactive example:

```bash
./scripts/reset-all-sessions.sh --yes
```

Optional environment variables:

- `SESSIONS_DIR`
  Default: `<repo-root>/data/sessions`

### Delete all sessions

Use this script when you want to remove every saved session JSON file entirely:

```bash
./scripts/delete-all-sessions.sh --help
```

Interactive example:

```bash
./scripts/delete-all-sessions.sh
```

Non-interactive example:

```bash
./scripts/delete-all-sessions.sh --yes
```

Optional environment variables:

- `SESSIONS_DIR`
  Default: `<repo-root>/data/sessions`

### Stream a reply from an existing session

Use this script when you want to see assistant text chunks as Bedrock streams them back:

```bash
./scripts/stream-message.sh --help
```

Example:

```bash
./scripts/stream-message.sh \
  your-session-id \
  "Explain the Amazon Bedrock Converse API using streaming."
```

By default, the script renders only the assistant text in a readable terminal view and then prints a short completion summary.
It also strips common Markdown markers such as heading prefixes, bold markers, and backticks while streaming.
The completion summary prioritizes request/response timestamps, duration, and token counts when available.

If you want to inspect the raw SSE frames instead, use:

```bash
./scripts/stream-message.sh your-session-id --raw
```

This script also prefers positional arguments, while still accepting `SESSION_ID=...` and `MESSAGE_TEXT=...` for backward compatibility.

The older environment-variable form still works:

```bash
SESSION_ID=your-session-id \
MESSAGE_TEXT="Explain the Amazon Bedrock Converse API using streaming." \
./scripts/stream-message.sh
```

### Pretty-print stored sessions

Use this script to inspect saved session JSON files in a more readable terminal view:

```bash
./scripts/pretty-print-sessions.sh
```

Show help:

```bash
./scripts/pretty-print-sessions.sh --help
```

What it does:

- Reads every `*.json` file under `data/sessions`
- Prints session metadata such as `sessionId`, `modelId`, and `systemPrompt`
- Renders conversation text with decoded line breaks
- Shows stored assistant response metadata such as timestamps, duration, and token counts when available
- Cleans up common Markdown markers like `###` and `**` for easier terminal reading

Optional environment variables:

- `SESSIONS_DIR`
  Default: `<repo-root>/data/sessions`

Options:

- `--raw`
  Shows pretty-printed raw JSON instead of the rendered conversation view

Examples:

```bash
./scripts/pretty-print-sessions.sh
```

```bash
./scripts/pretty-print-sessions.sh --raw
```

```bash
SESSIONS_DIR=data/sessions ./scripts/pretty-print-sessions.sh
```

## Project structure

```text
data
├── comparisons
│   └── .gitkeep
└── sessions
    └── .gitkeep
scripts
├── build-and-test.sh
├── chat.sh
├── comparison-stats.sh
├── compare-models.sh
├── curl-examples.sh
├── delete-all-sessions.sh
├── list-comparisons.sh
├── list-sessions.sh
├── pretty-print-comparisons.sh
├── pretty-print-sessions.sh
├── reset-all-sessions.sh
├── reset-session.sh
├── run-local.sh
├── send-message.sh
└── stream-message.sh
src/main/java/net/jrodolfo/aws/bedrock/chat/journal
├── Application.java
├── config
│   ├── AppProperties.java
│   ├── AwsConfig.java
│   └── RequestCorrelationFilter.java
├── controller
│   ├── ChatController.java
│   └── HealthController.java
├── exception
│   ├── BadRequestException.java
│   ├── BedrockInvocationException.java
│   ├── GlobalExceptionHandler.java
│   ├── ResourceNotFoundException.java
│   └── SessionStorageException.java
├── model
│   ├── ApiErrorResponse.java
│   ├── ChatMessage.java
│   ├── ChatSession.java
│   ├── ContentBlock.java
│   ├── CreateSessionRequest.java
│   ├── CreateSessionResponse.java
│   ├── SendMessageRequest.java
│   ├── SendMessageResponse.java
│   └── UpdateSessionRequest.java
└── service
    ├── BedrockChatService.java
    ├── ChatSessionService.java
    ├── FileSessionStore.java
    └── SessionStore.java
src/main/resources
└── application.yml
src/test/java/net/jrodolfo/aws/bedrock/chat/journal
├── ApplicationTests.java
├── ChatSessionIntegrationTest.java
├── controller
│   └── ChatControllerTest.java
├── requests
│   └── RequestScriptsSmokeTest.java
└── service
    ├── BedrockChatServiceTest.java
    ├── ChatSessionServiceTest.java
    ├── FileSessionStoreDeleteTest.java
    └── FileSessionStoreTest.java
```

## Contact

For issues or inquiries, feel free to contact the maintainer:

- Name: Rod Oliveira
- Role: Software Developer
- Email: jrodolfo@gmail.com
- GitHub: https://github.com/jrodolfo
- LinkedIn: https://www.linkedin.com/in/rodoliveira
- Webpage: https://jrodolfo.net
