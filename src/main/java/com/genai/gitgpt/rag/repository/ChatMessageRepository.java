package com.genai.gitgpt.rag.repository;

import com.genai.gitgpt.rag.model.ChatMessage;
import com.genai.gitgpt.rag.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);
}
