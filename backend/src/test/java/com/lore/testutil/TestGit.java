package com.lore.testutil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestGit {

    private TestGit() {}

    public static void git(Path dir, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) throw new IOException("git failed: " + out);
    }

    /** Creates a repo with an empty session-worthy change: .gitignore and app.js. */
    public static void writeTwoFiles(Path repo) throws IOException {
        Files.writeString(repo.resolve(".gitignore"), "node_modules/\n");
        Files.writeString(repo.resolve("app.js"), "console.log('hi');\n");
    }
}
