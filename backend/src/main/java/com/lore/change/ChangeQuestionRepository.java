package com.lore.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChangeQuestionRepository extends JpaRepository<ChangeQuestion, UUID> {

    List<ChangeQuestion> findByChangeIdOrderByCreatedAt(UUID changeId);

    /** With each question's change loaded, so it can be read outside a transaction. */
    @Query("SELECT q FROM ChangeQuestion q JOIN FETCH q.change ch WHERE ch.session.id = :sessionId ORDER BY q.createdAt")
    List<ChangeQuestion> findBySessionId(@Param("sessionId") UUID sessionId);
}
