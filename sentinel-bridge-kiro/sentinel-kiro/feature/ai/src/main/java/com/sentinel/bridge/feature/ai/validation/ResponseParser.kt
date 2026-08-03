package com.sentinel.bridge.feature.ai.validation

import com.sentinel.bridge.core.domain.model.CalendarEvent
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.ExtractedTask
import com.sentinel.bridge.core.domain.model.FollowUp
import com.sentinel.bridge.core.domain.model.PipelineResult
import com.sentinel.bridge.core.domain.model.TaskPriority
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses raw LLM response strings into [PipelineResult] domain objects.
 *
 * The parser handles common LLM output quirks such as markdown code fences
 * wrapping the JSON payload. It extracts the JSON object, parses it with
 * [JSONObject], and maps each field to the corresponding Kotlin domain model.
 *
 * Usage:
 * ```kotlin
 * val parser = ResponseParser()
 * val result: PipelineResult = parser.parse(rawLlmOutput)
 * ```
 *
 * @throws ResponseParseException if no JSON object can be extracted from the response.
 */
@Singleton
class ResponseParser @Inject constructor() {

    /**
     * Parses the raw LLM response into a [PipelineResult].
     *
     * Processing steps:
     * 1. Strips markdown code fences if present (` ```json ... ``` ` or ` ``` ... ``` `).
     * 2. Locates the first JSON object in the remaining text.
     * 3. Parses the JSON string into an [org.json.JSONObject].
     * 4. Maps each JSON field to the [PipelineResult] domain model.
     *
     * @param rawResponse The raw text output from the AI provider.
     * @return A fully-populated [PipelineResult] instance.
     * @throws ResponseParseException if no valid JSON object is found in the response.
     */
    fun parse(rawResponse: String): PipelineResult {
        val jsonString = extractJson(rawResponse)
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw ResponseParseException("Failed to parse JSON: ${e.message}")
        }
        return mapToPipelineResult(json)
    }

    /**
     * Extracts the JSON object string from a raw response that may contain
     * markdown code fences or surrounding prose.
     */
    private fun extractJson(raw: String): String {
        val stripped = stripMarkdownFences(raw)
        val jsonStart = stripped.indexOf('{')
        if (jsonStart == -1) {
            throw ResponseParseException("No JSON object found in response")
        }
        val jsonEnd = findMatchingBrace(stripped, jsonStart)
        if (jsonEnd == -1) {
            throw ResponseParseException("Unmatched opening brace in response JSON")
        }
        return stripped.substring(jsonStart, jsonEnd + 1)
    }

    /**
     * Removes markdown code fences from the response string.
     *
     * Handles patterns:
     * - ` ```json\n...\n``` `
     * - ` ```\n...\n``` `
     */
    private fun stripMarkdownFences(input: String): String {
        val fencePattern = Regex("""```(?:json|JSON)?\s*\n?(.*?)\n?\s*```""", RegexOption.DOT_MATCHES_ALL)
        val match = fencePattern.find(input)
        return match?.groupValues?.get(1)?.trim() ?: input.trim()
    }

    /**
     * Finds the index of the closing brace that matches the opening brace at [startIndex].
     * Respects string literals (double-quoted) so braces inside strings are not counted.
     *
     * @return The index of the matching `}`, or -1 if not found.
     */
    private fun findMatchingBrace(text: String, startIndex: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in startIndex until text.length) {
            val c = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                when (c) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
        }
        return -1
    }

    /**
     * Maps a parsed [JSONObject] to a [PipelineResult] domain object.
     */
    private fun mapToPipelineResult(json: JSONObject): PipelineResult {
        return PipelineResult(
            sessionId = json.optString("sessionId", ""),
            summary = json.optString("summary", ""),
            confidence = json.optDouble("confidence", 0.0).toFloat(),
            tasks = parseTasks(json),
            calendarEvents = parseCalendarEvents(json),
            followUps = parseFollowUps(json),
            people = parsePeople(json),
            projects = parseProjects(json),
            processingTimeMs = json.optLong("processingTimeMs", 0L),
            model = json.optString("model", ""),
            promptVersion = json.optString("promptVersion", ""),
            pipelineVersion = json.optString("pipelineVersion", "")
        )
    }

    /**
     * Parses the "tasks" JSON array into a list of [ExtractedTask].
     */
    private fun parseTasks(json: JSONObject): List<ExtractedTask> {
        val tasksArray = json.optJSONArray("tasks") ?: return emptyList()
        return (0 until tasksArray.length()).map { i ->
            val taskJson = tasksArray.getJSONObject(i)
            ExtractedTask(
                id = taskJson.optString("id", ""),
                title = taskJson.optString("title", ""),
                description = taskJson.optString("description", ""),
                priority = parseTaskPriority(taskJson.optString("priority", "MEDIUM")),
                dueDate = taskJson.optString("dueDate", "").ifEmpty { null },
                confidence = taskJson.optDouble("confidence", 0.0).toFloat(),
                source = parseEventSource(taskJson.optString("source", "CALL"))
            )
        }
    }

    /**
     * Parses the "calendarEvents" JSON array into a list of [CalendarEvent].
     */
    private fun parseCalendarEvents(json: JSONObject): List<CalendarEvent> {
        val eventsArray = json.optJSONArray("calendarEvents") ?: return emptyList()
        return (0 until eventsArray.length()).map { i ->
            val eventJson = eventsArray.getJSONObject(i)
            CalendarEvent(
                id = eventJson.optString("id", ""),
                title = eventJson.optString("title", ""),
                dateTime = eventJson.optString("dateTime", ""),
                description = eventJson.optString("description", "").ifEmpty { null }
            )
        }
    }

    /**
     * Parses the "followUps" JSON array into a list of [FollowUp].
     */
    private fun parseFollowUps(json: JSONObject): List<FollowUp> {
        val followUpsArray = json.optJSONArray("followUps") ?: return emptyList()
        return (0 until followUpsArray.length()).map { i ->
            val followUpJson = followUpsArray.getJSONObject(i)
            FollowUp(
                id = followUpJson.optString("id", ""),
                description = followUpJson.optString("description", ""),
                person = followUpJson.optString("person", "").ifEmpty { null }
            )
        }
    }

    /**
     * Parses the "people" JSON array into a list of strings.
     */
    private fun parsePeople(json: JSONObject): List<String> {
        val peopleArray = json.optJSONArray("people") ?: return emptyList()
        return (0 until peopleArray.length()).map { i ->
            peopleArray.getString(i)
        }
    }

    /**
     * Parses the "projects" JSON array into a list of strings.
     */
    private fun parseProjects(json: JSONObject): List<String> {
        val projectsArray = json.optJSONArray("projects") ?: return emptyList()
        return (0 until projectsArray.length()).map { i ->
            projectsArray.getString(i)
        }
    }

    /**
     * Converts a priority string to [TaskPriority], defaulting to MEDIUM for unknown values.
     */
    private fun parseTaskPriority(value: String): TaskPriority {
        return try {
            TaskPriority.valueOf(value.uppercase())
        } catch (_: IllegalArgumentException) {
            TaskPriority.MEDIUM
        }
    }

    /**
     * Converts a source string to [EventSource], defaulting to CALL for unknown values.
     */
    private fun parseEventSource(value: String): EventSource {
        return try {
            EventSource.valueOf(value.uppercase())
        } catch (_: IllegalArgumentException) {
            EventSource.CALL
        }
    }
}
