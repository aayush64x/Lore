package com.lore.cli;

import com.lore.ai.ApiKeyPrompt;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "config", description = "Change your API key, coding level or goal")
public class ConfigCommand implements Runnable {

    private final ApiKeyPrompt apiKeyPrompt;

    public ConfigCommand(ApiKeyPrompt apiKeyPrompt) {
        this.apiKeyPrompt = apiKeyPrompt;
    }

    @Override
    public void run() {
        apiKeyPrompt.configure();
    }
}
