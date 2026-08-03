---
inclusion: manual
---

# Sentinel AI Bridge — CI/CD Reference

## Workflow Overview

GitHub Actions is the only build system. NDK pinned to `r26d`. arm64-v8a only.

## Secrets Required

| Secret | Description |
|--------|-------------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded `.jks` keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

## CMake Build Command (exact flags)

```bash
cmake -S native -B native/build \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-26 \
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF \
  -DLLAMA_CURL=OFF -DGGML_CUDA=OFF -DGGML_METAL=OFF -DGGML_OPENCL=OFF \
  -DGGML_VULKAN=OFF -DGGML_BLAS=OFF -DGGML_RPC=OFF -DGGML_NEON=ON
cmake --build native/build --config Release
```

## CI Steps (every push)

1. Checkout + sync submodules (`submodules: recursive`)
2. JDK 17 (Temurin)
3. Android SDK
4. NDK r26d (pinned — never `latest`)
5. Cache Gradle (`~/.gradle/caches`, `~/.gradle/wrapper`)
6. Cache CMake (`.cxx`, keyed on `CMakeLists.txt` + llama.cpp hash)
7. Build llama.cpp
8. `./gradlew assembleDebug`
9. `./gradlew testDebugUnitTest`
10. `./gradlew lintDebug`
11. `./gradlew detekt`
12. Upload debug APK artifact (retain 14 days)

## Release Steps (on `v*` tag)

Steps 1–6 repeated, then:
7. Build llama.cpp
8. Decode keystore from `ANDROID_KEYSTORE_BASE64`
9. `./gradlew assembleRelease` with signing flags
10. Create GitHub Release with signed APK

## Detekt

- Config: `detekt.yml` in repo root
- Baseline: `detekt-baseline.xml` committed after first run
- CI runs `detekt` (not `detektBaseline`) — fails on new violations only
- `ForbiddenComment` rule active — blocks TODO/FIXME in CI

## Version Format

- `versionName`: `MAJOR.MINOR.PATCH` (matches git tag)
- `versionCode`: `(MAJOR × 10000) + (MINOR × 100) + PATCH`
- Release trigger: `git tag v1.0.0 && git push --tags`

## .gitmodules

```
[submodule "native/third_party/llama.cpp"]
    path = native/third_party/llama.cpp
    url = https://github.com/ggerganov/llama.cpp.git
```

Pin to tested commit SHA. Update intentionally, never track `master` loosely.
