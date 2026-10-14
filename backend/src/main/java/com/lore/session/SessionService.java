package com.lore.session;

import com.lore.ai.AiService;
import com.lore.ai.dto.SummaryResponse;
import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.git.DiffParser;
import com.lore.git.GitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;
    private final ChangeRepository changeRepository;
    private final GitService gitService;
    private final DiffParser diffParser;
    private final AiService aiService;

    public SessionService(SessionRepository sessionRepository,
                          ChangeRepository changeRepository,
                          GitService gitService,
                          DiffParser diffParser,
                          AiService aiService) {
        this.sessionRepository = sessionRepository;
        this.changeRepository = changeRepository;
        this.gitService = gitService;
        this.diffParser = diffParser;
        this.aiService = aiService;
    }

    public Session start(Path projectDir) throws IOException {
        Path repoRoot = requireRepoRoot(projectDir);
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

    public StopResult stop(Path projectDir, String reason) throws IOException {
        Path repoRoot = requireRepoRoot(projectDir);
        String projectPath = repoRoot.toAbsolutePath().toString();

        Session session = sessionRepository.findByProjectPathAndStatus(
                projectPath, Session.SessionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No session is running."));

        String stopRef = gitService.snapshot(repoRoot, session.getId() + "-stop");
        session.setStopRef(stopRef);

        String startCommit = gitService.resolveRef(repoRoot, session.getStartRef());
        String stopCommit = gitService.resolveRef(repoRoot, stopRef);
        String rawDiff = gitService.diff(repoRoot, startCommit, stopCommit);

        List<DiffParser.ParsedChange> parsed = diffParser.parse(rawDiff);

        if (parsed.isEmpty()) {
            session.setStatus(Session.SessionStatus.DISCARDED);
            session.setStoppedAt(Instant.now());
            sessionRepository.save(session);
            gitService.deleteRef(repoRoot, session.getStartRef());
            gitService.deleteRef(repoRoot, stopRef);
            return new StopResult(session, 0, 0, true);
        }

        session.setReason(reason);
        session.setStatus(Session.SessionStatus.COMPLETE);
        session.setStoppedAt(Instant.now());

        int fileCount = (int) parsed.stream().map(DiffParser.ParsedChange::filePath).distinct().count();

        List<Change> savedChanges = new ArrayList<>();
        for (DiffParser.ParsedChange pc : parsed) {
            Change change = new Change(UUID.randomUUID(), session, pc.filePath(),
                    Change.ChangeType.valueOf(pc.changeType()));
            change.setOldFilePath(pc.oldFilePath());
            change.setOldStart(pc.oldStart());
            change.setOldCount(pc.oldCount());
            change.setNewStart(pc.newStart());
            change.setNewCount(pc.newCount());
            change.setDiffContent(pc.diffContent());
            change.setBinary(pc.binary());
            change.setDisplayOrder(pc.displayOrder());
            savedChanges.add(changeRepository.save(change));
        }

        try {
            List<AiService.ChangeInput> aiInputs = parsed.stream()
                    .map(pc -> new AiService.ChangeInput(
                            pc.filePath(), pc.changeType(), pc.diffContent(), pc.binary()))
                    .toList();

            SummaryResponse summary = aiService.summarize(aiInputs, reason);
            session.setSummary(summary.sessionSummary());
            session.setSummaryStatus(Session.SummaryStatus.COMPLETE);

            for (SummaryResponse.ChangeDescription cd : summary.changeDescriptions()) {
                savedChanges.stream()
                        .filter(c -> c.getDisplayOrder() == cd.displayOrder())
                        .findFirst()
                        .ifPresent(c -> {
                            c.setDescription(cd.description());
                            changeRepository.save(c);
                        });
            }
        } catch (Exception e) {
            log.error("AI summarization failed", e);
            session.setSummaryStatus(Session.SummaryStatus.FAILED);
        }

        sessionRepository.save(session);

        return new StopResult(session, fileCount, parsed.size(), false);
    }

    public Optional<Session> getActive(Path projectDir) throws IOException {
        Path repoRoot = requireRepoRoot(projectDir);
        String projectPath = repoRoot.toAbsolutePath().toString();
        return sessionRepository.findByProjectPathAndStatus(
                projectPath, Session.SessionStatus.ACTIVE);
    }

    private Path requireRepoRoot(Path projectDir) throws IOException {
        if (!gitService.isGitRepo(projectDir)) {
            throw new IllegalStateException(
                    "This folder isn't a git repository. Run git init first, then lore start.");
        }
        return gitService.repoRoot(projectDir);
    }

    public record StopResult(Session session, int fileCount, int changeCount, boolean discarded) {}
}
