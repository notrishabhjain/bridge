# Sentinel AI Bridge — Requirements

## Overview

Build a local, AI-powered Android middleware that converts recorded phone call transcripts into structured tasks. The application runs entirely on-device with no internet dependency, integrates with MacroDroid via Android Intents, and uses a local llama.cpp-powered LLM for reasoning.

---

## User Stories

### US-01: MacroDroid Trigger

**As** MacroDroid,  
**I want to** send an explicit Intent to Sentinel Bridge when a call ends,  
**so that** the transcript extraction and AI pipeline starts automatically.

**Acceptance Criteria:**
- WHEN MacroDroid sends `com.sentinel.bridge.START_PIPELINE` with required extras (callTimestamp, callerName, phoneNumber, callDuration, macroInvocationId)
- THEN Bridge enqueues a pipeline via WorkManager (UniqueWork "sentinel_pipeline", KEEP policy)
- AND Bridge responds with either `PIPELINE_COMPLETE` or `PIPELINE_FAILED` — never silently
- AND if a pipeline is already running, the duplicate is dropped (KEEP policy)

### US-02: Recorder Automation

**As** the Bridge,  
**I want to** automatically drive the Xiaomi Recorder UI to produce a Hindi transcript,  
**so that** no manual interaction is needed after a call ends.

**Acceptance Criteria:**
- WHEN the pipeline starts
- THEN Bridge opens Xiaomi Recorder via explicit Intent
- AND navigates to the most recent recording using AccessibilityService
- AND clicks "Show text" to initiate transcription
- AND selects Hindi (configurable language) from the selector
- AND waits for the "Finished transcribing" notification (timeout: 180s)
- AND retries each Accessibility step with exponential backoff before failing
- AND if device is not Xiaomi HyperOS 2.x, fails immediately with UnsupportedDeviceException

### US-03: Transcript Extraction

**As** the Bridge,  
**I want to** extract the transcript text from the Recorder UI,  
**so that** it can be passed to the AI for analysis.

**Acceptance Criteria:**
- WHEN "Finished transcribing" notification is received
- THEN Bridge reads the Accessibility tree of the Recorder window
- AND extracts only transcript paragraph nodes (skipping controls, timestamps, AI summary card, speaker chips)
- AND includes speaker labels if detectable, omits them if not
- AND stores the transcript as UTF-8 text at `getExternalFilesDir("transcripts")/<sessionId>.txt`
- AND OCR is never used as a fallback

### US-04: Pre-AI Rules Filtering

**As** the Bridge,  
**I want to** filter inputs that don't need AI before calling the LLM,  
**so that** OTPs, spam, and promotional messages are handled deterministically.

**Acceptance Criteria:**
- WHEN the transcript is passed to the Rules Engine (Phase 1: Pre-AI)
- THEN rules from `assets/rules/default_rules.json` are evaluated in priority order
- AND if a rule matches with action IGNORE → pipeline sends PIPELINE_SKIPPED to MacroDroid
- AND if a rule matches with action REJECT → pipeline fails with structured error
- AND rules are never hardcoded in Kotlin

### US-05: Local AI Inference

**As** the Bridge,  
**I want to** run inference using a local Qwen3-4B GGUF model via llama.cpp,  
**so that** tasks are extracted without any cloud dependency.

**Acceptance Criteria:**
- WHEN the preprocessed transcript passes Rules Engine Phase 1
- THEN PromptBuilder loads the versioned prompt from `assets/prompts/task_extraction_v1.md`
- AND injects transcript, language, sessionId into the prompt
- AND LlamaCppProvider runs inference with config from prompt frontmatter
- AND output is passed to ResponseParser then JSONValidator
- AND if JSON is invalid, one repair attempt is made
- AND if still invalid, pipeline fails with JSON_VALIDATION error
- AND the model is lazy-loaded and auto-unloaded after 5 minutes idle

### US-06: Structured Output to MacroDroid

**As** MacroDroid,  
**I want to** receive structured JSON via broadcast Intent after pipeline completion,  
**so that** I can create tasks in the user's task app.

**Acceptance Criteria:**
- WHEN the pipeline completes successfully
- THEN Bridge broadcasts `com.sentinel.bridge.PIPELINE_COMPLETE` with: sessionId, status, summary, confidence, processingTimeMs, macroInvocationId
- AND rawTranscript is NOT in the Intent (stored separately, retrievable via GET_TRANSCRIPT)
- WHEN the pipeline fails at any stage
- THEN Bridge broadcasts `com.sentinel.bridge.PIPELINE_FAILED` with: sessionId, status, errorCode, errorStage, retryable, macroInvocationId

### US-07: Pipeline Resilience

**As** a user,  
**I want the** pipeline to survive app process kills,  
**so that** a call is never silently lost.

**Acceptance Criteria:**
- WHEN Android kills the app process mid-pipeline
- THEN WorkManager restarts the PipelineWorker
- AND the worker loads the PipelineSession from Room
- AND resumes from the last persisted stage
- AND no stage executes twice (all stages are idempotent)

### US-08: Capability Discovery

**As** a user,  
**I want to** be guided through permissions setup on first launch,  
**so that** the app is correctly configured before any pipeline runs.

**Acceptance Criteria:**
- WHEN the app is launched for the first time (DataStore `setupComplete = false`)
- THEN SetupWizardActivity runs in order: Accessibility permission, Notification Listener, Recorder app check, Recorder UI inspection, model download, checksum verification
- AND a CapabilityProfile is saved to Room after successful inspection
- WHEN a future Recorder UI change is detected
- THEN CapabilityManager broadcasts `CAPABILITY_MISMATCH` instead of failing silently

### US-09: Model Download and Verification

**As** a user,  
**I want** the GGUF model to be downloaded from GitHub Releases and verified,  
**so that** I know I have a correct, untampered model.

**Acceptance Criteria:**
- WHEN the setup wizard reaches the model step
- THEN the app downloads from the URL in `assets/model_config.json`
- AND verifies SHA-256 checksum against `model_config.json`
- AND stores model at `getExternalFilesDir("models")/`
- AND checksum is re-verified on every `loadModel()` call
- AND model is NEVER bundled in the APK

### US-10: CI Build Pipeline

**As** the developer,  
**I want** every push to produce a verified debug APK via GitHub Actions,  
**so that** there is no local build dependency.

**Acceptance Criteria:**
- WHEN any commit is pushed
- THEN GitHub Actions builds llama.cpp (arm64-v8a only), assembles debug APK, runs unit tests, lint, and Detekt
- AND a signed release APK is created and attached to a GitHub Release when a `v*` tag is pushed
- AND Detekt fails CI only on new violations beyond the committed baseline
