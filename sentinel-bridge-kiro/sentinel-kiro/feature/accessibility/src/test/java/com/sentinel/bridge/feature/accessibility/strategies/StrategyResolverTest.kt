package com.sentinel.bridge.feature.accessibility.strategies

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [StrategyResolver].
 *
 * Since [StrategyResolver.getHyperOsVersion] uses `ProcessBuilder("getprop", ...)`
 * which cannot run on a development machine, we use MockK's `spyk` to override
 * the `internal` method and verify the detection/routing logic in isolation.
 */
class StrategyResolverTest {

    private val hyperOS2Strategy: HyperOS2RecorderStrategy = mockk(relaxed = true)
    private lateinit var resolver: StrategyResolver

    @BeforeEach
    fun setUp() {
        resolver = spyk(StrategyResolver(hyperOS2Strategy))
    }

    @Nested
    @DisplayName("resolve() returns HyperOS2RecorderStrategy for valid HyperOS 2.x versions")
    inner class ValidHyperOS2Tests {

        @Test
        fun `returns strategy for HyperOS 2_0_1`() {
            every { resolver.getHyperOsVersion() } returns "HyperOS 2.0.1"

            val result = resolver.resolve()

            assertEquals(hyperOS2Strategy, result)
        }

        @Test
        fun `returns strategy for HyperOS 2_1_0`() {
            every { resolver.getHyperOsVersion() } returns "HyperOS 2.1.0"

            val result = resolver.resolve()

            assertEquals(hyperOS2Strategy, result)
        }

        @Test
        fun `returns strategy for uppercase HYPEROS 2_0 - case insensitive`() {
            every { resolver.getHyperOsVersion() } returns "HYPEROS 2.0"

            val result = resolver.resolve()

            assertEquals(hyperOS2Strategy, result)
        }
    }

    @Nested
    @DisplayName("resolve() throws UnsupportedDeviceException for unsupported versions")
    inner class UnsupportedDeviceTests {

        @Test
        fun `throws for HyperOS 1_0`() {
            every { resolver.getHyperOsVersion() } returns "HyperOS 1.0"

            assertThrows(UnsupportedDeviceException::class.java) {
                resolver.resolve()
            }
        }

        @Test
        fun `throws for MIUI 14`() {
            every { resolver.getHyperOsVersion() } returns "MIUI 14"

            assertThrows(UnsupportedDeviceException::class.java) {
                resolver.resolve()
            }
        }

        @Test
        fun `throws when version is null`() {
            every { resolver.getHyperOsVersion() } returns null

            assertThrows(UnsupportedDeviceException::class.java) {
                resolver.resolve()
            }
        }

        @Test
        fun `throws when version is empty string`() {
            every { resolver.getHyperOsVersion() } returns ""

            assertThrows(UnsupportedDeviceException::class.java) {
                resolver.resolve()
            }
        }
    }
}
