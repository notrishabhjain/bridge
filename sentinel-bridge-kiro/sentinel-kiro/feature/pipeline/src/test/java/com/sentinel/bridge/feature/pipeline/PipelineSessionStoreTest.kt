package com.sentinel.bridge.feature.pipeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PipelineSessionStore")
class PipelineSessionStoreTest {

    private val store = PipelineSessionStore()

    @Test
    @DisplayName("start seeds the transcript and language")
    fun startSeedsState() {
        store.start("s1", "hello there", "Hindi")

        assertEquals("hello there", store.requireTranscript("s1"))
        assertEquals("Hindi", store.get("s1")?.language)
    }

    @Test
    @DisplayName("update preserves fields it does not touch")
    fun updateIsIncremental() {
        store.start("s1", "hello there", "English")
        store.update("s1") { it.copy(renderedPrompt = "a prompt") }
        store.update("s1") { it.copy(rawResponse = "a response") }

        assertEquals("hello there", store.requireTranscript("s1"))
        assertEquals("a prompt", store.requirePrompt("s1"))
        assertEquals("a response", store.requireRawResponse("s1"))
    }

    @Test
    @DisplayName("update creates state for an unknown session rather than dropping the write")
    fun updateCreatesMissingState() {
        store.update("unseeded") { it.copy(rawResponse = "late arrival") }

        assertEquals("late arrival", store.requireRawResponse("unseeded"))
    }

    @Test
    @DisplayName("missing values raise rather than yielding an empty default")
    fun missingValuesRaise() {
        // The bug this guards against is a stage accepting "" and reporting success:
        // an empty prompt or response produces a run that does nothing but looks fine.
        val error = assertThrows(MissingSessionStateException::class.java) {
            store.requireTranscript("nothing-here")
        }
        assertEquals("transcript", error.field)
        assertEquals("nothing-here", error.sessionId)

        assertThrows(MissingSessionStateException::class.java) { store.requirePrompt("nothing-here") }
        assertThrows(MissingSessionStateException::class.java) { store.requireRawResponse("nothing-here") }
        assertThrows(MissingSessionStateException::class.java) { store.requireResult("nothing-here") }
    }

    @Test
    @DisplayName("a blank value counts as missing")
    fun blankCountsAsMissing() {
        store.update("s1") { it.copy(renderedPrompt = "   ") }

        assertThrows(MissingSessionStateException::class.java) { store.requirePrompt("s1") }
    }

    @Test
    @DisplayName("clear releases the session")
    fun clearReleasesSession() {
        store.start("s1", "hello there", "English")
        store.clear("s1")

        assertNull(store.get("s1"))
    }

    @Test
    @DisplayName("sessions do not read each other's state")
    fun sessionsAreIsolated() {
        store.start("s1", "first", "English")
        store.start("s2", "second", "Hindi")

        assertEquals("first", store.requireTranscript("s1"))
        assertEquals("second", store.requireTranscript("s2"))
    }
}
