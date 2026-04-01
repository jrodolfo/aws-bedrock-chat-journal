package net.jrodolfo.aws.bedrock.chat.journal.service;

import java.util.Optional;
import net.jrodolfo.aws.bedrock.chat.journal.model.ChatSession;

public interface SessionStore {

    ChatSession save(ChatSession session);

    Optional<ChatSession> load(String sessionId);
}
