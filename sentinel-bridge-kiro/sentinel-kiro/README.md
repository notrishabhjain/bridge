# Sentinel AI Bridge

On-device AI call transcript analyzer for Xiaomi HyperOS 2.x devices. Sentinel Bridge integrates with MacroDroid to automatically process phone call recordings via Xiaomi's built-in HyperAI transcription, then runs local LLM inference (llama.cpp) to extract tasks, summaries, calendar events, and follow-ups — entirely offline.

## Architecture

Sentinel uses a **microkernel architecture** with compile-time plugins:

```
MacroDroid ──[Intent]──► PipelineEntryReceiver ──► PipelineOrchestrator
    ──► WorkManager ──► CommandBus ──► [Stage Handlers] ──► MacroDroid
```

### Module Structure

| Module | Purpose |
|--------|---------|
| `app` | Application entry, Hilt setup, DI wiring |
| `core/domain` | Plugin interfaces, data models, contracts |
| `core/data` | Room database, DataStore, repositories |
| `core/common` | Shared utilities, logger, error types |
| `feature/pipeline` | CommandBus, PipelineOrchestrator, all handlers |
| `feature/accessibility` | AccessibilityService, RecorderStrategy |
| `feature/notification` | NotificationListenerService |
| `feature/ai` | LlamaCppProvider, PromptBuilder, JSONValidator |
| `feature/setup` | Setup wizard Compose UI |
| `native` | llama.cpp JNI bridge (CMake) |

### Universal Pipeline

```
IDLE → RECEIVED_INTENT → PIPELINE_CREATED → CAPABILITY_CHECK
→ OPEN_RECORDER → OPEN_RECORDING → CLICK_SHOW_TEXT → SELECT_LANGUAGE
→ WAIT_TRANSCRIPTION → EXTRACT_TRANSCRIPT → PREPROCESS
→ RULES_PRE → BUILD_PROMPT → LOAD_MODEL → INFERENCE
→ PARSE_RESPONSE → VALIDATE_JSON → RULES_POST
→ STORE_RESULT → DISPATCH_ACTION → RETURN_INTENT → COMPLETE
```

Every stage persists to Room before proceeding. WorkManager resumes from the last persisted stage on process death.

---

## Prerequisites

- **Device:** Xiaomi Redmi Turbo 5 (or compatible Xiaomi device with HyperOS 2.x)
- **OS:** Xiaomi HyperOS 2.x (verified via `getprop ro.mi.os.version.name`)
- **Android Studio:** Latest stable (Hedgehog or newer)
- **JDK:** 17 (temurin recommended)
- **Android NDK:** r26d (version `26.1.10909125`)
- **CMake:** 3.22.1+
- **Model file:** Qwen3-4B-Instruct Q4_K_M GGUF (~2.5GB)

---

## Build Instructions

### Clone with submodules

```bash
git clone --recurse-submodules https://github.com/<org>/sentinel-bridge.git
cd sentinel-bridge
```

If you already cloned without submodules:

```bash
git submodule update --init --recursive
```

### Build Debug APK

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Run Unit Tests

```bash
./gradlew test
```

### Run Detekt

```bash
./gradlew detekt
```

To regenerate the baseline after intentional changes:

```bash
./gradlew detektBaseline
```

### Run Lint

```bash
./gradlew lint
```

---

## First-Run Guide

1. **Install APK** on Redmi Turbo 5
2. **Launch Sentinel Bridge** — the setup wizard starts automatically
3. **Grant Accessibility Service** permission (required for Recorder automation)
4. **Grant Notification Listener** permission (required for transcription-complete detection)
5. **Device check** verifies HyperOS 2.x is running
6. **Recorder inspection** confirms Xiaomi Recorder is installed and UI nodes are accessible
7. **Model download** fetches the Qwen3-4B GGUF model to device storage
8. **Checksum verification** validates model integrity (SHA-256)
9. Setup complete — the app is ready to receive pipeline triggers

---

## MacroDroid Macro Setup

### Trigger Configuration

1. Open MacroDroid on your Redmi Turbo 5
2. Create a new macro
3. Add trigger: **Phone → Call Ended** (or your preferred call trigger)
4. Optionally add constraint: minimum call duration > 60 seconds

### Action Configuration

Add action: **Connectivity → Send Intent Broadcast**

| Field | Value |
|-------|-------|
| Action | `com.sentinel.bridge.START_PIPELINE` |
| Target | Broadcast |
| Package | `com.sentinel.bridge` |

**Extras:**

| Key | Type | Value |
|-----|------|-------|
| `source` | String | `CALL` |
| `language` | String | `hi` (for Hindi, or `en` for English) |
| `caller_name` | String | `%caller_name%` (MacroDroid variable) |
| `phone_number` | String | `%phone_number%` (MacroDroid variable) |
| `duration_seconds` | String | `%call_duration%` (MacroDroid variable) |

### Result Handling (Optional)

Add a second macro to receive results:

- Trigger: **Intent Received** → `com.sentinel.bridge.PIPELINE_COMPLETE`
- Action: Show notification or log to file

For failures:

- Trigger: **Intent Received** → `com.sentinel.bridge.PIPELINE_FAILED`
- Read extra `error_code` and `error_message` for diagnostics

---

## Manual Test Checklist

Before each release, run through these tests on a physical Redmi Turbo 5:

- [ ] 5-minute Hindi call → pipeline completes, JSON output valid
- [ ] 30-minute Hindi call → pipeline completes without OOM
- [ ] Process kill mid-INFERENCE → WorkManager resumes, pipeline completes
- [ ] Accessibility permission revoked → `CAPABILITY_MISMATCH` broadcast sent
- [ ] Recorder not installed → capability check fails gracefully
- [ ] Model file deleted → `MODEL_LOADING` error, pipeline fails with correct code
- [ ] Low storage (< 500MB free) → capability check warns

See `docs/manual-test-results.md` for the full test template.

---

## CI/CD

CI runs on GitHub Actions (`.github/workflows/ci.yml`).

### Build Job (every push/PR to main)

1. Checkout with recursive submodules
2. JDK 17 + NDK r26d setup
3. Gradle and CMake caching
4. Build llama.cpp for arm64-v8a
5. Assemble debug APK
6. Run unit tests
7. Run lint
8. Run Detekt
9. Upload debug APK as artifact

### Release Job (on `v*` tags)

1. Decodes signing keystore from repository secrets
2. Assembles signed release APK
3. Creates GitHub Release with APK attached

### Required Secrets

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded release keystore (.jks) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Signing key password |

To encode your keystore:

```bash
base64 -i release-keystore.jks | pbcopy  # macOS
base64 -w 0 release-keystore.jks         # Linux
```

---

## Verification Steps

### Task 86: Clean Clone Verification

Verify CI passes on a clean clone with submodules:

```bash
# Fresh clone
git clone --recurse-submodules <repo-url> sentinel-fresh
cd sentinel-fresh

# Build
./gradlew assembleDebug

# Tests
./gradlew test

# Lint + Detekt
./gradlew lint detekt
```

All steps must pass without manual intervention.

### Task 87: Signed Release APK Verification

1. Build release APK locally or download from GitHub Release
2. Install on Redmi Turbo 5: `adb install app-release.apk`
3. Launch app and complete setup wizard
4. Trigger pipeline via MacroDroid
5. Verify full pipeline execution

### Task 91: Final Detekt Run

```bash
./gradlew detekt
```

Must produce zero violations above the committed baseline (`detekt-baseline.xml`).

### Task 92: Final Lint Run

```bash
./gradlew lint
```

Must produce zero warnings. If new warnings appear, fix them or update the lint baseline.

---

## KDoc Documentation

All public declarations in `core/domain/` and feature module interfaces have KDoc documentation. This includes:

- All five plugin interfaces (`EventProvider`, `AIProvider`, `RuleProvider`, `ActionProvider`, `StorageProvider`)
- All data models (`InputContext`, `PipelineCommand`, `SentinelError`, `InferenceConfig`)
- All public repository and manager classes
- All command handlers

When adding new public APIs, always include KDoc with:
- Brief description
- `@param` for each parameter
- `@return` description
- `@throws` for expected exceptions

---

## Room Schema Export

Room database schemas are exported to `schemas/` at compile time. This enables migration testing:

```kotlin
@Database(
    entities = [...],
    version = 1,
    exportSchema = true
)
```

The schema export directory is configured in `app/build.gradle.kts`:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/../schemas")
}
```

Commit schema files after any database migration.

---

## License

Proprietary. All rights reserved.
