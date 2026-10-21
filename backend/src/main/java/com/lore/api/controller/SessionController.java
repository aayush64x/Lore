package com.lore.api.controller;

import com.lore.api.dto.ChangeResponse;
import com.lore.api.dto.SessionDetailResponse;
import com.lore.api.dto.SessionResponse;
import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import com.lore.session.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionService sessionService;
    private final SessionRepository sessionRepository;
    private final ChangeRepository changeRepository;

    public SessionController(SessionService sessionService, SessionRepository sessionRepository, ChangeRepository changeRepository){
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.changeRepository = changeRepository;

    }

    @GetMapping("/sessions")
    public List<SessionResponse> getAllSessions(@RequestParam String project){
        List<Session> sessionList = sessionRepository.findByProjectPathAndStatusOrderByStartedAtDesc(project, Session.SessionStatus.COMPLETE);
        List<SessionResponse> sessionResponseList = new ArrayList<>();
        for(Session session : sessionList){
            SessionResponse sessionResponse = new SessionResponse(
                    session.getId(),
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

    private SessionDetailResponse toDetail(Session session) {
        UUID id = session.getId();
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
                        c.getDisplayOrder()
                ))
                .toList();

        return new SessionDetailResponse(
                session.getId(),
                session.getProjectPath(),
                session.getReason(),
                session.getSummary(),
                session.getSummaryStatus().toString(),
                session.getStartedAt(),
                session.getStoppedAt(),
                changeResponses
        );
    }
}
