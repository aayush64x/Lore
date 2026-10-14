package com.lore.cli;

import com.lore.ai.ApiKeyPrompt;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "key", description = "Set or change your Anthropic API key")
public class KeyCommand implements Runnable {

    private final ApiKeyPrompt apiKeyPrompt;

    public KeyCommand(ApiKeyPrompt apiKeyPrompt) {
        this.apiKeyPrompt = apiKeyPrompt;
    }

    @Override
    public void run() {
        apiKeyPrompt.changeKey();
    }
}
