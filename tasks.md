# Sentinel AI Bridge — Implementation Tasks

## Stage 1: Project Skeleton

- [ ] 1. Initialize Android project with package `com.sentinel.bridge`, minSdk 26, Kotlin 2.x
- [ ] 2. Configure Gradle multi-module structure: `app`, `core/domain`, `core/data`, `core/common`, `feature/pipeline`, `feature/accessibility`, `feature/notification`, `feature/ai`, `feature/setup`, `native`
- [ ] 3. Add `libs.versions.toml` with all dependency versions pinned (Hilt, Room, WorkManager, DataStore, Coroutines, Compose, JUnit5, MockK, Turbine, Detekt)
- [ ] 4. Configure `native/CMakeLists.txt` with llama.cpp submodule reference and all required build flags disabled
- [ ] 5. Add `.gitmodules` pointing to `https://github.com/ggerganov/llama.cpp.git` at `native/third_party/llama.cpp/`
- [ ] 6. Add `detekt.yml` with ForbiddenComment (TODO/FIXME), NoWildcardImports, GlobalCoroutineUsage rules active
- [ ] 7. Create empty Hilt `@HiltAndroidApp SentinelApplication`
- [ ] 8. Create all five plugin interfaces in `core/domain/interfaces/` with exact signatures from architecture steering file
- [ ] 9. Create `InputContext` data class and `EventSource` enum in `core/domain/model/`
- [ ] 10. Create `PipelineCommand` sealed class with all subclasses in `feature/pipeline/commands/`
- [ ] 11. Create `SentinelError` data class and `ErrorCategory` enum in `core/domain/model/`
- [ ] 12. Create `InferenceConfig` data class in `core/domain/model/`

## Stage 2: Infrastructure

- [ ] 13. Implement Room database: `PipelineSessionEntity`, `LogEntryEntity`, `CapabilityProfileEntity`, `PipelineResultEntity` with WAL mode, FK enabled, schema export to `schemas/`
- [ ] 14. Implement all Room DAOs with suspend functions and Flow for reactive queries
- [ ] 15. Implement `LogRepository.insertWithRotation()` — deletes sessions beyond 100 after every insert
- [ ] 16. Implement DataStore feature flags with all keys and defaults from architecture doc
- [ ] 17. Implement DataStore app settings with all keys and defaults from architecture doc
- [ ] 18. Implement `SentinelLogger` — structured JSON to Logcat (`Log.d`) + Room via `LogRepository`
- [ ] 19. Implement `FileStorageProvider` — transcript save/load/delete at `getExternalFilesDir("transcripts")`
- [ ] 20. Implement `CapabilityManager` — all capability checks as `StateFlow<CapabilityState>`, startup check returns `CapabilityReport`
- [ ] 21. Implement `SentinelNotificationListener` with `SharedFlow<TranscriptionCompleteEvent>` and configurable notification text matching
- [ ] 22. Create `FakeAccessibilityGateway` implementing `AccessibilityGateway` with controllable stubs for all methods
- [ ] 23. Create `FakeAIProvider` implementing `AIProvider` with configurable response, failure, and call count
- [ ] 24. Write unit tests for `LogRepository` rotation (exactly 100 sessions retained)
- [ ] 25. Write unit tests for `CapabilityManager` (each capability check branch)
- [ ] 26. Write instrumentation tests for all Room DAOs (insert, query, update, cascade delete, migration)

## Stage 3: Pipeline Orchestration

- [ ] 27. Implement `CommandHandler<T>` interface and base retry logic with exponential backoff (1s, 2s, 4s, 8s)
- [ ] 28. Implement `CommandBus` — `Channel<PipelineCommand>`, dedicated `CoroutineScope`, dispatch to handler map (Hilt multibinding)
- [ ] 29. Implement `PipelineOrchestrator` — full state machine, CapabilityManager check, WorkManager enqueue, stage persistence
- [ ] 30. Implement `PipelineWorker` (WorkManager `CoroutineWorker`) — loads session from Room, calls `orchestrator.resumePipeline()`
- [ ] 31. Implement `PipelineEntryReceiver` (BroadcastReceiver) — parses START_PIPELINE intent, validates extras, triggers orchestrator
- [ ] 32. Implement `MacroDroidActionProvider` — broadcasts PIPELINE_COMPLETE and PIPELINE_FAILED intents with correct extras
- [ ] 33. Implement stub `CommandHandler` for each `PipelineCommand` subclass (no business logic yet — log + persist stage + return success)
- [ ] 34. Write unit tests for `PipelineOrchestrator` state transitions (valid + invalid transitions)
- [ ] 35. Write unit tests for each stub `CommandHandler` (retry behavior, error categories, stage persistence)
- [ ] 36. Write instrumentation tests for WorkManager pipeline (enqueue → complete → Room state correct)
- [ ] 37. Write instrumentation tests for WorkManager recovery (kill mid-stage → restart → resume from correct stage)
- [ ] 38. Write instrumentation tests for Intent contracts (PIPELINE_COMPLETE and PIPELINE_FAILED extras correct)

## Stage 4: llama.cpp JNI

- [ ] 39. Write `native/jni/native_bridge.cpp` — JNI wrapper exposing exactly 5 functions: `loadModel`, `infer`, `unloadModel`, `health`, `cancelInference`
- [ ] 40. Implement cancellation via `std::atomic<bool>` flag checked in token generation callback
- [ ] 41. Write `NativeBridge.kt` with `external` declarations for all 5 JNI functions and `System.loadLibrary("sentinel_native")`
- [ ] 42. Implement `ModelRepository` — checksum verification (SHA-256), path resolution, config loading from `assets/model_config.json`
- [ ] 43. Implement `LlamaCppProvider` — lazy model load, auto-unload after configurable idle timeout, cancellation via `AtomicBoolean`, all `AIProvider` methods
- [ ] 44. Add `assets/model_config.json` with model name, download URL placeholder, checksum placeholder, version, contextSize, threads
- [ ] 45. Write unit tests for `LlamaCppProvider` using a fake `NativeBridge` (load, infer, unload, cancel, health, checksum failure)
- [ ] 46. Write unit tests for `ModelRepository` (checksum match, checksum mismatch, file not found)

## Stage 5: Recorder Automation

- [ ] 47. Implement `SentinelAccessibilityService` extending `AccessibilityService` with `RealAccessibilityGateway`
- [ ] 48. Implement `RealAccessibilityGateway` — all `AccessibilityGateway` methods using `AccessibilityService` node APIs, timeout Flows (no Thread.sleep)
- [ ] 49. Implement `HyperOS2RecorderStrategy` — full automation sequence (openLatestRecording, clickShowText, selectLanguage, extractTranscriptNodes) using node resolution priority order
- [ ] 50. Implement `StrategyResolver` — reads `getprop ro.mi.os.version.name`, matches Xiaomi HyperOS 2.x, throws `UnsupportedDeviceException` otherwise
- [ ] 51. Implement `RecorderConfig` loaded from DataStore with all configurable values (recorderPackage, preferredLanguage, completionNotificationText, timeouts, fallbackCoordinates)
- [ ] 52. Wire `WaitForTranscriptionHandler` to subscribe to `SentinelNotificationListener.transcriptionComplete` Flow with 180s timeout and single retry
- [ ] 53. Implement `CapabilityManager.recordCapabilityProfile()` — opens Recorder, probes for expected nodes, saves `CapabilityProfileEntity` to Room
- [ ] 54. Implement `CapabilityManager.validateCapabilityProfile()` — compares current Recorder nodes to stored profile, returns `ProfileMatchResult`
- [ ] 55. Write unit tests for `HyperOS2RecorderStrategy` using `FakeAccessibilityGateway` (each step: happy path, retry, exhausted retries)
- [ ] 56. Write unit tests for `StrategyResolver` (Xiaomi HyperOS 2 → correct strategy, other combinations → exception)
- [ ] 57. Write unit tests for `WaitForTranscriptionHandler` (notification arrives in time, timeout, retry then timeout)

## Stage 6: AI Inference

- [ ] 58. Implement `PromptRepository` — loads from `assets/prompts/`, manual YAML frontmatter parser (no library), caches parsed templates
- [ ] 59. Implement `PromptRenderer` — injects all variables (transcript, language, sessionId, conversationMemory, userPreferences, schema) into prompt body
- [ ] 60. Add `assets/prompts/task_extraction_v1.md` with YAML frontmatter and task extraction prompt body
- [ ] 61. Add `assets/prompts/summary_v1.md` with YAML frontmatter and summarization prompt body
- [ ] 62. Implement `RuleParser` — loads and parses `assets/rules/default_rules.json` (lazy, cached)
- [ ] 63. Implement `RulesEngine` — evaluates Pre-AI and Post-AI passes in priority order, returns `RuleDecision`
- [ ] 64. Add `assets/rules/default_rules.json` with MVP rules: ignore_otp, ignore_banking_otp, ignore_promotional, reject_low_confidence, flag_medium_confidence, filter_low_confidence_tasks
- [ ] 65. Add `assets/rules/rule_schema.json` for rule file validation
- [ ] 66. Implement `ResponseParser` — strips markdown fences, extracts JSON object, maps to Kotlin domain objects
- [ ] 67. Implement `JSONValidator` — schema validation, repair pipeline (strip fences → trailing commas → escape chars), returns `ValidationResult`
- [ ] 68. Wire all AI handlers in `CommandBus`: `BuildPromptHandler`, `RunInferenceHandler`, `ParseResponseHandler`, `ValidateJsonHandler`, `RunRulesPreAIHandler`, `RunRulesPostAIHandler`
- [ ] 69. Write unit tests for `PromptRepository` (frontmatter parsing, missing fields, variable injection)
- [ ] 70. Write unit tests for `RulesEngine` (each rule type, phase ordering, priority, disabled rules)
- [ ] 71. Write unit tests for `JSONValidator` (valid, fences, trailing commas, invalid, schema missing field)
- [ ] 72. Write integration test for full AI pipeline using `FakeAIProvider` (prompt built → fake LLM response → validated JSON)

## Stage 7: Setup Wizard and Capability Discovery

- [ ] 73. Implement `SetupWizardActivity` with Compose UI — sequenced steps: Accessibility, Notification Listener, device check, Recorder inspection, model download, checksum verify
- [ ] 74. Implement model download with progress in the setup wizard (HTTP download using `DownloadManager` — this is the ONLY place network is used, behind user action in setup)
- [ ] 75. On successful setup completion, set `setupComplete = true` in DataStore and record `CapabilityProfile` to Room
- [ ] 76. On subsequent launches, `CapabilityManager.validateCapabilityProfile()` runs; mismatch broadcasts `CAPABILITY_MISMATCH`

## Stage 8: End-to-End Integration

- [ ] 77. Wire all Hilt modules: bind interfaces to implementations, provide Room, WorkManager, DataStore instances
- [ ] 78. Write instrumentation test for full pipeline end-to-end with all fakes (Intent in → stages execute → PIPELINE_COMPLETE Intent out → Room state = COMPLETE)
- [ ] 79. Write instrumentation test for pipeline failure path (FakeAIProvider.shouldFail = true → PIPELINE_FAILED Intent with correct error code)
- [ ] 80. Manual device test on Redmi Turbo 5: 5-minute Hindi call (document result in PR)
- [ ] 81. Manual device test on Redmi Turbo 5: 30-minute Hindi call (document result in PR)
- [ ] 82. Manual device test: process kill mid-INFERENCE → WorkManager resume (document result in PR)

## Stage 9: CI/CD

- [ ] 83. Write `.github/workflows/ci.yml` — full workflow: checkout+submodules, JDK 17, NDK r26d, Gradle cache, CMake cache, build llama.cpp, assemble debug, unit tests, lint, Detekt, upload artifact
- [ ] 84. Add release job to ci.yml — triggers on `v*` tags, decodes keystore from secrets, assembles release APK, creates GitHub Release
- [ ] 85. Run `./gradlew detektBaseline` locally, commit `detekt-baseline.xml`
- [ ] 86. Verify CI passes on clean clone with submodule (`git clone --recurse-submodules`)
- [ ] 87. Verify signed release APK installs and runs on Redmi Turbo 5

## Stage 10: Documentation Polish

- [ ] 88. Verify KDoc present on all public declarations in `core/domain/` and all feature module interfaces
- [ ] 89. Write `README.md` — setup instructions, first-run guide, MacroDroid macro setup, manual test checklist
- [ ] 90. Export Room schema for version 1 to `schemas/` and commit
- [ ] 91. Final Detekt run — ensure no new violations above baseline
- [ ] 92. Final lint run — zero warnings
