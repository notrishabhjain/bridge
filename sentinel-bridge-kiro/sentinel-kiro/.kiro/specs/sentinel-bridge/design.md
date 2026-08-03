# Sentinel AI Bridge — Design

## Architecture Overview

Sentinel AI Bridge uses a **microkernel architecture** with compile-time plugins.

The kernel (`PipelineOrchestrator` + `CommandBus`) coordinates the pipeline. Five plugin interfaces define the extension points. For MVP, all plugins are Hilt-injected modules within the same APK.

---

## System Context

```
MacroDroid ──[Intent: START_PIPELINE]──► Sentinel Bridge
Sentinel Bridge ──[Intent: PIPELINE_COMPLETE]──► MacroDroid
```

Xiaomi HyperAI performs the actual speech-to-text transcription inside Xiaomi Recorder. Sentinel only drives the Recorder UI and reads the resulting text nodes.

---

## Universal Pipeline Sequence

```
┌─────────────────────────────────────────────────────────────────┐
│ MacroDroid                                                      │
│   START_PIPELINE Intent                                         │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
                PipelineEntryReceiver
                       │ creates sessionId, validates extras
                       ▼
                PipelineOrchestrator
                       │ checks CapabilityManager
                       │ enqueues UniqueWork("sentinel_pipeline", KEEP)
                       ▼
                PipelineWorker (WorkManager)
                       │ creates PipelineSession in Room
                       ▼
                CommandBus (Channel<PipelineCommand>)
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
    OpenRecorder  ... (each     ReturnIntent
    Handler       command       Handler
                  handler)
                       │
                       ▼
                MacroDroid: PIPELINE_COMPLETE / PIPELINE_FAILED
```

---

## Component Interactions

### Pipeline Orchestration

```
PipelineOrchestrator
  uses: CapabilityManager (pre-flight check)
  uses: WorkManager (enqueue)
  uses: PipelineSessionDao (persist stage)

CommandBus
  holds: Channel<PipelineCommand>
  dispatches to: CommandHandler<T> (one per command, Hilt-injected map)

PipelineWorker
  uses: PipelineOrchestrator.resumePipeline(sessionId)
  reads: PipelineSessionDao.getById(sessionId) for current stage
```

### Accessibility Layer

```
HyperOS2RecorderStrategy
  uses: AccessibilityGateway (interface)
  reads: RecorderConfig (from DataStore)
  
StrategyResolver
  reads: Build.MANUFACTURER + getprop ro.mi.os.version.name
  returns: HyperOS2RecorderStrategy or throws UnsupportedDeviceException

SentinelNotificationListener
  emits: SharedFlow<TranscriptionCompleteEvent>
  WaitForTranscriptionHandler subscribes, applies 180s timeout
```

### AI Layer

```
PromptBuilder
  uses: PromptRepository (load prompt file + parse frontmatter)
  uses: PromptRenderer (inject variables)
  produces: String (prompt) + InferenceConfig

LlamaCppProvider (implements AIProvider)
  uses: NativeBridge (JNI — 5 functions only)
  uses: ModelRepository (path, checksum)
  manages: single model, lazy load, auto-unload after idle timeout

ResponseParser
  input: raw String from LlamaCppProvider
  output: Kotlin domain objects (strips markdown fences first)

JSONValidator
  validates against output schema
  attempts repair (strip fences, trailing commas, escape chars)
  returns: ValidationResult.Valid | Repaired | Invalid

RulesEngine
  Phase 1 (Pre-AI): evaluate on InputContext — may IGNORE or REJECT
  Phase 2 (Post-AI): evaluate on PipelineResult — normalize, reject low confidence
  loads rules from: assets/rules/default_rules.json
```

### Data Layer

```
Room SentinelDatabase
  entities: PipelineSession, LogEntry, CapabilityProfile, PipelineResult
  WAL mode, FK enabled, schema exported to schemas/

FileStorageProvider (implements StorageProvider)
  transcripts: getExternalFilesDir("transcripts")/<sessionId>.txt
  models: getExternalFilesDir("models")/

DataStore
  FeatureFlagsRepository: enableCalls, enableNotifications, enableCloud, etc.
  AppSettingsRepository: preferredLanguage, modelIdleTimeoutMs, recorderPackage, etc.
```

---

## Key Data Structures

### InputContext (universal pipeline contract)

```kotlin
data class InputContext(
    val sessionId: String,
    val source: EventSource,
    val rawContent: String,
    val language: String,
    val timestamp: Instant,
    val conversationId: String?,
    val metadata: Map<String, String>,      // callerName, phoneNumber, duration, etc.
    val attachments: List<InputAttachment>,
    val capabilityProfileVersion: Int,
    val recorderStrategy: String,
    val pipelineVersion: Int
)
```

### PipelineCommand sealed class

```kotlin
sealed class PipelineCommand {
    data class OpenRecorder(val sessionId: String) : PipelineCommand()
    data class OpenRecording(val sessionId: String) : PipelineCommand()
    data class ClickShowText(val sessionId: String) : PipelineCommand()
    data class SelectLanguage(val sessionId: String, val language: String) : PipelineCommand()
    data class WaitForTranscription(val sessionId: String, val timeoutMs: Long) : PipelineCommand()
    data class ExtractTranscript(val sessionId: String) : PipelineCommand()
    data class RunPreprocessor(val sessionId: String) : PipelineCommand()
    data class RunRulesPreAI(val sessionId: String) : PipelineCommand()
    data class BuildPrompt(val sessionId: String) : PipelineCommand()
    data class RunInference(val sessionId: String) : PipelineCommand()
    data class ParseResponse(val sessionId: String) : PipelineCommand()
    data class ValidateJson(val sessionId: String) : PipelineCommand()
    data class RunRulesPostAI(val sessionId: String) : PipelineCommand()
    data class StoreResult(val sessionId: String) : PipelineCommand()
    data class DispatchAction(val sessionId: String) : PipelineCommand()
    data class ReturnIntent(val sessionId: String) : PipelineCommand()
}
```

### JSON Output Schema

```json
{
  "version": "1.0",
  "sessionId": "",
  "summary": "",
  "confidence": 0.95,
  "tasks": [{ "id":"", "title":"", "description":"", "priority":"HIGH|MEDIUM|LOW",
              "dueDate":"", "confidence":0.9, "source":"CALL" }],
  "calendarEvents": [],
  "followUps": [],
  "people": [],
  "projects": [],
  "processingTimeMs": 0,
  "model": "",
  "promptVersion": "",
  "pipelineVersion": ""
}
```

Note: `rawTranscript` is never in this JSON. It lives in `getExternalFilesDir("transcripts")/<sessionId>.txt`.

---

## State Machine

```
IDLE → RECEIVED_INTENT → PIPELINE_CREATED → CAPABILITY_CHECK
→ OPEN_RECORDER → OPEN_RECORDING → CLICK_SHOW_TEXT → SELECT_LANGUAGE
→ WAIT_TRANSCRIPTION → EXTRACT_TRANSCRIPT → PREPROCESS
→ RULES_PRE → BUILD_PROMPT → LOAD_MODEL → INFERENCE
→ PARSE_RESPONSE → VALIDATE_JSON → RULES_POST
→ STORE_RESULT → DISPATCH_ACTION → RETURN_INTENT → COMPLETE
```

Every transition persists `currentStage` to Room before proceeding. If WorkManager restarts the worker, it reads `currentStage` and resumes. Every stage handler is idempotent.

---

## Retry Policy

| Stage | Max Retries | Backoff |
|-------|-------------|---------|
| OPEN_RECORDER | 2 | 1s, 2s |
| OPEN_RECORDING | 3 | 1s, 2s, 4s |
| CLICK_SHOW_TEXT | 3 | 1s, 2s, 4s |
| SELECT_LANGUAGE | 2 | 1s, 2s |
| WAIT_TRANSCRIPTION | 1 | timeout 180s |
| EXTRACT_TRANSCRIPT | 2 | 1s, 2s |
| LOAD_MODEL | 1 | single retry |
| INFERENCE | 1 | single retry |
| JSON_VALIDATION | 1 | repair then fail |

---

## Error Handling Strategy

Every stage failure produces a `SentinelError`:

```kotlin
data class SentinelError(
    val code: String,
    val category: ErrorCategory,     // UI_AUTOMATION | TRANSCRIPTION | ACCESSIBILITY |
                                     // NOTIFICATION | MODEL_LOADING | INFERENCE |
                                     // JSON_VALIDATION | STORAGE | SYSTEM
    val message: String,
    val stage: PipelineStage,
    val retryable: Boolean,
    val timestamp: Instant,
    val sessionId: String
)
```

`SentinelLogger` logs every error to Logcat (structured JSON) and Room (last 100 sessions).

---

## Accessibility Node Resolution

For every UI interaction in HyperOS2RecorderStrategy:

1. Text match (`getText()` equals or contains target)
2. Content description (`getContentDescription()` matches)
3. Class hierarchy traversal
4. Relative node positioning (sibling/parent)
5. Coordinate fallback (stored in `RecorderConfig.fallbackCoordinates`, never inline)

Never use `Thread.sleep()`. Wait only on `AccessibilityEvent` callbacks or timeout Flows.

---

## HyperOS Version Detection

```kotlin
ProcessBuilder("getprop", "ro.mi.os.version.name")
    .start().inputStream.bufferedReader().readLine()?.trim()
```

Returns null on failure → treated as unsupported device.
Result starting with "HyperOS 2" → use `HyperOS2RecorderStrategy`.
Anything else → `UnsupportedDeviceException`.

---

## CapabilityManager

Monitors at startup and continuously:
- Accessibility permission
- Notification Listener permission
- Recorder app installed
- Model file exists + checksum valid
- RAM ≥ 2GB free (configurable)
- Storage ≥ 500MB free (configurable)

On mismatch with stored `CapabilityProfile` → broadcast `CAPABILITY_MISMATCH` instead of failing silently.

---

## Testing Strategy

| Component | Test approach |
|-----------|--------------|
| CommandHandlers | Unit test with FakeAccessibilityGateway, FakeAIProvider |
| PipelineOrchestrator | Unit test state transitions with in-memory Room |
| WorkManager recovery | Instrumentation test with WorkManager TestDriver |
| JSONValidator | Unit test with various malformed inputs |
| RulesEngine | Unit test each rule + phase |
| StrategyResolver | Unit test device detection branches |
| Room DAOs | Instrumentation test with in-memory database |
| Intent contracts | Instrumentation test round-trip |
| AccessibilityService | Manual device test only (permission not automatable) |
