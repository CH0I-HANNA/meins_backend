package com.mcm.onboarding.domain.chat.repository;

import com.mcm.onboarding.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByTagCodeOrderByCreatedAtAsc(String tagCode);

    void deleteByTagCode(String tagCode);
}
