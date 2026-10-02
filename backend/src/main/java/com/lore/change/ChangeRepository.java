package com.lore.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChangeRepository extends JpaRepository<Change, UUID> {

    List<Change> findBySessionIdOrderByDisplayOrder(UUID sessionId);

    @Query("SELECT COUNT(DISTINCT c.filePath) FROM Change c WHERE c.session.id = :sessionId")
    int countDistinctFilePathBySessionId(@Param("sessionId") UUID sessionId);

    int countBySessionId(UUID sessionId);
    int countBySessionIdAndUnderstoodTrue(UUID sessionId);
}
