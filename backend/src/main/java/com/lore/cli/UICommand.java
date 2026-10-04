package com.lore.cli;

// TODO: Build the "lore ui" command that starts the web server and opens the browser.
//
// WHAT: When the user runs "lore ui", Spring Boot is already running in SERVLET
//   mode (LoreApplication.main handles that). This command just needs to print
//   the URL, try to open the browser, and let the server keep running.
//
// INPUT: None.
//
// OUTPUT: Prints the URL to the console. Opens the browser if possible.
//   The terminal blocks (the web server stays alive) until the user hits Ctrl+C.
//
// STEPS:
//   1. Print "Lore is running at http://127.0.0.1:8080" (or similar).
//   2. Try to open the URL in the default browser using java.awt.Desktop.
//      If Desktop isn't supported, just print a fallback message.
//   3. That's it — Spring's web server is already running, so this command
//      returns and the process stays alive serving requests.
//
// ANNOTATIONS NEEDED:
//   - @Component (Spring finds it)
//   - @Command(name = "ui", description = "...") (Picocli registers it)
//   - implements Runnable (Picocli calls run())
//
// ALSO: Register this command as a subcommand in LoreCli.java —
//   add UICommand.class to the subcommands array on line 20.
//
// READ: Picocli User Guide > Subcommands
//   Also: java.awt.Desktop javadoc — browse(URI) method
//
// HOW TO CHECK: Run "mvn spring-boot:run -Dspring-boot.run.arguments=ui"
//   from the backend directory. It should print the URL, open the browser,
//   and keep running until you hit Ctrl+C.
//
// LOOK AT: StartCommand.java for the annotation pattern.

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.awt.*;
import java.net.URI;

@Component
@Command(name="ui", description = "See your session")
public class UICommand implements Runnable{

    @Override
    public void run() {
        try{
            System.out.println("Lore is running at http://127.0.0.1:8080");
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("http://127.0.0.1:8080"));
            }
            else {
                Runtime.getRuntime().exec(new String[]{"open", "http://127.0.0.1:8080"});
                System.out.println("Open http://127.0.0.1:8080 in your browser.");
            }
        }
        catch (Exception e){
            System.out.println(e);
        }

    }
}
