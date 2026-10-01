package com.lore.cli;

import com.lore.session.SessionService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.util.Scanner;

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

            System.out.print("What were you trying to do? ");
            Scanner scanner = new Scanner(System.in);
            String reason = scanner.nextLine().trim();

            if (reason.isEmpty()) {
                System.out.println("(No reason given — the summary will be less useful.)");
            }

            SessionService.StopResult result = sessionService.stop(cwd, reason.isEmpty() ? null : reason);

            if (result.discarded()) {
                System.out.println("No changes since lore start. Nothing to save.");
            } else {
                System.out.println("Session saved: " + (result.session().getReason() != null ? result.session().getReason() : "(no reason)")
                        + ". " + result.fileCount() + " files changed, "
                        + result.changeCount() + " changes to review."
                        + " Run lore ui to study them.");
            }
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
