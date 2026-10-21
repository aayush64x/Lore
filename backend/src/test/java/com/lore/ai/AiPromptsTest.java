package com.lore.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiPromptsTest {

    @Test
    void each_diff_sits_under_its_own_change_header() {
        String msg = AiPrompts.summaryUserMessage(List.of(
                new AiService.ChangeInput(0, ".gitignore", "ADDED", "@@ -0,0 +1 @@\n+node_modules/\n", false),
                new AiService.ChangeInput(1, "logo.png", "ADDED", null, true),
                new AiService.ChangeInput(2, "app.js", "ADDED", "@@ -0,0 +1 @@\n+console.log('hi');\n", false)
        ), "add app");

        int gitignore = msg.indexOf("### Change display_order=0 | ADDED | .gitignore");
        int logo = msg.indexOf("### Change display_order=1 | ADDED | logo.png");
        int app = msg.indexOf("### Change display_order=2 | ADDED | app.js");
        assertTrue(gitignore >= 0 && logo > gitignore && app > logo, msg);

        // The .gitignore diff is between its header and the next one, never under app.js.
        int nodeModules = msg.indexOf("+node_modules/");
        assertTrue(nodeModules > gitignore && nodeModules < logo, msg);
        assertTrue(msg.indexOf("[binary file, no diff]") > logo);
        assertTrue(msg.indexOf("+console.log") > app);
    }

    private static UserProfile profile(UserProfile.Level level, UserProfile.Goal goal) {
        return new UserProfile(level, goal);
    }

    @Test
    void every_profile_gets_the_shared_rules_and_its_own_audience() {
        for (UserProfile.Level level : UserProfile.Level.values()) {
            for (UserProfile.Goal goal : UserProfile.Goal.values()) {
                String p = AiPrompts.explainSystemPrompt(profile(level, goal));
                assertTrue(p.contains("\"diff_line_start\""), level + " " + goal);
                assertTrue(p.contains("Only use line numbers from the diff."), level + " " + goal);
                assertTrue(p.contains("Only tag an idea if a line starting with + or - uses it."), level + " " + goal);
                assertTrue(p.contains("only appears in unchanged context lines"), level + " " + goal);
                assertTrue(p.contains("WHO YOU ARE WRITING FOR:\n" + AiPrompts.audience(profile(level, goal))));
            }
        }
    }

    @Test
    void new_to_coding_and_understand_app_talks_about_the_app_in_plain_words() {
        String p = AiPrompts.explainSystemPrompt(profile(UserProfile.Level.NEW_TO_CODING, UserProfile.Goal.UNDERSTAND_APP));
        assertTrue(p.contains("never coded"));
        assertTrue(p.contains("first say what it does for the app"));
        assertTrue(p.contains("at most 3"));
    }

    @Test
    void developer_is_short_and_technical_and_learning_adds_alternatives() {
        String understand = AiPrompts.audience(profile(UserProfile.Level.DEVELOPER, UserProfile.Goal.UNDERSTAND_APP));
        String learn = AiPrompts.audience(profile(UserProfile.Level.DEVELOPER, UserProfile.Goal.LEARN_TO_CODE));
        assertTrue(understand.contains("Be short and technical."));
        assertFalse(understand.contains("better ways"));
        assertTrue(learn.contains("better ways"));
    }

    @Test
    void every_profile_gets_its_own_audience_text() {
        var texts = new java.util.HashSet<String>();
        for (UserProfile.Level level : UserProfile.Level.values()) {
            for (UserProfile.Goal goal : UserProfile.Goal.values()) {
                texts.add(AiPrompts.audience(profile(level, goal)));
            }
        }
        assertEquals(6, texts.size());
    }

    @Test
    void explain_message_has_reason_then_numbered_diff() {
        String msg = AiPrompts.explainUserMessage("app.js", "@@ -1 +1 @@\n+hi", "Add a greeting\n\nMore detail.");
        assertTrue(msg.startsWith("File: app.js\n\n"));
        assertTrue(msg.contains("in their own words:\nAdd a greeting\n\nMore detail.\n\nDiff:\n1: @@ -1 +1 @@\n2: +hi\n"), msg);

        String noReason = AiPrompts.explainUserMessage("app.js", "+hi", "  ");
        assertFalse(noReason.contains("own words"));
        assertTrue(noReason.contains("Diff:\n1: +hi\n"));
    }
}
