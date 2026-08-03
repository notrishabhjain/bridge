---
inclusion: fileMatch
fileMatchPattern: ["**/*.kt", "**/*.kts"]
---

# Sentinel AI Bridge — Kotlin Coding Standards

## Threading Rules

```kotlin
// CORRECT
withContext(Dispatchers.IO) { database.query() }
withContext(Dispatchers.Default) { runInference() }
// On main thread only for UI updates — use Dispatchers.Main

// NEVER
GlobalScope.launch { }          // leaks, no lifecycle
Thread { }.start()              // raw thread
Thread.sleep(500)               // blocks, prevents Accessibility events
runBlocking { }                 // blocks caller thread
```

## Coroutine Scope Rules

- `viewModelScope` in ViewModels
- `lifecycleScope` in Activities/Fragments
- Custom `CoroutineScope(Dispatchers.Default + SupervisorJob())` in `@Singleton` classes with explicit `cancel()` in teardown
- Never `GlobalScope`

## Flow / State

```kotlin
// CORRECT
private val _state = MutableStateFlow(InitialState)
val state: StateFlow<State> = _state.asStateFlow()

// NEVER
MutableLiveData()   // LiveData is banned
BehaviorSubject()   // RxJava is banned
```

## Result Handling

```kotlin
// Methods that can fail return Result<T> or throw typed exceptions
suspend fun loadModel(): Result<Unit>           // recoverable
suspend fun openRecording(): Boolean            // simple boolean for retry logic
fun resolve(): RecorderAutomationStrategy       // throws UnsupportedDeviceException
```

## Typed Exceptions (use these — never throw raw Exception)

```kotlin
class UnsupportedDeviceException(message: String) : SentinelException(message)
class StorageException(message: String, cause: Throwable? = null) : SentinelException(message, cause)
class InferenceException(message: String, cause: Throwable? = null) : SentinelException(message, cause)
class AccessibilityException(message: String) : SentinelException(message)
class TranscriptionTimeoutException(sessionId: String) : SentinelException("Transcription timed out: $sessionId")
class JsonValidationException(reason: String) : SentinelException(reason)
```

## Null Safety

- Prefer `?: return false` or `?: throw TypedEx()` over `!!`
- `?.let { }` for nullable chains
- Document why a nullable is expected with a comment if non-obvious

## Immutability

- `val` by default; `var` only when mutation is required and documented
- `data class` with `val` properties for all domain models
- Never mutate `InputContext` after construction

## Hilt Injection

```kotlin
@Singleton             // for stateful services (LlamaCppProvider, CapabilityManager)
@ActivityScoped        // for setup wizard components
// Constructor injection ONLY — never field injection except in Android framework classes

// Android framework classes (Activity, Service, BroadcastReceiver) use field injection:
@Inject lateinit var orchestrator: PipelineOrchestrator
```

## Room

```kotlin
// All DAO methods are suspend fun
@Dao interface PipelineSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: PipelineSessionEntity)
    
    @Query("SELECT * FROM pipeline_sessions WHERE sessionId = :id")
    suspend fun getById(id: String): PipelineSessionEntity?
    
    // Use Flow for reactive queries
    @Query("SELECT * FROM pipeline_sessions ORDER BY createdAt DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<PipelineSessionEntity>>
}
```

## KDoc (mandatory on all public declarations)

```kotlin
/**
 * Resolves the correct [RecorderAutomationStrategy] for the current device.
 *
 * Reads HyperOS version via `getprop` and matches against known strategy implementations.
 *
 * @throws UnsupportedDeviceException if the device is not Xiaomi HyperOS 2.x.
 */
fun resolve(): RecorderAutomationStrategy
```

## No Magic Numbers

```kotlin
// NEVER
delay(180_000L)
if (retryCount > 3)

// ALWAYS
private const val TRANSCRIPTION_TIMEOUT_MS = 180_000L
private const val MAX_RETRIES = 3
```

## Logging

```kotlin
// NEVER
Log.d("Sentinel", "stage started")

// ALWAYS
logger.logStageStart(sessionId = sessionId, stage = PipelineStage.INFERENCE)
logger.logError(sessionId = sessionId, error = SentinelError(...))
```
