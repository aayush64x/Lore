package com.lore.cli;

import com.lore.session.Session;
import com.lore.session.SessionService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@Command(name = "start", description = "Start a recording session")
public class StartCommand implements Runnable {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final SessionService sessionService;

    public StartCommand(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public void run() {
        try {
            Path cwd = Path.of(System.getProperty("user.dir"));
            Session session = sessionService.start(cwd);

            String projectName = Path.of(session.getProjectPath()).getFileName().toString();
            String time = TIME_FMT.format(session.getStartedAt());

            System.out.println("Session started in " + projectName + " at " + time
                    + ". Run lore stop when you're done.");
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
