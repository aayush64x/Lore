package com.lore.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lore.ai.AiOutputCheck;
import com.lore.ai.AiService;
import com.lore.ai.AiUnavailableException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SessionService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /**
     * Above this many changes the summary is asked for in parts of this size, then joined.
     * One answer has to describe every change it was given, and 330 changes in one call ran
     * past the answer's length limit and got cut off.
     */
    static final int SUMMARY_BATCH_SIZE = 60;
    /** How many parts are asked for at the same time. */
    private static final int SUMMARY_THREADS = 4;

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
            return new StopResult(session, 0, 0, true, null);
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

        String failure = generateSummary(session, savedChanges);
        if (failure != null) {
            session.setSummary(null);
            session.setSummaryStatus(Session.SummaryStatus.FAILED);
        }

        sessionRepository.save(session);

        return new StopResult(session, fileCount, parsed.size(), false, failure);
    }

    public Optional<Session> getActive(Path projectDir) throws IOException {
        Path repoRoot = requireRepoRoot(projectDir);
        String projectPath = repoRoot.toAbsolutePath().toString();
        return sessionRepository.findByProjectPathAndStatus(
                projectPath, Session.SessionStatus.ACTIVE);
    }

    /**
     * Runs the AI summary again for a stopped session, using its saved changes.
     * On failure a previously good summary is kept; otherwise the summary is marked FAILED.
     *
     * @throws java.util.NoSuchElementException if the session doesn't exist
     * @throws IllegalStateException if the session is still running or was discarded
     */
    public RetryResult retrySummary(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        if (session.getStatus() != Session.SessionStatus.COMPLETE) {
            throw new IllegalStateException("Only stopped sessions can be summarized.");
        }
        List<Change> changes = changeRepository.findBySessionIdOrderByDisplayOrder(sessionId);

        String failure = generateSummary(session, changes);
        if (failure != null && session.getSummaryStatus() != Session.SummaryStatus.COMPLETE) {
            session.setSummary(null);
            session.setSummaryStatus(Session.SummaryStatus.FAILED);
        }
        sessionRepository.save(session);
        return new RetryResult(session, failure);
    }

    /**
     * Asks the AI for a summary and per-change descriptions. On success, saves them on the
     * session and changes and returns null. On failure, changes nothing and returns a short
     * reason that is safe to show the user (never contains the API key).
     */
    private String generateSummary(Session session, List<Change> changes) {
        try {
            List<AiService.ChangeInput> aiInputs = changes.stream()
                    .map(c -> new AiService.ChangeInput(c.getDisplayOrder(), c.getFilePath(),
                            c.getChangeType().name(), c.getDiffContent(), c.isBinary()))
                    .toList();

            SummaryResponse summary = aiInputs.size() <= SUMMARY_BATCH_SIZE
                    ? askSummary(aiInputs, session.getReason())
                    : summarizeInParts(aiInputs, session.getReason());
            if (summary == null || summary.sessionSummary() == null || summary.sessionSummary().isBlank()) {
                throw new AiUnavailableException("AI returned an empty summary.");
            }
            session.setSummary(summary.sessionSummary().strip());
            session.setSummaryStatus(Session.SummaryStatus.COMPLETE);
            // null = goal not checked (no goal given); a list, maybe empty, = checked.
            session.setGoalGaps(summary.goalPartsNotDone() == null ? null : toJson(summary.goalPartsNotDone()));

            // Cleared first, so a retry never leaves an earlier answer on a change.
            changes.forEach(c -> {
                c.setDescription(null);
                c.setGoalMatch(null);
                c.setGoalReason(null);
            });
            applyDescriptions(changes, summary.changeDescriptions());
            changeRepository.saveAll(changes);
            return null;
        } catch (AiUnavailableException e) {
            // Expected (e.g. no key yet). The reason goes back to the caller, which shows it.
            log.debug("AI summary not saved: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("AI summarization failed", e);
            return "AI summary failed.";
        }
    }

    /** One summary call, asked once more if a text field comes back with stray JSON characters. */
    private SummaryResponse askSummary(List<AiService.ChangeInput> inputs, String reason) {
        return AiOutputCheck.askWithRetry(() -> aiService.summarize(inputs, reason), AiOutputCheck::problems);
    }

    /**
     * Summarizes a big session in parts of SUMMARY_BATCH_SIZE changes, a few parts at a time,
     * then asks for one session summary (and goal check) from the parts' summaries. If any
     * call fails the whole summary fails, so a session never ends up half described.
     */
    private SummaryResponse summarizeInParts(List<AiService.ChangeInput> inputs, String reason) {
        ExecutorService pool = Executors.newFixedThreadPool(SUMMARY_THREADS);
        List<SummaryResponse> parts = new ArrayList<>();
        try {
            List<CompletableFuture<SummaryResponse>> futures = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i += SUMMARY_BATCH_SIZE) {
                List<AiService.ChangeInput> batch = inputs.subList(i, Math.min(i + SUMMARY_BATCH_SIZE, inputs.size()));
                futures.add(CompletableFuture.supplyAsync(() -> askSummary(batch, reason), pool));
            }
            for (CompletableFuture<SummaryResponse> f : futures) {
                try {
                    parts.add(f.join());
                } catch (CompletionException e) {
                    // Rethrow the part's own error, so an AiUnavailableException keeps its message.
                    if (e.getCause() instanceof RuntimeException cause) throw cause;
                    throw e;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        SummaryResponse combined = AiOutputCheck.askWithRetry(
                () -> aiService.combineSummaries(parts, reason), AiOutputCheck::problems);
        List<SummaryResponse.ChangeDescription> descriptions = new ArrayList<>();
        for (SummaryResponse part : parts) {
            if (part.changeDescriptions() != null) descriptions.addAll(part.changeDescriptions());
        }
        return new SummaryResponse(combined.sessionSummary(), descriptions, combined.goalPartsNotDone());
    }

    /**
     * Puts each AI description on the change it was written for. A description is
     * matched by display_order, and only kept if its file_path agrees with that change,
     * so a confused or shuffled AI reply can't put one file's description on another.
     * Changes with no trustworthy description keep a null description.
     */
    static void applyDescriptions(List<Change> changes, List<SummaryResponse.ChangeDescription> descriptions) {
        if (descriptions == null) return;
        Map<Integer, Change> byOrder = new HashMap<>();
        for (Change c : changes) {
            byOrder.put(c.getDisplayOrder(), c);
        }
        for (SummaryResponse.ChangeDescription d : descriptions) {
            if (d == null || d.description() == null || d.description().isBlank()) continue;
            Change target = byOrder.get(d.displayOrder());
            if (target == null) continue;
            if (d.filePath() != null && !d.filePath().equals(target.getFilePath())) continue;
            target.setDescription(d.description().strip());
            target.setGoalMatch(goalMatch(d.goalMatch()));
            target.setGoalReason(target.getGoalMatch() == null || d.goalReason() == null || d.goalReason().isBlank()
                    ? null : d.goalReason().strip());
        }
    }

    /** "yes" / "partly" / "no" from the AI; anything else counts as not checked. */
    private static Change.GoalMatch goalMatch(String value) {
        if (value == null) return null;
        return switch (value.strip().toLowerCase()) {
            case "yes" -> Change.GoalMatch.YES;
            case "partly" -> Change.GoalMatch.PARTLY;
            case "no" -> Change.GoalMatch.NO;
            default -> null;
        };
    }

    private static String toJson(List<String> items) {
        try {
            return JSON.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** error is null on success, otherwise a short reason safe to show the user. */
    public record RetryResult(Session session, String error) {}

    private Path requireRepoRoot(Path projectDir) throws IOException {
        if (!gitService.isGitRepo(projectDir)) {
            throw new IllegalStateException(
                    "This folder isn't a git repository. Run git init first, then lore start.");
        }
        return gitService.repoRoot(projectDir);
    }

    /** summaryError is null when the AI summary was saved, otherwise a reason safe to show the user. */
    public record StopResult(Session session, int fileCount, int changeCount, boolean discarded,
                             String summaryError) {}
}
