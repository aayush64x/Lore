package com.lore.session;

import com.lore.git.GitService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final GitService gitService;

    public SessionService(SessionRepository sessionRepository, GitService gitService) {
        this.sessionRepository = sessionRepository;
        this.gitService = gitService;
    }

    public Session start(Path projectDir) throws IOException {
        Path repoRoot = gitService.repoRoot(projectDir);
        String projectPath = repoRoot.toAbsolutePath().toString();

        Optional<Session> active = sessionRepository.findByProjectPathAndStatus(
                projectPath, Session.SessionStatus.ACTIVE);
        if (active.isPresent()) {
            throw new IllegalStateException(
                    "A session is already running since " + active.get().getStartedAt());
        }

        UUID sessionId = UUID.randomUUID();
        String ref = gitService.snapshot(repoRoot, sessionId.toString());

        Session session = new Session(sessionId, projectPath, ref);
        return sessionRepository.save(session);
    }

    public Optional<Session> getActive(Path projectDir) throws IOException {
        Path repoRoot = gitService.repoRoot(projectDir);
        String projectPath = repoRoot.toAbsolutePath().toString();
        return sessionRepository.findByProjectPathAndStatus(
                projectPath, Session.SessionStatus.ACTIVE);
    }
}
