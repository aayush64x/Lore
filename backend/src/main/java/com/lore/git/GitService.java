package com.lore.git;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class GitService {

    /**
     * Checks whether the given directory is inside a git repository.
     */
    public boolean isGitRepo(Path dir) throws IOException {
        Process process = new ProcessBuilder("git", "rev-parse", "--is-inside-work-tree")
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        try {
            int exit = process.waitFor();
            return exit == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Returns the root of the git repository containing the given directory.
     */
    public Path repoRoot(Path dir) throws IOException {
        Process process = new ProcessBuilder("git", "rev-parse", "--show-toplevel")
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        try {
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("Not a git repository: " + dir);
            }
            return Path.of(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking git root", e);
        }
    }

    /**
     * Creates a snapshot of all non-ignored files in the repo as a git tree,
     * wrapped in a commit, stored under refs/lore/sessions/{sessionId}.
     *
     * @return the ref path (e.g. "refs/lore/sessions/{sessionId}")
     */
    public String snapshot(Path repoDir, String sessionId) throws IOException {
        Path tempIndex = repoDir.resolve(".git/lore-index-" + sessionId);
        try {
            Map<String, String> indexEnv = Map.of("GIT_INDEX_FILE", tempIndex.toString());

            runGit(repoDir, indexEnv, "git", "add", "--all");

            String treeSha = runGit(repoDir, indexEnv, "git", "write-tree").trim();

            Map<String, String> commitEnv = Map.of(
                    "GIT_AUTHOR_NAME", "Lore",
                    "GIT_AUTHOR_EMAIL", "lore@localhost",
                    "GIT_COMMITTER_NAME", "Lore",
                    "GIT_COMMITTER_EMAIL", "lore@localhost"
            );
            String commitSha = runGit(repoDir, commitEnv, "git", "commit-tree", treeSha, "-m", "lore snapshot").trim();

            String ref = "refs/lore/sessions/" + sessionId;
            runGit(repoDir, Map.of(), "git", "update-ref", ref, commitSha);

            return ref;
        } finally {
            Files.deleteIfExists(tempIndex);
        }
    }

    /**
     * Diffs two refs and returns the raw unified diff patch.
     */
    public String diff(Path repoDir, String startRef, String stopRef) throws IOException {
        return runGit(repoDir, Map.of(), "git", "diff-tree", "-r", "-p", "-M", startRef, stopRef);
    }

    /**
     * Resolves a ref to its commit SHA.
     */
    public String resolveRef(Path repoDir, String ref) throws IOException {
        Process process = new ProcessBuilder("git", "rev-parse", ref)
                .directory(repoDir.toFile())
                .redirectErrorStream(true)
                .start();
        try {
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("Could not resolve ref: " + ref);
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while resolving ref", e);
        }
    }

    /**
     * True if this exact ref (a full name like refs/lore/sessions/...) exists.
     */
    public boolean refExists(Path repoDir, String ref) throws IOException {
        Process process = new ProcessBuilder("git", "show-ref", "--verify", "--quiet", ref)
                .directory(repoDir.toFile())
                .redirectErrorStream(true)
                .start();
        try {
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking ref", e);
        }
    }

    /**
     * Deletes a git ref (for discarding sessions).
     */
    public void deleteRef(Path repoDir, String ref) throws IOException {
        runGit(repoDir, Map.of(), "git", "update-ref", "-d", ref);
    }

    private String runGit(Path dir, Map<String, String> extraEnv, String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(dir.toFile())
                .redirectErrorStream(true);
        pb.environment().putAll(extraEnv);
        Process process = pb.start();
        try {
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("Git command failed (" + exit + "): "
                        + String.join(" ", cmd) + "\n" + output.trim());
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during git command", e);
        }
    }
}
