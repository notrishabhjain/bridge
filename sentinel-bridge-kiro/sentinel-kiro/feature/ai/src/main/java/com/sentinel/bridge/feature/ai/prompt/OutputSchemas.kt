package com.sentinel.bridge.feature.ai.prompt

/**
 * Concrete output shapes injected into prompts as the `schema` variable.
 *
 * A prompt template's frontmatter names its schema (for example `task_extraction`);
 * this object holds the actual JSON text for that name. Without it the `{{schema}}`
 * placeholder renders as the bare name, which tells the model nothing about the
 * structure it is expected to produce.
 *
 * The shapes are written as annotated examples rather than formal JSON Schema —
 * small instruct models follow a concrete example far more reliably.
 *
 * Field names must stay in step with
 * `com.sentinel.bridge.feature.ai.validation.ResponseParser`, which reads them.
 */
object OutputSchemas {

    /** Schema name used by `task_extraction_v1.md`. */
    const val TASK_EXTRACTION_NAME = "task_extraction"

    /**
     * Shape for call-transcript task extraction.
     *
     * Only the analytical fields appear here. Provenance fields — session ID,
     * processing time, model, and version identifiers — are filled in from the run
     * itself after parsing and are deliberately not requested from the model.
     */
    val TASK_EXTRACTION = """
        {
          "summary": "one or two sentence summary of the conversation",
          "confidence": 0.0,
          "tasks": [
            {
              "id": "t1",
              "title": "short imperative title",
              "description": "what needs doing and any relevant detail",
              "priority": "HIGH | MEDIUM | LOW",
              "dueDate": "2026-03-14 or 2026-03-14T15:30:00, or null if not stated",
              "confidence": 0.0,
              "source": "CALL"
            }
          ],
          "calendarEvents": [
            {
              "id": "e1",
              "title": "meeting title",
              "dateTime": "2026-03-14T15:30:00",
              "description": "purpose of the meeting, or null"
            }
          ],
          "followUps": [
            {
              "id": "f1",
              "description": "what to follow up on",
              "person": "who with, or null"
            }
          ],
          "people": ["names mentioned"],
          "projects": ["projects or topics referenced"]
        }
    """.trimIndent()

    /**
     * Returns the schema text for [name].
     *
     * @throws IllegalArgumentException if no schema is registered under [name],
     *         rather than silently rendering a prompt with no shape.
     */
    fun forName(name: String): String = when (name) {
        TASK_EXTRACTION_NAME -> TASK_EXTRACTION
        else -> throw IllegalArgumentException(
            "No output schema registered for '$name'. Add it to OutputSchemas."
        )
    }
}
