package com.sentinel.bridge.feature.ai.validation

/**
 * Thrown when [ResponseParser] cannot extract a valid JSON object from
 * the raw LLM response string.
 *
 * @param message Description of why parsing failed.
 */
class ResponseParseException(message: String) : RuntimeException(message)
