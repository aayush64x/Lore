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
}
