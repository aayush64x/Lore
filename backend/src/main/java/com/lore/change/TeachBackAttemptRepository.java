package com.lore.change;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TeachBackAttemptRepository extends JpaRepository<TeachBackAttempt, UUID> {

    List<TeachBackAttempt> findByChangeIdOrderByCreatedAt(UUID changeId);

    /** Every attempt in a session, in one query, for the session page. */
    List<TeachBackAttempt> findByChangeSessionIdOrderByCreatedAt(UUID sessionId);
}
