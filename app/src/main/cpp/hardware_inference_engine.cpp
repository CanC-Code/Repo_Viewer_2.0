#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <algorithm>
#include <android/log.h>

// Llama.cpp API for GGUF parsing and LLM inference
#include "llama.h"

#define LOG_TAG "NativeLlmEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ----------------------------------------------------------------------------
// GGUF Engine Context Structure
// ----------------------------------------------------------------------------
struct EngineContext {
    bool isLoaded = false;
    std::string activeModelPath;
    std::mutex executionMutex;
    
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
};

// Global instance of the engine context
static EngineContext g_Context;

// ----------------------------------------------------------------------------
// JNI Methods
// ----------------------------------------------------------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_com_explorer_ai_domain_NativeHardwareLlmEngine_initNativeEngine(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPathStr) {

    std::lock_guard<std::mutex> lock(g_Context.executionMutex);

    const char* modelPath = env->GetStringUTFChars(modelPathStr, nullptr);
    LOGI("Initializing GGUF backend. Target: %s", modelPath);

    // Initialize backend architecture (CPU/GPU optimizations)
    llama_backend_init();

    // Load the .gguf model weights natively utilizing the modern API
    llama_model_params model_params = llama_model_default_params();
    g_Context.model = llama_model_load_from_file(modelPath, model_params);

    if (g_Context.model == nullptr) {
        LOGE("Hardware Fault: Failed to load GGUF model tensors from path.");
        env->ReleaseStringUTFChars(modelPathStr, modelPath);
        return JNI_FALSE;
    }

    // Allocate the working memory context based on hardware limits
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; // Maximum sliding window context
    ctx_params.n_threads = std::max(1, (int)std::thread::hardware_concurrency() - 2);
    
    g_Context.ctx = llama_new_context_with_model(g_Context.model, ctx_params);

    g_Context.activeModelPath = std::string(modelPath);
    g_Context.isLoaded = true;

    env->ReleaseStringUTFChars(modelPathStr, modelPath);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_explorer_ai_domain_NativeHardwareLlmEngine_processPromptNative(
    JNIEnv* env,
    jobject /* this */,
    jstring promptStr) {

    std::lock_guard<std::mutex> lock(g_Context.executionMutex);

    if (!g_Context.isLoaded || !g_Context.ctx || !g_Context.model) {
        LOGE("Execution Fault: Engine context not initialized.");
        return env->NewStringUTF("[SYSTEM_ERROR: Hardware bounds unallocated. Initialize context first.]");
    }

    const char* prompt = env->GetStringUTFChars(promptStr, nullptr);
    std::string dynamicInput(prompt);
    env->ReleaseStringUTFChars(promptStr, prompt);

    if (dynamicInput.empty()) {
        return env->NewStringUTF("");
    }

    LOGI("Executing forward pass on input stream: %zu bytes", dynamicInput.length());

    // Extract the decoupled vocabulary structure required by the modern API
    const struct llama_vocab* vocab = llama_model_get_vocab(g_Context.model);

    // 1. Tokenize Input
    std::vector<llama_token> tokens_list(dynamicInput.length() + 1);
    int n_tokens = llama_tokenize(vocab, dynamicInput.c_str(), dynamicInput.length(), tokens_list.data(), tokens_list.size(), true, false);
    
    if (n_tokens < 0) {
        tokens_list.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, dynamicInput.c_str(), dynamicInput.length(), tokens_list.data(), tokens_list.size(), true, false);
    }
    tokens_list.resize(n_tokens);

    // 2. Initialize Inference Batch
    llama_batch batch = llama_batch_init(512, 0, 1);
    
    // Manually map token states to bypass missing batch macros
    for (size_t i = 0; i < tokens_list.size(); i++) {
        batch.token[batch.n_tokens] = tokens_list[i];
        batch.pos[batch.n_tokens] = i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = false;
        batch.n_tokens++;
    }
    
    // Request logits only for the final token in the prompt
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(g_Context.ctx, batch) != 0) {
        LOGE("Forward pass failed during initial prompt decoding.");
        llama_batch_free(batch);
        return env->NewStringUTF("[SYSTEM_ERROR: Tensor decode fault]");
    }

    // 3. Custom Generation Loop (True Response Calculation)
    int n_cur = batch.n_tokens;
    int n_decode = 0;
    const int max_predict = 512;
    std::string outputBuffer = "";

    while (n_decode < max_predict) {
        // Retrieve calculated logits
        auto* logits = llama_get_logits_ith(g_Context.ctx, batch.n_tokens - 1);
        int n_vocab = llama_vocab_n_tokens(vocab);

        // Calculate pure greedy sampling natively across the probability distribution
        llama_token new_token_id = 0;
        float max_logit = logits[0];
        
        for(llama_token id = 1; id < n_vocab; id++) {
            if (logits[id] > max_logit) {
                max_logit = logits[id];
                new_token_id = id;
            }
        }

        // Check for End of Stream (EOS) utilizing the vocabulary pointer
        if (new_token_id == llama_vocab_eos(vocab)) {
            break;
        }

        // Convert token ID back to string fragment
        char buf[128];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            outputBuffer.append(buf, n);
        }

        // Reset the batch length index (replacing llama_batch_clear)
        batch.n_tokens = 0;
        
        // Prepare the next forward pass with the newly generated token
        batch.token[batch.n_tokens] = new_token_id;
        batch.pos[batch.n_tokens] = n_cur;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = true;
        batch.n_tokens++;

        if (llama_decode(g_Context.ctx, batch) != 0) {
            LOGE("Forward pass failed during token generation loop.");
            break;
        }

        n_cur += 1;
        n_decode += 1;
    }

    llama_batch_free(batch);
    return env->NewStringUTF(outputBuffer.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_explorer_ai_domain_NativeHardwareLlmEngine_releaseNativeEngine(
    JNIEnv* env,
    jobject /* this */) {
    
    std::lock_guard<std::mutex> lock(g_Context.executionMutex);
    
    LOGI("Executing strict teardown of GGUF engine memory.");
    
    if (g_Context.ctx) {
        llama_free(g_Context.ctx);
        g_Context.ctx = nullptr;
    }
    if (g_Context.model) {
        llama_model_free(g_Context.model);
        g_Context.model = nullptr;
    }
    
    llama_backend_free();
    
    g_Context.isLoaded = false;
    g_Context.activeModelPath.clear();
}
