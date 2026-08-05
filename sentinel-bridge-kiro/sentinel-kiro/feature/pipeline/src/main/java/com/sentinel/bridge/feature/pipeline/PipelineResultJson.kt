package com.sentinel.bridge.feature.pipeline

import com.sentinel.bridge.core.domain.model.CalendarEvent
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.ExtractedTask
import com.sentinel.bridge.core.domain.model.FollowUp
import com.sentinel.bridge.core.domain.model.TaskPriority
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Serialises pipeline result collections to JSON.
 *
 * Two consumers need this: Room, which stores each collection as a JSON string
 * column, and the MacroDroid broadcast, which carries the same collections as intent
 * extras. Both go through here so a field added in one place cannot quietly go
 * missing in the other.
 *
 * Uses `org.json`, matching the parsing side, so no serialisation dependency is
 * introduced.
 */
object PipelineResultJson {

    /** Serialises [tasks] to a JSON array string. */
    fun tasks(tasks: List<ExtractedTask>): String = JSONArray().apply {
        tasks.forEach { task ->
            put(
                JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("description", task.description)
                    put("priority", task.priority.name)
                    // JSONObject.put drops a null value, which would silently omit the
                    // key; JSONObject.NULL preserves it as an explicit null.
                    put("dueDate", task.dueDate ?: JSONObject.NULL)
                    put("confidence", task.confidence.toDouble())
                    put("source", task.source.name)
                }
            )
        }
    }.toString()

    /** Serialises [events] to a JSON array string. */
    fun calendarEvents(events: List<CalendarEvent>): String = JSONArray().apply {
        events.forEach { event ->
            put(
                JSONObject().apply {
                    put("id", event.id)
                    put("title", event.title)
                    put("dateTime", event.dateTime)
                    put("description", event.description ?: JSONObject.NULL)
                }
            )
        }
    }.toString()

    /** Serialises [followUps] to a JSON array string. */
    fun followUps(followUps: List<FollowUp>): String = JSONArray().apply {
        followUps.forEach { followUp ->
            put(
                JSONObject().apply {
                    put("id", followUp.id)
                    put("description", followUp.description)
                    put("person", followUp.person ?: JSONObject.NULL)
                }
            )
        }
    }.toString()

    /** Serialises a list of plain strings to a JSON array string. */
    fun strings(values: List<String>): String = JSONArray(values).toString()

    /**
     * Reads tasks back from a string produced by [tasks].
     *
     * @return The tasks, or an empty list if [json] is blank or malformed — a stored
     *         result should never prevent the rest of a screen from rendering.
     */
    fun readTasks(json: String): List<ExtractedTask> = readArray(json) { item ->
        ExtractedTask(
            id = item.optString("id"),
            title = item.optString("title"),
            description = item.optString("description"),
            priority = runCatching {
                TaskPriority.valueOf(item.optString("priority").uppercase())
            }.getOrDefault(TaskPriority.MEDIUM),
            dueDate = item.optString("dueDate").ifEmpty { null },
            confidence = item.optDouble("confidence", 0.0).toFloat(),
            source = runCatching {
                EventSource.valueOf(item.optString("source").uppercase())
            }.getOrDefault(EventSource.CALL)
        )
    }

    /** Reads calendar events back from a string produced by [calendarEvents]. */
    fun readCalendarEvents(json: String): List<CalendarEvent> = readArray(json) { item ->
        CalendarEvent(
            id = item.optString("id"),
            title = item.optString("title"),
            dateTime = item.optString("dateTime"),
            description = item.optString("description").ifEmpty { null }
        )
    }

    /** Reads follow-ups back from a string produced by [followUps]. */
    fun readFollowUps(json: String): List<FollowUp> = readArray(json) { item ->
        FollowUp(
            id = item.optString("id"),
            description = item.optString("description"),
            person = item.optString("person").ifEmpty { null }
        )
    }

    /** Reads a plain string list back from a string produced by [strings]. */
    fun readStrings(json: String): List<String> = try {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    } catch (_: JSONException) {
        emptyList()
    }

    private fun <T> readArray(json: String, map: (JSONObject) -> T): List<T> = try {
        val array = JSONArray(json)
        (0 until array.length()).map { map(array.getJSONObject(it)) }
    } catch (_: JSONException) {
        emptyList()
    }
}
