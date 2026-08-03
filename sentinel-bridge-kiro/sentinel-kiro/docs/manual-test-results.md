# Manual Test Results — Sentinel AI Bridge

## Test Environment

- **Device:** Redmi Turbo 5
- **OS:** Xiaomi HyperOS 2.x
- **Build variant:** Debug / Release
- **Date:** ____-__-__
- **Tester:** _______________

---

## Task 80: 5-Minute Hindi Call

### Test Steps
1. Configure MacroDroid trigger for call end event
2. Make a 5-minute phone call in Hindi
3. Observe Sentinel pipeline triggered via `START_PIPELINE` intent
4. Verify Xiaomi Recorder opens, transcription completes
5. Verify AI inference runs and produces structured JSON output
6. Verify `PIPELINE_COMPLETE` broadcast received by MacroDroid

### Expected Results
- Pipeline completes within reasonable time (< 60s post-transcription)
- Transcript stored at `getExternalFilesDir("transcripts")/<sessionId>.txt`
- JSON output contains: summary, tasks, confidence > 0.0
- No ANR or crash observed
- Room database shows session in COMPLETE state

### Actual Results

| Criterion | Pass/Fail | Notes |
|-----------|-----------|-------|
| Pipeline triggered | | |
| Recorder opened | | |
| Transcription completed | | |
| Transcript saved | | |
| AI inference completed | | |
| JSON output valid | | |
| PIPELINE_COMPLETE sent | | |
| Room state = COMPLETE | | |
| No crash/ANR | | |

### Observations
_Document any issues, timing observations, or unexpected behavior here._

---

## Task 81: 30-Minute Hindi Call

### Test Steps
1. Configure MacroDroid trigger for call end event
2. Make a 30-minute phone call in Hindi
3. Observe Sentinel pipeline triggered via `START_PIPELINE` intent
4. Verify Xiaomi Recorder opens, transcription completes (may take longer)
5. Verify AI inference runs on longer transcript
6. Verify `PIPELINE_COMPLETE` broadcast received by MacroDroid

### Expected Results
- Pipeline completes (longer transcription time acceptable)
- Transcript stored correctly (larger file)
- AI inference handles longer input without OOM
- JSON output contains: summary, tasks, confidence > 0.0
- No ANR or crash observed
- Room database shows session in COMPLETE state
- Memory usage stays within device limits

### Actual Results

| Criterion | Pass/Fail | Notes |
|-----------|-----------|-------|
| Pipeline triggered | | |
| Recorder opened | | |
| Transcription completed | | |
| Transcript saved (size) | | |
| AI inference completed | | |
| Inference time (seconds) | | |
| JSON output valid | | |
| PIPELINE_COMPLETE sent | | |
| Room state = COMPLETE | | |
| No crash/ANR/OOM | | |
| Peak memory usage (MB) | | |

### Observations
_Document any issues, timing observations, memory pressure, or unexpected behavior here._

---

## Task 82: Process Kill Mid-INFERENCE → WorkManager Resume

### Test Steps
1. Trigger pipeline via MacroDroid (or adb intent)
2. Wait until pipeline reaches INFERENCE stage (monitor via logcat)
3. Force-kill the app process: `adb shell am force-stop com.sentinel.bridge`
4. Wait for WorkManager to re-schedule the worker (~15s to ~10min depending on OS)
5. Verify pipeline resumes from the correct stage

### Expected Results
- WorkManager re-schedules `PipelineWorker` after process death
- Worker reads `PipelineSession.currentStage` from Room
- Pipeline resumes from INFERENCE (or LOAD_MODEL if model was unloaded)
- Pipeline completes successfully after resume
- `PIPELINE_COMPLETE` broadcast sent
- Room database shows session in COMPLETE state
- No duplicate work executed (stages before kill not re-run)

### Actual Results

| Criterion | Pass/Fail | Notes |
|-----------|-----------|-------|
| Process killed during INFERENCE | | |
| WorkManager rescheduled worker | | |
| Resume stage correct | | |
| Pipeline completed after resume | | |
| PIPELINE_COMPLETE sent | | |
| Room state = COMPLETE | | |
| No duplicate stage execution | | |
| Time to resume (seconds) | | |

### Observations
_Document WorkManager retry timing, any stage re-execution, or edge cases here._

---

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Developer | | | |
| QA | | | |
