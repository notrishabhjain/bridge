---
inclusion: always
---

# Sentinel AI Bridge — Technology Stack

## Android

| Concern | Choice | Notes |
|---------|--------|-------|
| Language | Kotlin 2.x | No Java |
| Min SDK | 26 (Android 8.0) | Covers all required APIs |
| Target SDK | Latest stable | |
| UI | Jetpack Compose | Setup wizard + status screen only |
| DI | Hilt | Mandatory. No Koin, no service locator |
| Async | Coroutines + StateFlow | No LiveData, no RxJava, no GlobalScope |
| Persistence | Room | WAL mode, FK enabled, schema exported to `schemas/` |
| Background | WorkManager | UniqueWork "sentinel_pipeline", KEEP policy |
| Settings | Jetpack DataStore | Feature flags + app settings |
| Architecture | Clean Architecture + Repository | |

## Native AI Runtime

| Concern | Choice | Notes |
|---------|--------|-------|
| Runtime | llama.cpp | Compiled from source as Git submodule |
| Submodule path | `native/third_party/llama.cpp/` | Upstream only, no forks |
| ABI | arm64-v8a ONLY | No x86, no armeabi-v7a |
| Build system | CMake + Android NDK | GitHub Actions CI only |
| JNI surface | 5 functions only | loadModel, infer, unloadModel, health, cancelInference |
| MVP model | Qwen3-4B-Instruct Q4_K_M GGUF | |

## CMake Build Flags (mandatory)

```cmake
-DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF
-DLLAMA_CURL=OFF -DGGML_CUDA=OFF -DGGML_METAL=OFF -DGGML_OPENCL=OFF
-DGGML_VULKAN=OFF -DGGML_BLAS=OFF -DGGML_RPC=OFF -DGGML_NEON=ON
```

## Testing

| Layer | Tools |
|-------|-------|
| Unit (JVM) | JUnit 5, MockK, Turbine, kotlinx-coroutines-test |
| Instrumentation | AndroidJUnit4, WorkManager TestDriver, Room in-memory |
| Manual | Redmi Turbo 5, HyperOS 2.x — release checklist |

No Robolectric. No YAML library. No EventBus library.

## CI/CD

GitHub Actions only. No local builds. NDK pinned to `r26d`.

## Banned Dependencies

These must never appear in the project:

- RxJava / RxAndroid
- Koin
- EventBus (Otto, Greenrobot, etc.)
- Any YAML parsing library
- OkHttp / Retrofit (MVP has no INTERNET permission)
- Robolectric
- Any llama.cpp wrapper library

## Permissions (MVP)

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28"/>
```

**NOT included in MVP:** `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, `CAMERA`, `RECORD_AUDIO`
