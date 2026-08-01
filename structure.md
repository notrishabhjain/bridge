---
inclusion: always
---

# Sentinel AI Bridge — Project Structure

## Repository Layout

```
bridge/                             # https://github.com/notrishabhjain/bridge
│
├── .kiro/
│   ├── steering/                   # Kiro steering files (always loaded)
│   └── specs/                      # Kiro feature specs
│
├── app/                            # Android application module
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/sentinel/bridge/
│           └── SentinelApplication.kt
│
├── core/
│   ├── domain/                     # Interfaces, models, use cases — NO Android deps
│   │   ├── model/                  # InputContext, PipelineResult, SentinelError, etc.
│   │   ├── interfaces/             # All 5 plugin interfaces
│   │   └── usecase/
│   ├── data/
│   │   ├── db/                     # Room database, entities, DAOs
│   │   ├── datastore/              # Feature flags, app settings
│   │   └── repository/             # Repository implementations
│   └── common/
│       ├── logging/                # SentinelLogger
│       └── extensions/
│
├── feature/
│   ├── pipeline/                   # PipelineOrchestrator, CommandBus, WorkManager Worker
│   │   └── commands/               # Sealed PipelineCommand + CommandHandler per command
│   ├── accessibility/              # SentinelAccessibilityService, AccessibilityGateway
│   │   └── strategies/             # HyperOS2RecorderStrategy, StrategyResolver
│   ├── notification/               # SentinelNotificationListener
│   ├── ai/
│   │   ├── provider/               # AIProvider interface + LlamaCppProvider
│   │   ├── prompt/                 # PromptRepository, PromptRenderer
│   │   ├── rules/                  # RulesEngine, RuleParser
│   │   └── validation/             # JSONValidator, ResponseParser
│   └── setup/                      # CapabilityManager, SetupWizardActivity
│
├── native/
│   ├── third_party/
│   │   └── llama.cpp/              # Git submodule — DO NOT MODIFY
│   ├── jni/
│   │   ├── NativeBridge.kt         # Kotlin JNI declarations (5 functions only)
│   │   └── native_bridge.cpp       # C++ JNI wrapper
│   └── CMakeLists.txt
│
├── schemas/                        # Room schema exports — committed to Git
│
├── assets/
│   ├── prompts/                    # task_extraction_v1.md, summary_v1.md, etc.
│   ├── rules/                      # default_rules.json, rule_schema.json
│   └── model_config.json           # Model download URL, checksum, version
│
├── .github/
│   └── workflows/
│       └── ci.yml
│
├── gradle/
│   └── libs.versions.toml          # All dependency versions pinned here
│
├── detekt.yml
├── detekt-baseline.xml             # Committed after first run
└── .gitmodules                     # llama.cpp submodule reference
```

## Package Name

`com.sentinel.bridge`

## Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Interfaces | Noun | `AIProvider`, `AccessibilityGateway` |
| Implementations | Prefix + Interface | `LlamaCppProvider`, `RealAccessibilityGateway` |
| Test fakes | Fake + Interface | `FakeAccessibilityGateway`, `FakeAIProvider` |
| Commands | Verb phrase | `OpenRecorder`, `ExtractTranscript`, `RunInference` |
| Handlers | Command name + Handler | `OpenRecorderHandler`, `RunInferenceHandler` |
| Errors | Descriptive + Exception | `UnsupportedDeviceException`, `InferenceException` |

## File Organization Rules

- One class per file
- File name matches class name exactly
- Interfaces in `core/domain/interfaces/` — never in feature modules
- Domain models in `core/domain/model/` — never in feature modules
- No business logic in `app/` module
- `core/domain/` has NO Android dependencies — pure Kotlin only

## Import Order

1. Android/Kotlin stdlib
2. AndroidX
3. Hilt
4. Room / WorkManager / DataStore
5. Project imports (`com.sentinel.bridge.*`)

No wildcard imports. Detekt enforces this.

## KDoc

All public declarations require KDoc. Format:

```kotlin
/**
 * Brief one-line description.
 *
 * @param sessionId Unique identifier for this pipeline run.
 * @return [CommandResult] indicating success or failure with structured error.
 */
```
