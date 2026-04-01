package net.jrodolfo.aws.bedrock.chat.journal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionResponse;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageResponse;
import net.jrodolfo.aws.bedrock.chat.journal.model.StreamEvent;
import net.jrodolfo.aws.bedrock.chat.journal.model.UpdateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.service.ChatSessionService;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class ChatController {

    private final ChatSessionService chatSessionService;

    public ChatController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    @Operation(summary = "Create a chat session", description = "Creates a new local session with model, system prompt, and inference settings.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session created"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class)))
    })
    public ResponseEntity<CreateSessionResponse> createSession(@Valid @RequestBody(required = false) CreateSessionRequest request) {
        CreateSessionRequest payload = request != null ? request : new CreateSessionRequest();
        ChatSession session = chatSessionService.createSession(payload);
        CreateSessionResponse response = new CreateSessionResponse(
                session.getSessionId(),
                session.getModelId(),
                session.getSystemPrompt(),
                session.getInferenceConfig(),
                session.getMessages() != null ? session.getMessages().size() : 0
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get a session", description = "Returns the full stored session JSON, including all messages.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session found"),
            @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class)))
    })
    public ChatSession getSession(@PathVariable String sessionId) {
        return chatSessionService.getSession(sessionId);
    }

    @PatchMapping("/{sessionId}")
    @Operation(summary = "Update a session", description = "Updates model, system prompt, or inference settings for an existing session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class)))
    })
    public ChatSession updateSession(@PathVariable String sessionId,
                                     @Valid @RequestBody(required = false) UpdateSessionRequest request) {
        return chatSessionService.updateSession(sessionId, request);
    }

    @PostMapping("/{sessionId}/messages")
    @Operation(summary = "Send a message", description = "Appends a user message, calls Amazon Bedrock Converse, stores the assistant reply, and returns it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reply generated"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Bedrock or storage failure", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class)))
    })
    public SendMessageResponse sendMessage(@PathVariable String sessionId,
                                           @Valid @RequestBody SendMessageRequest request) {
        return chatSessionService.sendMessage(sessionId, request);
    }

    @PostMapping(value = "/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream a message reply", description = "Streams assistant text chunks from Amazon Bedrock using server-sent events and persists the final reply only after successful completion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Streaming started"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Bedrock or storage failure", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class)))
    })
    public SseEmitter streamMessage(@PathVariable String sessionId,
                                    @Valid @RequestBody SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        sendEvent(emitter, "start", StreamEvent.start());

        chatSessionService.streamMessage(sessionId, request, chunk -> sendEvent(emitter, "chunk", StreamEvent.chunk(chunk)))
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        sendEvent(emitter, "error", StreamEvent.error(throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage()));
                        emitter.complete();
                        return;
                    }

                    sendEvent(emitter, "complete", StreamEvent.complete(response));
                    emitter.complete();
                });

        return emitter;
    }

    @PostMapping("/{sessionId}/reset")
    @Operation(summary = "Reset a session", description = "Clears stored messages but keeps session metadata such as model, prompt, and inference settings.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session reset"),
            @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class)))
    })
    public ChatSession resetSession(@PathVariable String sessionId) {
        return chatSessionService.resetSession(sessionId);
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Delete a session", description = "Deletes the stored session JSON file.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session deleted"),
            @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(schema = @Schema(implementation = net.jrodolfo.aws.bedrock.chat.journal.model.ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        chatSessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    private void sendEvent(SseEmitter emitter, String name, StreamEvent payload) {
        try {
            emitter.send(SseEmitter.event().name(name).data(payload));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to send SSE event", ex);
        }
    }
}
