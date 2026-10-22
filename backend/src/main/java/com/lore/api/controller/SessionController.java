package com.lore.api.controller;

import com.lore.api.dto.ChangeResponse;
import com.lore.api.dto.SessionDetailResponse;
import com.lore.api.dto.SessionResponse;
import com.lore.change.Change;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lore.ai.ApiKeyStore;
import com.lore.ai.UserProfile;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.change.TeachBackAttempt;
import com.lore.change.TeachBackAttemptRepository;
import com.lore.cost.SessionCost;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import com.lore.session.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SessionController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SessionService sessionService;
    private final SessionRepository sessionRepository;
    private final ChangeRepository changeRepository;
    private final ExplanationRepository explanationRepository;
    private final ApiKeyStore settings;
    private final TeachBackAttemptRepository attemptRepository;
    private final SessionCost sessionCost;

    public SessionController(SessionService sessionService, SessionRepository sessionRepository, ChangeRepository changeRepository,
                             ExplanationRepository explanationRepository, ApiKeyStore settings,
                             TeachBackAttemptRepository attemptRepository, SessionCost sessionCost){
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.changeRepository = changeRepository;
        this.explanationRepository = explanationRepository;
        this.settings = settings;
        this.attemptRepository = attemptRepository;
        this.sessionCost = sessionCost;

    }

    /** Stopped sessions, plus the running one (if any), so the page can show it as Running. */
    @GetMapping("/sessions")
    public List<SessionResponse> getAllSessions(@RequestParam String project){
        List<Session> sessionList = sessionRepository.findByProjectPathAndStatusInOrderByStartedAtDesc(
                project, List.of(Session.SessionStatus.ACTIVE, Session.SessionStatus.COMPLETE));
        List<SessionResponse> sessionResponseList = new ArrayList<>();
        for(Session session : sessionList){
            SessionResponse sessionResponse = new SessionResponse(
                    session.getId(),
                    session.getStatus().toString(),
                    session.getReason(),
                    session.getSummary(),
                    session.getSummaryStatus().toString(),
                    session.getStartedAt(),
                    session.getStoppedAt(),
                    changeRepository.countDistinctFilePathBySessionId(session.getId()),
                    changeRepository.countBySessionId(session.getId()),
                    changeRepository.countBySessionIdAndUnderstoodTrue(session.getId())
            );
            sessionResponseList.add(sessionResponse);
        }
        return sessionResponseList;
    }

    @GetMapping("/sessions/{id}")
    public SessionDetailResponse getSessionDetail(@PathVariable UUID id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toDetail(session);
    }

    /**
     * Runs the AI summary again for a stopped session.
     * 200: the updated session (same shape as GET /api/sessions/{id}).
     * 503: {"error": "...", "summaryStatus": "..."} when the AI isn't set up or fails.
     * 404: no such session. 409: session is still running or was discarded.
     */
    @PostMapping("/sessions/{id}/retry-summary")
    public ResponseEntity<?> retrySummary(@PathVariable UUID id) {
        SessionService.RetryResult result;
        try {
            result = sessionService.retrySummary(id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
        if (result.error() != null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", result.error(),
                    "summaryStatus", result.session().getSummaryStatus().toString()));
        }
        return ResponseEntity.ok(toDetail(result.session()));
    }

    /**
     * The session's estimated AI cost, for the page to refresh after an Explain or a grade.
     * {"cost": null} when there's nothing to show. 404: no such session.
     */
    @GetMapping("/sessions/{id}/cost")
    public Map<String, Object> getSessionCost(@PathVariable UUID id) {
        if (!sessionRepository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Map<String, Object> out = new HashMap<>();
        out.put("cost", sessionCost.of(id).orElse(null));
        return out;
    }

    /**
     * Deletes a session and everything saved for it, then its snapshot refs.
     * 204: deleted. 404: no such session. 409: {"error": "..."} when it is still running.
     */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable UUID id) {
        try {
            sessionService.delete(id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.noContent().build();
    }

    private SessionDetailResponse toDetail(Session session) {
        UUID id = session.getId();
        // Saved explanations ride along, so the page shows them without asking the AI again.
        // Failed ones are left out; the page shows the Explain button for those.
        UserProfile profile = settings.profile().orElse(UserProfile.DEFAULT);
        Map<UUID, List<TeachBackAttempt>> attempts = new HashMap<>();
        for (TeachBackAttempt a : attemptRepository.findByChangeSessionIdOrderByCreatedAt(id)) {
            attempts.computeIfAbsent(a.getChange().getId(), k -> new ArrayList<>()).add(a);
        }
        Map<UUID, Explanation> explanations = new HashMap<>();
        for (Explanation e : explanationRepository.findByChangeSessionId(id)) {
            if (e.getStatus() == Explanation.ExplanationStatus.COMPLETE) explanations.put(e.getChange().getId(), e);
        }
        List<ChangeResponse> changeResponses = changeRepository.findBySessionIdOrderByDisplayOrder(id)
                .stream()
                .map(c -> new ChangeResponse(
                        c.getId(),
                        c.getFilePath(),
                        c.getChangeType().toString(),
                        c.getOldFilePath(),
                        c.getOldStart(),
                        c.getOldCount(),
                        c.getNewStart(),
                        c.getNewCount(),
                        c.getDiffContent(),
                        c.getDescription(),
                        c.isBinary(),
                        c.isUnderstood(),
                        c.getDisplayOrder(),
                        explanations.containsKey(c.getId())
                                ? ExplanationView.toMap(explanations.get(c.getId()), profile)
                                : null,
                        c.getGoalMatch() == null ? null : c.getGoalMatch().name().toLowerCase(),
                        c.getGoalReason(),
                        TeachBackView.understoodBy(c),
                        TeachBackView.state(attempts.getOrDefault(c.getId(), List.of()), explanations.get(c.getId()))
                ))
                .toList();

        return new SessionDetailResponse(
                session.getId(),
                session.getStatus().toString(),
                session.getProjectPath(),
                session.getReason(),
                session.getSummary(),
                session.getSummaryStatus().toString(),
                session.getStartedAt(),
                session.getStoppedAt(),
                changeResponses,
                goalGaps(session.getGoalGaps()),
                sessionCost.of(id).orElse(null)
        );
    }

    /** The saved JSON list; null if the goal wasn't checked or the value can't be read. */
    private static List<String> goalGaps(String json) {
        if (json == null) return null;
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
