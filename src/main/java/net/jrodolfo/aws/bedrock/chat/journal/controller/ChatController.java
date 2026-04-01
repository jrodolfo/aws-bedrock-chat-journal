package net.jrodolfo.aws.bedrock.chat.journal.controller;

import jakarta.validation.Valid;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;
import net.jrodolfo.aws.bedrock.chat.journal.model.CreateSessionRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageRequest;
import net.jrodolfo.aws.bedrock.chat.journal.model.SendMessageResponse;
import net.jrodolfo.aws.bedrock.chat.journal.service.ChatSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<ChatSession> createSession(@Valid @RequestBody(required = false) CreateSessionRequest request) {
        CreateSessionRequest payload = request != null ? request : new CreateSessionRequest();
        ChatSession session = chatSessionService.createSession(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping("/{sessionId}")
    public ChatSession getSession(@PathVariable String sessionId) {
        return chatSessionService.getSession(sessionId);
    }

    @PostMapping("/{sessionId}/messages")
    public SendMessageResponse sendMessage(@PathVariable String sessionId,
                                           @Valid @RequestBody SendMessageRequest request) {
        return chatSessionService.sendMessage(sessionId, request);
    }
}
