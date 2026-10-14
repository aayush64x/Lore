package com.lore.cli;

import com.lore.session.Session;
import com.lore.session.SessionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

import java.nio.file.Path;

@Component
@Command(
        name = "lore",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "Learn from your AI-assisted coding sessions.",
        subcommands = { StartCommand.class, StopCommand.class, UICommand.class, KeyCommand.class }
)
public class LoreCli implements Runnable, CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private final SessionService sessionService;
    private int exitCode;

    public LoreCli(IFactory factory, SessionService sessionService) {
        this.factory = factory;
        this.sessionService = sessionService;
    }

    @Override
    public void run() {
        try {
            Path cwd = Path.of(System.getProperty("user.dir"));
            var active = sessionService.getActive(cwd);
            if (active.isPresent()) {
                Session s = active.get();
                System.out.println("Active session since " + s.getStartedAt());
                System.out.println("Run lore stop when you're done.");
            } else {
                System.out.println("No active session. Run lore start to begin.");
            }
        } catch (Exception e) {
            System.out.println("Lore — Learn from your AI-assisted coding sessions.");
            System.out.println();
            System.out.println("Commands:");
            System.out.println("  lore start   Start a recording session");
            System.out.println("  lore stop    Stop the session and summarize changes");
            System.out.println("  lore ui      Open the web UI");
            System.out.println("  lore key     Set or change your Anthropic API key");
        }
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(this, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
