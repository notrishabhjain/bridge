cmake_minimum_required(VERSION 3.22.1)

project(sentinel_native)

# Disable all unnecessary llama.cpp build targets
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

# Disable GPU/accelerator backends not available on Android ARM64
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)

# Enable ARM NEON for arm64-v8a
set(GGML_NEON ON CACHE BOOL "" FORCE)

# Add llama.cpp submodule
add_subdirectory(third_party/llama.cpp)

# Sentinel JNI bridge shared library
add_library(sentinel_native SHARED
    jni/native_bridge.cpp
)

# Link against llama.cpp targets and Android log
target_link_libraries(sentinel_native
    llama
    ggml
    log
)
