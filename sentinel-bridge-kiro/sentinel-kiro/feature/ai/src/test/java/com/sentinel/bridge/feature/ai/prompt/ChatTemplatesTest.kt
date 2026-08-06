package com.sentinel.bridge.feature.ai.prompt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ChatTemplates")
class ChatTemplatesTest {

    @Test
    @DisplayName("chatml wraps the prompt in a user turn and opens the assistant turn")
    fun chatMlWrapsPrompt() {
        val result = ChatTemplates.apply("Extract the tasks.", ChatTemplates.CHATML)

        assertTrue(result.startsWith("<|im_start|>user\n"), result)
        assertTrue(result.contains("Extract the tasks."), result)
        assertTrue(result.contains("<|im_end|>"), result)
        // The assistant turn must be left open, otherwise the model has nothing to fill.
        assertTrue(result.trimEnd().endsWith("</think>"), result)
    }

    @Test
    @DisplayName("chatml pre-closes the thinking block")
    fun chatMlDisablesThinking() {
        // Qwen3 is a hybrid reasoning model. Left to itself it opens <think> and can
        // spend the whole token budget reasoning before emitting any JSON.
        val result = ChatTemplates.apply("Extract the tasks.", ChatTemplates.CHATML)

        assertTrue(result.contains("<think>"), result)
        assertTrue(result.contains("</think>"), result)
        assertTrue(
            result.indexOf("<think>") < result.indexOf("</think>"),
            "the thinking block must already be closed"
        )
    }

    @Test
    @DisplayName("none passes the prompt through untouched")
    fun noneIsPassThrough() {
        val prompt = "Extract the tasks."

        assertEquals(prompt, ChatTemplates.apply(prompt, ChatTemplates.NONE))
    }

    @Test
    @DisplayName("an unknown format falls back to the default rather than passing through")
    fun unknownFormatFallsBackToDefault() {
        // Falling through to raw text is the failure this class exists to prevent, so an
        // unrecognised name must not silently produce an unwrapped prompt.
        val result = ChatTemplates.apply("Extract the tasks.", "llama2")

        assertTrue(result.startsWith("<|im_start|>user\n"), result)
    }

    @Test
    @DisplayName("format matching is case-insensitive")
    fun formatIsCaseInsensitive() {
        val prompt = "Extract the tasks."

        assertEquals(prompt, ChatTemplates.apply(prompt, "NONE"))
        assertEquals(prompt, ChatTemplates.apply(prompt, "None"))
    }
}
