package com.lore.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The queries load each item's change and session, so they can be read outside a transaction. */
public interface ReviewItemRepository extends JpaRepository<ReviewItem, UUID> {

    @Query("SELECT r FROM ReviewItem r JOIN FETCH r.change ch JOIN FETCH ch.session WHERE ch.id = :changeId")
    Optional<ReviewItem> findByChangeId(@Param("changeId") UUID changeId);

    @Query("SELECT r FROM ReviewItem r JOIN FETCH r.change ch WHERE ch.session.id = :sessionId")
    List<ReviewItem> findBySessionId(@Param("sessionId") UUID sessionId);

    /** Every item in a project's stopped sessions, soonest due first. */
    @Query("SELECT r FROM ReviewItem r JOIN FETCH r.change ch JOIN FETCH ch.session s "
            + "WHERE s.projectPath = :project AND s.status = com.lore.session.Session.SessionStatus.COMPLETE "
            + "ORDER BY r.dueAt")
    List<ReviewItem> findInProject(@Param("project") String project);
}
