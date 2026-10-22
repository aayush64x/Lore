package com.lore.cli;

import com.lore.session.SessionService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Optional;

@Component
@Command(name = "stop", description = "Stop the session and save changes")
public class StopCommand implements Runnable {

    private final SessionService sessionService;

    public StopCommand(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public void run() {
        try {
            Path cwd = Path.of(System.getProperty("user.dir"));

            if (sessionService.getActive(cwd).isEmpty()) {
                System.err.println("No session is running. Run lore start first.");
                return;
            }

            String reason = new ReasonInput(
                    new BufferedReader(new InputStreamReader(System.in)), System.out, editor()).read();

            if (reason == null) {
                System.out.println("(No reason given — the summary will be less useful.)");
            }

            System.out.println("Summarizing changes...");
            SessionService.StopResult result = sessionService.stop(cwd, reason, System.out::println);

            if (result.discarded()) {
                System.out.println("No changes since lore start. Nothing to save.");
            } else {
                String reasonText = result.session().getReason() != null
                        ? ReasonInput.title(result.session().getReason()) : "(no reason)";
                System.out.println("Session saved: " + reasonText
                        + ". " + plural(result.fileCount(), "file") + " changed, "
                        + plural(result.changeCount(), "change") + " to review."
                        + " Run lore ui to study them.");

                if (result.summaryError() != null) {
                    System.out.println("(No AI summary: " + result.summaryError()
                            + " You can retry from the web UI.)");
                }
            }
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /** An editor is only offered when a person is typing in a terminal. */
    private static Optional<ReasonInput.Editor> editor() {
        if (System.console() == null) return Optional.empty();
        return ReasonInput.editorCommand(System.getenv(), System.getProperty("os.name", ""), ReasonInput.onPath("nano"))
                .map(ReasonInput::systemEditor);
    }

    static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
