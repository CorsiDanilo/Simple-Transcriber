#include <jni.h>
#include <android/log.h>
#include <cstring>

#include "whisper.h"

#define TAG "TranscriberWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

#define UNUSED(x) (void)(x)

struct callback_context {
    JNIEnv *env;
    jobject callback;
    jmethodID on_progress;
    jmethodID on_new_segment;
    jmethodID should_abort;
};

static void progress_callback(
        whisper_context *ctx,
        whisper_state *state,
        int progress,
        void *user_data
) {
    UNUSED(ctx);
    UNUSED(state);
    auto *callbacks = reinterpret_cast<callback_context *>(user_data);
    callbacks->env->CallVoidMethod(callbacks->callback, callbacks->on_progress, progress);
}

static void new_segment_callback(
        whisper_context *ctx,
        whisper_state *state,
        int n_new,
        void *user_data
) {
    UNUSED(state);
    auto *callbacks = reinterpret_cast<callback_context *>(user_data);
    const int segment_count = whisper_full_n_segments(ctx);
    const int first_new_segment = segment_count - n_new;
    for (int i = first_new_segment; i < segment_count; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        jstring segment = callbacks->env->NewStringUTF(text != nullptr ? text : "");
        callbacks->env->CallVoidMethod(callbacks->callback, callbacks->on_new_segment, segment);
        callbacks->env->DeleteLocalRef(segment);
    }
}

static bool abort_callback(void *user_data) {
    auto *callbacks = reinterpret_cast<callback_context *>(user_data);
    return callbacks->env->CallBooleanMethod(callbacks->callback, callbacks->should_abort);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_anomalyzed_simpletranscriber_whisper_NativeWhisper_00024Companion_initContext(
        JNIEnv *env,
        jobject thiz,
        jstring model_path_str
) {
    UNUSED(thiz);
    const char *model_path = env->GetStringUTFChars(model_path_str, nullptr);
    whisper_context_params params = whisper_context_default_params();
    whisper_context *context = whisper_init_from_file_with_params(model_path, params);
    env->ReleaseStringUTFChars(model_path_str, model_path);
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT void JNICALL
Java_com_anomalyzed_simpletranscriber_whisper_NativeWhisper_00024Companion_freeContext(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr
) {
    UNUSED(env);
    UNUSED(thiz);
    auto *context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context != nullptr) {
        whisper_free(context);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_anomalyzed_simpletranscriber_whisper_NativeWhisper_00024Companion_fullTranscribe(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jint num_threads,
        jfloatArray audio_data,
        jstring language_str,
        jobject callback
) {
    UNUSED(thiz);
    auto *context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context == nullptr) {
        LOGW("Cannot transcribe with a null context");
        return -1;
    }

    jfloat *audio_data_arr = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize audio_data_length = env->GetArrayLength(audio_data);
    const char *language = language_str != nullptr
            ? env->GetStringUTFChars(language_str, nullptr)
            : nullptr;
    jclass callback_class = env->GetObjectClass(callback);
    callback_context callbacks = {};
    callbacks.env = env;
    callbacks.callback = callback;
    callbacks.on_progress = env->GetMethodID(callback_class, "onProgress", "(I)V");
    callbacks.on_new_segment = env->GetMethodID(callback_class, "onNewSegment", "(Ljava/lang/String;)V");
    callbacks.should_abort = env->GetMethodID(callback_class, "shouldAbort", "()Z");
    env->DeleteLocalRef(callback_class);
    if (callbacks.on_progress == nullptr ||
        callbacks.on_new_segment == nullptr ||
        callbacks.should_abort == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        LOGW("Whisper callback methods were not found");
        if (language != nullptr) {
            env->ReleaseStringUTFChars(language_str, language);
        }
        env->ReleaseFloatArrayElements(audio_data, audio_data_arr, JNI_ABORT);
        return -2;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = language;
    params.n_threads = num_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = false;
    params.progress_callback = progress_callback;
    params.progress_callback_user_data = &callbacks;
    params.new_segment_callback = new_segment_callback;
    params.new_segment_callback_user_data = &callbacks;
    params.abort_callback = abort_callback;
    params.abort_callback_user_data = &callbacks;

    whisper_reset_timings(context);
    LOGI("Running whisper_full with %d samples and %d threads", audio_data_length, num_threads);
    const int result = whisper_full(context, params, audio_data_arr, audio_data_length);
    if (result != 0) {
        LOGW("whisper_full failed: %d", result);
    }

    if (language != nullptr) {
        env->ReleaseStringUTFChars(language_str, language);
    }
    env->ReleaseFloatArrayElements(audio_data, audio_data_arr, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_anomalyzed_simpletranscriber_whisper_NativeWhisper_00024Companion_getTextSegmentCount(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr
) {
    UNUSED(env);
    UNUSED(thiz);
    auto *context = reinterpret_cast<whisper_context *>(context_ptr);
    return context != nullptr ? whisper_full_n_segments(context) : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_anomalyzed_simpletranscriber_whisper_NativeWhisper_00024Companion_getTextSegment(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jint index
) {
    UNUSED(thiz);
    auto *context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context == nullptr) {
        return env->NewStringUTF("");
    }
    const char *text = whisper_full_get_segment_text(context, index);
    return env->NewStringUTF(text != nullptr ? text : "");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_anomalyzed_simpletranscriber_whisper_NativeWhisper_00024Companion_getSystemInfo(
        JNIEnv *env,
        jobject thiz
) {
    UNUSED(thiz);
    const char *sysinfo = whisper_print_system_info();
    return env->NewStringUTF(sysinfo != nullptr ? sysinfo : "");
}
