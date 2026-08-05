package com.sentinel.bridge.feature.ai.prompt

/**
 * Wraps a rendered prompt in the conversation format the target model was tuned on.
 *
 * Instruction-tuned models are trained to answer inside a turn structure and to close
 * their reply with an end-of-turn token. Passing bare text instead leaves the model in
 * plain completion mode: it continues the document rather than answering, and never
 * emits the end-of-generation token the native loop watches for. Generation then runs
 * until it hits the token cap — minutes of wasted compute for output that is usually
 * unusable.
 *
 * Applying the wrapping here rather than in the JNI layer keeps it visible and testable
 * from Kotlin, at the cost of encoding the format in the app rather than reading it from
 * the GGUF. The format is therefore named per prompt template rather than assumed.
 */
object ChatTemplates {

    /** ChatML, used by the Qwen family among others. */
    const val CHATML = "chatml"

    /** No wrapping — the rendered prompt is passed through unchanged. */
    const val NONE = "none"

    /** Format applied when a prompt template does not name one. */
    const val DEFAULT = CHATML

    /**
     * Applies [format] to [prompt].
     *
     * @param prompt Fully rendered instruction text.
     * @param format One of [CHATML] or [NONE]; unknown values fall back to [DEFAULT].
     * @return The prompt wrapped for the model, ready to tokenize.
     */
    fun apply(prompt: String, format: String): String = when (format.lowercase()) {
        NONE -> prompt
        else -> chatMl(prompt)
    }

    /**
     * Wraps [prompt] as a single ChatML user turn and opens the assistant turn.
     *
     * The assistant turn is opened with an already-closed thinking block. Qwen3 is a
     * hybrid reasoning model that otherwise opens `<think>` and can spend the entire
     * token budget reasoning before producing any JSON. Pre-closing it is how the
     * official chat template disables thinking, and is deterministic — unlike asking
     * the model in prose not to think.
     */
    private fun chatMl(prompt: String): String = buildString {
        append("<|im_start|>user\n")
        append(prompt)
        append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
        append("<think>\n\n</think>\n\n")
    }
}
