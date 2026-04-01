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

Each session file stores:

- `sessionId`
- `modelId`
- `systemPrompt`
- `inferenceConfig`
- `messages`

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

## Build and test

```bash
./gradlew test build
```

## Run locally

```bash
./gradlew bootRun
```

The API starts on `http://localhost:8080`.

You can also use the helper script:

```bash
./requests/run-local.sh
```

If port `8080` is busy, override it for that run:

```bash
PORT=8081 ./requests/run-local.sh
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

## Notes

- `ChatSessionService` depends on a `SessionStore` abstraction, with `FileSessionStore` as the default implementation
- This keeps the local file-based design simple now while leaving room for other storage implementations later

## Curl collection

A small runnable curl collection is included here:

[`requests/curl-examples.sh`](requests/curl-examples.sh)
[`requests/list-sessions.sh`](requests/list-sessions.sh)
[`requests/reset-session.sh`](requests/reset-session.sh)
[`requests/run-local.sh`](requests/run-local.sh)
[`requests/send-message.sh`](requests/send-message.sh)
[`requests/pretty-print-sessions.sh`](requests/pretty-print-sessions.sh)

Run it:

```bash
./requests/curl-examples.sh
```

### Usage

Show the built-in help:

```bash
./requests/curl-examples.sh --help
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
BASE_URL=http://localhost:8080 MODEL_ID=amazon.nova-lite-v1:0 ./requests/curl-examples.sh
```

```bash
SYSTEM_PROMPT="You are an AWS certification coach." \
MESSAGE_TEXT="Give me 3 exam-focused Bedrock Converse API tips." \
./requests/curl-examples.sh
```

### List stored sessions

Use this script to get a quick overview of saved sessions before inspecting or continuing one:

```bash
./requests/list-sessions.sh
```

Show help:

```bash
./requests/list-sessions.sh --help
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
./requests/send-message.sh --help
```

Example:

```bash
SESSION_ID=your-session-id \
MESSAGE_TEXT="Continue the previous explanation and compare Converse with InvokeModel." \
./requests/send-message.sh
```

Required environment variables:

- `SESSION_ID`

Optional environment variables:

- `BASE_URL`
  Default: `http://localhost:8080`
- `MESSAGE_TEXT`
  Default: `Continue the conversation.`

### Reset an existing session

Use this script when you want to clear the stored messages for a session but keep its metadata:

```bash
./requests/reset-session.sh --help
```

Example:

```bash
SESSION_ID=your-session-id ./requests/reset-session.sh
```

### Pretty-print stored sessions

Use this script to inspect saved session JSON files in a more readable terminal view:

```bash
./requests/pretty-print-sessions.sh
```

Show help:

```bash
./requests/pretty-print-sessions.sh --help
```

What it does:

- Reads every `*.json` file under `data/sessions`
- Prints session metadata such as `sessionId`, `modelId`, and `systemPrompt`
- Renders conversation text with decoded line breaks
- Cleans up common Markdown markers like `###` and `**` for easier terminal reading

Optional environment variables:

- `SESSIONS_DIR`
  Default: `<repo-root>/data/sessions`

Options:

- `--raw`
  Shows pretty-printed raw JSON instead of the rendered conversation view

Examples:

```bash
./requests/pretty-print-sessions.sh
```

```bash
./requests/pretty-print-sessions.sh --raw
```

```bash
SESSIONS_DIR=data/sessions ./requests/pretty-print-sessions.sh
```

## Project structure

```text
data
└── sessions
    └── .gitkeep
requests
├── curl-examples.sh
├── list-sessions.sh
├── pretty-print-sessions.sh
├── reset-session.sh
├── run-local.sh
└── send-message.sh
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
