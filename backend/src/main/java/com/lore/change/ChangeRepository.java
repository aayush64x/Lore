package com.lore.change;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ChangeRepository extends JpaRepository<Change, UUID> {

    List<Change> findBySessionIdOrderByDisplayOrder(UUID sessionId);
}
