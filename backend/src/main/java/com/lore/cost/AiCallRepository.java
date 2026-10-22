package com.lore.cost;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiCallRepository extends JpaRepository<AiCall, UUID> {

    List<AiCall> findBySessionId(UUID sessionId);
}
