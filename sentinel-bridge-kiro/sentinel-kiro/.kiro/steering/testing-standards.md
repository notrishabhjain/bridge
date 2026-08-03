---
inclusion: fileMatch
fileMatchPattern: ["**/*Test.kt", "**/*Spec.kt", "**/test/**/*.kt", "**/androidTest/**/*.kt"]
---

# Sentinel AI Bridge — Testing Standards

## Test Layers

| Layer | Location | Tooling | Runs in CI |
|-------|----------|---------|------------|
| Unit | `src/test/` | JUnit 5, MockK, Turbine | Yes |
| Instrumentation | `src/androidTest/` | AndroidJUnit4, WorkManager TestDriver | Yes |
| Manual | Real device | Release checklist | No |

## Unit Test Structure

```kotlin
class OpenRecorderHandlerTest {

    private val fakeGateway = FakeAccessibilityGateway()
    private val fakeLogger = FakeLogger()
    private val handler = OpenRecorderHandlerImpl(fakeGateway, fakeLogger)

    @Test
    fun `returns success when Recorder window opens`() = runTest {
        fakeGateway.windowOpenResult = true
        val result = handler.handle(OpenRecorder(sessionId = "test-123"))
        assertIs<CommandResult.Success>(result)
    }

    @Test
    fun `retries on first failure then succeeds`() = runTest {
        fakeGateway.windowOpenResults = listOf(false, true)
        val result = handler.handle(OpenRecorder(sessionId = "test-123"))
        assertIs<CommandResult.Success>(result)
        assertEquals(2, fakeGateway.windowOpenCallCount)
    }

    @Test
    fun `fails after max retries exhausted`() = runTest {
        fakeGateway.windowOpenResult = false
        val result = handler.handle(OpenRecorder(sessionId = "test-123"))
        assertIs<CommandResult.Failure>(result)
        assertEquals(ErrorCategory.UI_AUTOMATION, (result as CommandResult.Failure).error.category)
    }
}
```

## Fake Implementations (required for all interfaces)

```kotlin
class FakeAccessibilityGateway : AccessibilityGateway {
    var windowOpenResult: Boolean = true
    var windowOpenResults: List<Boolean> = emptyList()
    var windowOpenCallCount: Int = 0
    var textNodesToReturn: List<TextNode> = emptyList()
    var clickResults: Map<String, Boolean> = emptyMap()

    override suspend fun waitForWindow(packageName: String, timeoutMs: Long): Boolean {
        windowOpenCallCount++
        return if (windowOpenResults.isNotEmpty())
            windowOpenResults.getOrElse(windowOpenCallCount - 1) { false }
        else windowOpenResult
    }
    // ... all methods implemented as controllable stubs
}

class FakeAIProvider : AIProvider {
    var responseToReturn: String = """{"version":"1.0","sessionId":"","summary":"","confidence":0.9,"tasks":[]}"""
    var shouldFail: Boolean = false
    var inferCallCount: Int = 0

    override val id = "fake_ai"
    override val isAvailable = true
    override suspend fun infer(prompt: String, config: InferenceConfig): String {
        inferCallCount++
        if (shouldFail) throw InferenceException("Fake failure")
        return responseToReturn
    }
    override suspend fun loadModel() = Result.success(Unit)
    override suspend fun unloadModel() {}
    override fun cancelInference() {}
    override suspend fun health() = ProviderHealth(true, null, null, inferCallCount)
}
```

## What Must Be Unit Tested

Every `CommandHandler<T>` must have tests covering:
- Happy path
- Each retry step (fail N times, then succeed)
- Exhausted retries → correct `ErrorCategory`
- Correct stage persisted to Room on each transition

`JSONValidator` must cover:
- Valid JSON → `ValidationResult.Valid`
- JSON with markdown fences → repaired
- JSON with trailing commas → repaired
- Completely invalid → `ValidationResult.Invalid`
- Missing required fields → `ValidationResult.Invalid`

`RulesEngine` must cover:
- OTP match → `IGNORE` (pre-AI)
- Low confidence → `REJECT` (post-AI)
- Disabled rule → not evaluated
- Priority ordering (100 before 10)

`PromptRepository` must cover:
- Valid frontmatter parsed correctly
- Missing optional field uses default
- Variable injection works for all tokens

`StrategyResolver` must cover:
- Xiaomi + "HyperOS 2" → `HyperOS2RecorderStrategy`
- Any other combination → `UnsupportedDeviceException`

## Flow Testing with Turbine

```kotlin
@Test
fun `capability state emits correct sequence`() = runTest {
    val manager = CapabilityManager(fakeContext)
    manager.capabilities.test {
        assertEquals(CapabilityState.Checking, awaitItem())
        assertEquals(CapabilityState.Ready, awaitItem())
        cancelAndConsumeRemainingEvents()
    }
}
```

## Instrumentation Test Rules

- Use `@HiltAndroidTest` with `@UninstallModules` to replace real implementations with fakes
- Use Room in-memory database (`Room.inMemoryDatabaseBuilder`)
- Use `WorkManagerTestInitHelper` for synchronous worker execution
- Never depend on real Accessibility permission being granted in CI

## Test Naming Convention

```kotlin
// Format: `backtick description of scenario`
fun `returns failure when accessibility node not found after 3 retries`()
fun `emits pipeline complete intent with correct session id`()
fun `repairs json with markdown fences before validation`()
```

## Definition of Done for Tests

A feature's tests are done when:
- [ ] Happy path tested
- [ ] Every documented failure mode tested
- [ ] Retry behavior tested (fail then succeed)
- [ ] Error categories verified
- [ ] All public interfaces have fake implementations
- [ ] No flaky tests (no arbitrary delays, no timing dependencies)
