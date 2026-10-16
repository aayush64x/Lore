package com.lore.cli;

// "lore ui" — Spring's web server is already running (LoreApplication.main
// switches to SERVLET mode for this command). This prints the URL and opens
// the browser. The URL carries ?project=<repo root> so the session list knows
// which project to show; it must match how SessionService stores projectPath.

import com.lore.git.GitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Component
@Command(name = "ui", mixinStandardHelpOptions = true, description = "See your session")
public class UICommand implements Runnable{

    private final GitService gitService;
    private final int port;

    // Declared so picocli accepts and documents it; LoreApplication.main
    // applies it to server.port, which is what this.port reads.
    @Option(names = "--port", paramLabel = "<port>", defaultValue = "8080",
            description = "Port for the web UI (default: ${DEFAULT-VALUE})")
    @SuppressWarnings("unused")
    private int portOption;

    public UICommand(GitService gitService, @Value("${server.port:8080}") int port) {
        this.gitService = gitService;
        this.port = port;
    }

    @Override
    public void run() {
        String url = "http://127.0.0.1:" + port + "/";
        try {
            Path cwd = Path.of(System.getProperty("user.dir"));
            String projectPath = gitService.repoRoot(cwd).toAbsolutePath().toString();
            url += "?project=" + URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("Not inside a git repository, so no project is selected.");
            System.out.println("Run lore ui from inside your project folder to see its sessions.");
        }

        System.out.println("Lore is running at " + url);
        if (!openBrowser(url)) {
            System.out.println("Open " + url + " in your browser.");
        }
    }

    /** Tries Java's built-in browser support, then the system's own command. */
    private static boolean openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Exception ignored) {
            // Fall through to the system command.
        }
        try {
            new ProcessBuilder(browserCommand(System.getProperty("os.name", ""), url))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (Exception e) {
            // Command not installed (e.g. no xdg-open on a server). The caller prints the URL.
            return false;
        }
    }

    /**
     * The command that opens a URL in the default browser on each OS.
     * Windows uses rundll32 rather than "cmd /c start" so characters in the URL
     * are passed through as-is instead of being read by cmd.
     */
    static String[] browserCommand(String osName, String url) {
        String os = osName.toLowerCase();
        if (os.contains("mac")) {
            return new String[]{"open", url};
        }
        if (os.contains("win")) {
            return new String[]{"rundll32", "url.dll,FileProtocolHandler", url};
        }
        return new String[]{"xdg-open", url};
    }
}
