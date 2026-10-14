package com.lore.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** The app starts with the default provider and wires up the Anthropic beans. */
@SpringBootTest(properties = "lore.ai.provider=anthropic")
@ActiveProfiles("test")
class AnthropicProviderWiringTest {

    @Autowired AiService aiService;
    @Autowired ApiKeyPrompt apiKeyPrompt;

    @Test
    void anthropic_is_the_ai_service() {
        assertInstanceOf(AnthropicAiService.class, aiService);
        assertNotNull(apiKeyPrompt);
    }
}
