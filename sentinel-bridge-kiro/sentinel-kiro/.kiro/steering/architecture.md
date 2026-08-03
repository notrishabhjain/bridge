---
inclusion: always
---

# Sentinel AI Bridge — Architecture Decisions

## Core Pattern: Microkernel with Compile-Time Plugins

The kernel coordinates the pipeline. Plugins implement interfaces. The kernel never knows about concrete implementations.

**MVP:** All plugins are Hilt-injected modules within the same APK. Dynamic plugin loading is deferred.

## The Five Plugin Interfaces (never modify signatures)

```kotlin
interface EventProvider {
    val sourceType: EventSource
    fun canHandle(intent: Intent): Boolean
    suspend fun buildInputContext(intent: Intent): InputContext
}

interface AIProvider {
    val id: String
    val isAvailable: Boolean
    suspend fun loadModel(): Result<Unit>
    suspend fun unloadModel()
    suspend fun infer(prompt: String, config: InferenceConfig): String
    fun cancelInference()
    suspend fun health(): ProviderHealth
}

interface RuleProvider {
    val version: Int
    fun evaluate(context: InputContext): RuleDecision
    fun postProcess(result: PipelineResult): PipelineResult
}

interface ActionProvider {
    val id: String
    fun canHandle(source: EventSource): Boolean
    suspend fun dispatch(result: PipelineResult, context: InputContext): ActionOutcome
}

interface StorageProvider {
    suspend fun saveTranscript(sessionId: String, content: String): Uri
    suspend fun loadTranscript(sessionId: String): String?
    suspend fun deleteTranscript(sessionId: String)
}
```

## Universal Pipeline (source-agnostic)

```
EventSource → InputContext → Preprocessor → Rules Engine (Pre-AI)
→ PromptBuilder → InferenceEngine → ResponseParser → JSONValidator
→ Rules Engine (Post-AI) → ActionProvider
```

## Command Bus Implementation

```kotlin
sealed class PipelineCommand  // one sealed subclass per pipeline stage
interface CommandHandler<T : PipelineCommand>  // one handler per command

// Dispatch via Channel — never direct calls, never reflection, never EventBus
Channel<PipelineCommand> in dedicated CoroutineScope
```

## IPC: Intents Only

- MacroDroid → Bridge: `com.sentinel.bridge.START_PIPELINE` (explicit Intent)
- Bridge → MacroDroid success: `com.sentinel.bridge.PIPELINE_COMPLETE` (broadcast)
- Bridge → MacroDroid failure: `com.sentinel.bridge.PIPELINE_FAILED` (broadcast)
- MacroDroid retrieves transcript: `com.sentinel.bridge.GET_TRANSCRIPT` (explicit Intent)

**Never:** HTTP server, sockets, ContentProvider for primary IPC, shared files for signaling.

## Storage Locations

| Data | Location |
|------|----------|
| Transcripts | `getExternalFilesDir("transcripts")/<sessionId>.txt` |
| Models | `getExternalFilesDir("models")/` |
| Pipeline state | Room |
| Logs (last 100 sessions) | Room |
| Feature flags | DataStore |
| App settings | DataStore |

**Never pass rawTranscript in Intent** — Binder 1MB limit. Return sessionId only.

## WorkManager

- Work name: `"sentinel_pipeline"` (static constant)
- Policy: `ExistingWorkPolicy.KEEP`
- One pipeline at a time. Second trigger is silently dropped.
- Resume: load `PipelineSession` from Room → continue from `currentStage`

## Accessibility

```kotlin
interface AccessibilityGateway  // production: RealAccessibilityGateway, tests: FakeAccessibilityGateway
interface RecorderAutomationStrategy  // MVP: HyperOS2RecorderStrategy only
```

Node resolution order: text → content description → class hierarchy → relative position → coordinate (last resort, in RecorderConfig only).

**Never:** Thread.sleep(), coordinate hardcoded inline, OCR as fallback.

## HyperOS Version Detection

```kotlin
ProcessBuilder("getprop", "ro.mi.os.version.name").start().inputStream.bufferedReader().readLine()
```

If device is not Xiaomi HyperOS 2.x → throw `UnsupportedDeviceException`. Never guess.

## AI Layer Split

```
PromptBuilder → InferenceEngine (AIProvider) → ResponseParser → JSONValidator
```

InferenceEngine only generates text. ResponseParser converts to Kotlin. JSONValidator enforces schema.

## Prompt Files

Location: `assets/prompts/<name>_v<N>.md`  
Frontmatter: manually parsed (no YAML library). Keys: version, model, temperature, maxTokens, topP, topK, repeatPenalty, schema.

## Rules Files

Location: `assets/rules/default_rules.json`  
Never hardcode rules in Kotlin. Rules are versioned JSON, hot-swappable.

## Logging

One structured JSON line per event via `SentinelLogger`. Never bare `Log.d("tag", "string")`.

```json
{"sessionId":"...","stage":"INFERENCE","durationMs":4200,"status":"SUCCESS","timestamp":"..."}
```

## Error Taxonomy

Every error has: `code`, `category` (UI_AUTOMATION | TRANSCRIPTION | ACCESSIBILITY | NOTIFICATION | MODEL_LOADING | INFERENCE | JSON_VALIDATION | STORAGE | SYSTEM), `stage`, `retryable: Boolean`, `timestamp`, `sessionId`.

## Retry Policy

Exponential backoff: 1s → 2s → 4s → 8s. Max 3 retries per stage (see spec for per-stage limits). `retryable` flag on every error.

## Hard Prohibitions

```
Never: GlobalScope, Thread.sleep(), LiveData, RxJava, reflection, EventBus library
Never: hardcode prompt text in Kotlin
Never: hardcode rules in Kotlin
Never: hardcode Recorder text outside RecorderConfig
Never: pass rawTranscript in MacroDroid intent
Never: INTERNET permission in MVP
Never: MANAGE_EXTERNAL_STORAGE permission
Never: expose JNI outside LlamaCppProvider
Never: bypass JSONValidator
Never: TODO comments
Never: placeholder implementations
```
