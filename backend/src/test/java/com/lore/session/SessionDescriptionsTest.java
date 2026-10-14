package com.lore.session;

import com.lore.ai.AiService;
import com.lore.ai.dto.ExplanationResponse;
import com.lore.ai.dto.SummaryResponse;
import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.testutil.TestGit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SessionDescriptionsTest {

    /**
     * Pretends to be the AI: writes "describes <path>" for each change it was given,
     * but returns the list reversed, the way a real model might reorder its answer.
     */
    @TestConfiguration
    static class ReversingAi {
        @Bean
        @Primary
        AiService reversingAiService() {
            return new AiService() {
                @Override
                public SummaryResponse summarize(List<ChangeInput> changes, String reason) {
                    List<SummaryResponse.ChangeDescription> out = new ArrayList<>();
                    for (ChangeInput c : changes) {
                        out.add(new SummaryResponse.ChangeDescription(
                                c.filePath(), c.displayOrder(), "describes " + c.filePath()));
                    }
                    Collections.reverse(out);
                    return new SummaryResponse("Session summary", out);
                }

                @Override
                public ExplanationResponse explain(String filePath, String diffContent) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    @Autowired SessionService sessionService;
    @Autowired ChangeRepository changeRepository;

    @Test
    void each_change_gets_its_own_description(@TempDir Path repo) throws Exception {
        TestGit.git(repo, "init");
        sessionService.start(repo);
        TestGit.writeTwoFiles(repo);

        SessionService.StopResult result = sessionService.stop(repo, "add app");

        assertEquals(Session.SummaryStatus.COMPLETE, result.session().getSummaryStatus());
        List<Change> changes = changeRepository.findBySessionIdOrderByDisplayOrder(result.session().getId());
        assertEquals(2, changes.size());
        for (Change c : changes) {
            assertEquals("describes " + c.getFilePath(), c.getDescription(), "wrong description on " + c.getFilePath());
        }
    }

    @Test
    void ignores_descriptions_whose_path_or_order_does_not_match() {
        Change gitignore = change(".gitignore", 0);
        Change app = change("app.js", 1);

        SessionService.applyDescriptions(List.of(gitignore, app), List.of(
                // Every description claims order 0: only the one for .gitignore may land there.
                new SummaryResponse.ChangeDescription("app.js", 0, "app.js text"),
                new SummaryResponse.ChangeDescription(".gitignore", 0, "gitignore text"),
                new SummaryResponse.ChangeDescription("other.js", 7, "unknown change")));

        assertEquals("gitignore text", gitignore.getDescription());
        assertNull(app.getDescription());
    }

    private static Change change(String path, int order) {
        Change c = new Change(UUID.randomUUID(), null, path, Change.ChangeType.ADDED);
        c.setDisplayOrder(order);
        return c;
    }
}
