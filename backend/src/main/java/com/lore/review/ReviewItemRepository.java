package com.lore.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, UUID> {

    Optional<ReviewItem> findByChangeId(UUID changeId);

    /** With each item's change loaded, so it can be read outside a transaction. */
    @Query("SELECT r FROM ReviewItem r JOIN FETCH r.change ch WHERE ch.session.id = :sessionId")
    List<ReviewItem> findBySessionId(@Param("sessionId") UUID sessionId);
}
