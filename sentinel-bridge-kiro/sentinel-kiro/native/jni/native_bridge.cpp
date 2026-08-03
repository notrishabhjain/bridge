#include <jni.h>
#include <string>
#include <atomic>
#include <android/log.h>

#include "llama.h"
#include "common.h"

#define TAG "SentinelNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Single model instance — only one model loaded at a time
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static std::atomic<bool> g_cancel_flag{false};

// Helper: throw a Java exception from native code
static void throw_java_exception(JNIEnv *env, const char *class_name, const char *message) {
    jclass cls = env->FindClass(class_name);
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}

extern "C" {

/**
 * loadModel(modelPath: String, contextSize: Int, threads: Int): Boolean
 *
 * Loads a GGUF model from the given path with specified context size and thread count.
 * Returns true on success, false on failure.
 */
JNIEXPORT jboolean JNICALL
Java_com_sentinel_bridge_native_1_NativeBridge_loadModel(
        JNIEnv *env,
        jobject /* this */,
        jstring model_path,
        jint context_size,
        jint threads) {

    if (model_path == nullptr) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "modelPath must not be null");
        return JNI_FALSE;
    }

    const char *path_cstr = env->GetStringUTFChars(model_path, nullptr);
    if (path_cstr == nullptr) {
        LOGE("Failed to get model path string from JNI");
        return JNI_FALSE;
    }

    LOGI("Loading model from: %s (contextSize=%d, threads=%d)", path_cstr, context_size, threads);

    // Unload any previously loaded model
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    // Configure model parameters
    llama_model_params model_params = llama_model_default_params();

    g_model = llama_model_load_from_file(path_cstr, model_params);
    env->ReleaseStringUTFChars(model_path, path_cstr);

    if (g_model == nullptr) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    // Configure context parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(context_size);
    ctx_params.n_threads = static_cast<int32_t>(threads);
    ctx_params.n_threads_batch = static_cast<int32_t>(threads);

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        LOGE("Failed to create context from model");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // Reset cancellation flag on successful load
    g_cancel_flag.store(false);

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

/**
 * infer(prompt: String, maxTokens: Int, temperature: Float, topP: Float, topK: Int, repeatPenalty: Float): String
 *
 * Runs text generation with the loaded model using the given sampling parameters.
 * Returns the generated text. Checks cancellation flag during token generation.
 */
JNIEXPORT jstring JNICALL
Java_com_sentinel_bridge_native_1_NativeBridge_infer(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt,
        jint max_tokens,
        jfloat temperature,
        jfloat top_p,
        jint top_k,
        jfloat repeat_penalty) {

    if (g_model == nullptr || g_ctx == nullptr) {
        throw_java_exception(env, "java/lang/IllegalStateException", "Model not loaded. Call loadModel() first.");
        return nullptr;
    }

    if (prompt == nullptr) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "prompt must not be null");
        return nullptr;
    }

    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    if (prompt_cstr == nullptr) {
        LOGE("Failed to get prompt string from JNI");
        return nullptr;
    }

    LOGI("Starting inference (maxTokens=%d, temp=%.2f, topP=%.2f, topK=%d, repeatPenalty=%.2f)",
         max_tokens, temperature, top_p, top_k, repeat_penalty);

    // Reset cancellation flag before inference
    g_cancel_flag.store(false);

    // Tokenize the prompt
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    const int n_prompt_max = llama_n_ctx(g_ctx);
    std::vector<llama_token> tokens(n_prompt_max);

    const int n_prompt_tokens = llama_tokenize(
            vocab,
            prompt_cstr,
            static_cast<int32_t>(strlen(prompt_cstr)),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,   // add_special (BOS)
            true    // parse_special
    );

    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    if (n_prompt_tokens < 0) {
        LOGE("Tokenization failed (returned %d)", n_prompt_tokens);
        throw_java_exception(env, "java/lang/RuntimeException", "Failed to tokenize prompt");
        return nullptr;
    }

    tokens.resize(n_prompt_tokens);

    LOGI("Prompt tokenized: %d tokens", n_prompt_tokens);

    // Clear the KV cache before new inference
    llama_kv_cache_clear(g_ctx);

    // Process prompt tokens in a single batch
    llama_batch batch = llama_batch_init(n_prompt_tokens, 0, 1);

    for (int i = 0; i < n_prompt_tokens; i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    // Set logits for the last token
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Failed to decode prompt batch");
        llama_batch_free(batch);
        throw_java_exception(env, "java/lang/RuntimeException", "Failed to process prompt");
        return nullptr;
    }

    llama_batch_free(batch);

    // Set up the sampler chain with the specified parameters
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());

    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
            static_cast<int32_t>(n_prompt_max),  // penalty_last_n
            repeat_penalty,                       // penalty_repeat
            0.0f,                                 // penalty_freq
            0.0f                                  // penalty_present
    ));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // Generate tokens
    std::string result;
    int n_cur = n_prompt_tokens;

    for (int i = 0; i < max_tokens; i++) {
        // Check cancellation flag
        if (g_cancel_flag.load()) {
            LOGI("Inference cancelled at token %d", i);
            break;
        }

        // Sample the next token
        llama_token new_token = llama_sampler_sample(sampler, g_ctx, -1);

        // Check for end of generation
        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGI("End of generation at token %d", i);
            break;
        }

        // Convert token to text
        char token_text[256];
        int token_len = llama_token_to_piece(vocab, new_token, token_text, sizeof(token_text), 0, true);
        if (token_len > 0) {
            result.append(token_text, token_len);
        }

        // Prepare batch for next token
        llama_batch next_batch = llama_batch_init(1, 0, 1);
        llama_batch_add(next_batch, new_token, n_cur, {0}, true);

        if (llama_decode(g_ctx, next_batch) != 0) {
            LOGE("Failed to decode token at position %d", n_cur);
            llama_batch_free(next_batch);
            break;
        }

        llama_batch_free(next_batch);
        n_cur++;
    }

    llama_sampler_free(sampler);

    LOGI("Inference complete: generated %d characters", (int) result.size());

    return env->NewStringUTF(result.c_str());
}

/**
 * unloadModel(): Void
 *
 * Frees the model and context resources.
 */
JNIEXPORT void JNICALL
Java_com_sentinel_bridge_native_1_NativeBridge_unloadModel(
        JNIEnv *env,
        jobject /* this */) {

    LOGI("Unloading model");

    // Cancel any running inference
    g_cancel_flag.store(true);

    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    LOGI("Model unloaded");
}

/**
 * health(): Boolean
 *
 * Returns true if a model is loaded and the context is ready for inference.
 */
JNIEXPORT jboolean JNICALL
Java_com_sentinel_bridge_native_1_NativeBridge_health(
        JNIEnv *env,
        jobject /* this */) {

    bool healthy = (g_model != nullptr && g_ctx != nullptr);
    LOGI("Health check: %s", healthy ? "OK" : "NOT_READY");
    return healthy ? JNI_TRUE : JNI_FALSE;
}

/**
 * cancelInference(): Void
 *
 * Sets the atomic cancellation flag. The inference loop checks this flag
 * on every token generation iteration and stops if set.
 */
JNIEXPORT void JNICALL
Java_com_sentinel_bridge_native_1_NativeBridge_cancelInference(
        JNIEnv *env,
        jobject /* this */) {

    LOGI("Cancellation requested");
    g_cancel_flag.store(true);
}

} // extern "C"
