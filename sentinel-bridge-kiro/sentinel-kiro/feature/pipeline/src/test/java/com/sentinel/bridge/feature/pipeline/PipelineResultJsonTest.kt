package com.sentinel.bridge.feature.pipeline

import com.sentinel.bridge.core.domain.model.CalendarEvent
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.ExtractedTask
import com.sentinel.bridge.core.domain.model.FollowUp
import com.sentinel.bridge.core.domain.model.TaskPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PipelineResultJson")
class PipelineResultJsonTest {

    private fun task(
        id: String = "t1",
        dueDate: String? = "2026-03-14"
    ) = ExtractedTask(
        id = id,
        title = "Call the client",
        description = "Discuss the renewal",
        priority = TaskPriority.HIGH,
        dueDate = dueDate,
        confidence = 0.9f,
        source = EventSource.CALL
    )

    @Test
    @DisplayName("tasks survive a write/read round trip")
    fun tasksRoundTrip() {
        val original = listOf(task(), task(id = "t2", dueDate = null))

        val restored = PipelineResultJson.readTasks(PipelineResultJson.tasks(original))

        assertEquals(2, restored.size)
        assertEquals("t1", restored[0].id)
        assertEquals("Call the client", restored[0].title)
        assertEquals(TaskPriority.HIGH, restored[0].priority)
        assertEquals("2026-03-14", restored[0].dueDate)
        assertEquals(EventSource.CALL, restored[0].source)
        assertNull(restored[1].dueDate)
    }

    @Test
    @DisplayName("a null due date is written as an explicit null, not omitted")
    fun nullDueDateIsExplicit() {
        // JSONObject.put silently drops a null value, which would remove the key
        // entirely and make the field's absence ambiguous to consumers.
        val json = PipelineResultJson.tasks(listOf(task(dueDate = null)))

        assertTrue(json.contains("\"dueDate\":null"), "expected an explicit null, got: $json")
    }

    @Test
    @DisplayName("calendar events survive a round trip")
    fun calendarEventsRoundTrip() {
        val original = listOf(
            CalendarEvent("e1", "Renewal call", "2026-03-14T15:30:00", "with Priya"),
            CalendarEvent("e2", "Standup", "2026-03-15T09:00:00", null)
        )

        val restored = PipelineResultJson.readCalendarEvents(
            PipelineResultJson.calendarEvents(original)
        )

        assertEquals(2, restored.size)
        assertEquals("Renewal call", restored[0].title)
        assertEquals("with Priya", restored[0].description)
        assertNull(restored[1].description)
    }

    @Test
    @DisplayName("follow-ups and string lists survive a round trip")
    fun followUpsAndStringsRoundTrip() {
        val followUps = listOf(FollowUp("f1", "Send the quote", "Priya"), FollowUp("f2", "Chase", null))

        val restoredFollowUps = PipelineResultJson.readFollowUps(
            PipelineResultJson.followUps(followUps)
        )
        assertEquals(2, restoredFollowUps.size)
        assertEquals("Priya", restoredFollowUps[0].person)
        assertNull(restoredFollowUps[1].person)

        val people = listOf("Priya", "Anand")
        assertEquals(people, PipelineResultJson.readStrings(PipelineResultJson.strings(people)))
    }

    @Test
    @DisplayName("empty collections round trip as empty, not null")
    fun emptyCollectionsRoundTrip() {
        assertEquals(emptyList<ExtractedTask>(), PipelineResultJson.readTasks(PipelineResultJson.tasks(emptyList())))
        assertEquals(emptyList<String>(), PipelineResultJson.readStrings(PipelineResultJson.strings(emptyList())))
    }

    @Test
    @DisplayName("malformed stored JSON yields an empty list instead of throwing")
    fun malformedJsonIsTolerated() {
        // A corrupt row must not stop a results screen from rendering the rest.
        assertEquals(emptyList<ExtractedTask>(), PipelineResultJson.readTasks("not json"))
        assertEquals(emptyList<CalendarEvent>(), PipelineResultJson.readCalendarEvents(""))
        assertEquals(emptyList<String>(), PipelineResultJson.readStrings("{"))
    }

    @Test
    @DisplayName("an unknown priority falls back to MEDIUM")
    fun unknownPriorityFallsBack() {
        val json = """[{"id":"t1","title":"x","description":"","priority":"URGENT","confidence":0.5}]"""

        assertEquals(TaskPriority.MEDIUM, PipelineResultJson.readTasks(json).single().priority)
    }
}
