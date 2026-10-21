package com.lore.change;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExplanationRepository extends JpaRepository<Explanation, UUID> {

    Optional<Explanation> findByChangeId(UUID changeId);

    /** Every saved explanation in a session, in one query. */
    List<Explanation> findByChangeSessionId(UUID sessionId);
}
