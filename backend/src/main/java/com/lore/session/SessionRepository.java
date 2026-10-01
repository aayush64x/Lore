package com.lore.session;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByProjectPathAndStatus(String projectPath, Session.SessionStatus status);
}
