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
```

The application uses the normal AWS default credential chain. Before calling Bedrock, make sure your local environment already has credentials and Bedrock model access configured.

## Run locally

```bash
./gradlew bootRun
```

The API starts on `http://localhost:8080`.

## Build and test

```bash
./gradlew test build
```

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
    "systemPrompt": "You are a helpful AWS study assistant."
  }'
```

### Get a session

```bash
curl http://localhost:8080/api/sessions/<sessionId>
```

### Send a message

```bash
curl -X POST http://localhost:8080/api/sessions/<sessionId>/messages \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Explain the Amazon Bedrock Converse API in simple terms."
  }'
```

## Curl collection

A small runnable curl collection is included here:

[`requests/curl-examples.sh`](requests/curl-examples.sh)
[`requests/send-message.sh`](requests/send-message.sh)

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

## Project structure

```text
src/main/java/net/jrodolfo/aws/bedrock/chat/journal
├── Application.java
├── config
│   ├── AppProperties.java
│   └── AwsConfig.java
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
│   ├── SendMessageRequest.java
│   └── SendMessageResponse.java
└── service
    ├── BedrockChatService.java
    ├── ChatSessionService.java
    └── FileSessionStore.java
```

## Contact

For issues or inquiries, feel free to contact the maintainer:

- Name: Rod Oliveira
- Role: Software Developer
- Email: jrodolfo@gmail.com
- GitHub: https://github.com/jrodolfo
- LinkedIn: https://www.linkedin.com/in/rodoliveira
- Webpage: https://jrodolfo.net
