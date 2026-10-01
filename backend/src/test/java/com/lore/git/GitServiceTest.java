package com.lore.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

    private GitService gitService;

    @BeforeEach
    void setUp() {
        gitService = new GitService();
    }

    private void initRepo(Path dir) throws IOException, InterruptedException {
        run(dir, "git", "init");
    }

    private String run(Path dir, String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("Command failed (" + exit + "): " + String.join(" ", cmd)
                    + "\n" + output);
        }
        return output;
    }

    @Test
    void snapshot_creates_ref(@TempDir Path dir) throws Exception {
        initRepo(dir);
        Files.writeString(dir.resolve("hello.txt"), "hello world");

        String sessionId = UUID.randomUUID().toString();
        String ref = gitService.snapshot(dir, sessionId);

        assertEquals("refs/lore/sessions/" + sessionId, ref);
        String showRef = run(dir, "git", "show-ref", ref);
        assertTrue(showRef.contains(ref));
    }

    @Test
    void snapshot_creates_tree_with_files(@TempDir Path dir) throws Exception {
        initRepo(dir);
        Files.writeString(dir.resolve("a.txt"), "aaa");
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("sub/b.txt"), "bbb");

        String sessionId = UUID.randomUUID().toString();
        String ref = gitService.snapshot(dir, sessionId);

        String tree = run(dir, "git", "ls-tree", "-r", "--name-only", ref);
        assertTrue(tree.contains("a.txt"));
        assertTrue(tree.contains("sub/b.txt"));
    }

    @Test
    void snapshot_does_not_modify_working_tree(@TempDir Path dir) throws Exception {
        initRepo(dir);
        Files.writeString(dir.resolve("file.txt"), "original");
        run(dir, "git", "add", "file.txt");
        run(dir, "git", "-c", "user.name=Test", "-c", "user.email=test@test", "commit", "-m", "init");

        Files.writeString(dir.resolve("file.txt"), "modified");

        gitService.snapshot(dir, UUID.randomUUID().toString());

        assertEquals("modified", Files.readString(dir.resolve("file.txt")));
        String status = run(dir, "git", "status", "--porcelain");
        assertTrue(status.contains("file.txt"), "file should still show as modified");
    }

    @Test
    void snapshot_works_with_zero_commits(@TempDir Path dir) throws Exception {
        initRepo(dir);
        Files.writeString(dir.resolve("first.txt"), "content");

        String ref = gitService.snapshot(dir, UUID.randomUUID().toString());

        String tree = run(dir, "git", "ls-tree", "-r", "--name-only", ref);
        assertTrue(tree.contains("first.txt"));
    }

    @Test
    void snapshot_works_without_git_identity(@TempDir Path dir) throws Exception {
        initRepo(dir);
        Files.writeString(dir.resolve("file.txt"), "data");

        assertDoesNotThrow(() -> gitService.snapshot(dir, UUID.randomUUID().toString()));
    }

    @Test
    void isGitRepo_true_for_repo(@TempDir Path dir) throws Exception {
        initRepo(dir);
        assertTrue(gitService.isGitRepo(dir));
    }

    @Test
    void isGitRepo_false_for_non_repo(@TempDir Path dir) throws Exception {
        assertFalse(gitService.isGitRepo(dir));
    }
}
