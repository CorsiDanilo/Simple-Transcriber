package com.anomalyzed.simpletranscriber.whisper

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val LOG_TAG = "WhisperContext"

class WhisperContext private constructor(private var ptr: Long) {
    private val scope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    suspend fun transcribeData(
        data: FloatArray,
        languageCode: String,
        onProgress: (Int) -> Unit,
        onNewSegment: (String) -> Unit
    ): String =
        withContext(scope.coroutineContext) {
            require(ptr != 0L) { "Whisper context has already been released." }
            val numThreads = WhisperCpuConfig.preferredThreadCount
            val coroutineContext = currentCoroutineContext()
            val job = coroutineContext[Job]
            val abortRequested = AtomicBoolean(false)
            val completionHandle = job?.invokeOnCompletion {
                abortRequested.set(true)
            }
            val partialText = StringBuilder()
            val callback = object : NativeWhisperCallback {
                override fun onProgress(progress: Int) {
                    onProgress(progress)
                }

                override fun onNewSegment(text: String) {
                    partialText.append(text)
                    onNewSegment(partialText.toString().trim())
                }

                override fun shouldAbort(): Boolean =
                    abortRequested.get() || job?.isActive == false
            }

            try {
                val result = NativeWhisper.fullTranscribe(ptr, numThreads, data, languageCode, callback)
                if (callback.shouldAbort()) {
                    throw InterruptedException("Whisper transcription cancelled.")
                }
                if (result != 0) {
                    throw IllegalStateException("Whisper transcription failed with code $result")
                }
                val segmentCount = NativeWhisper.getTextSegmentCount(ptr)
                buildString {
                    for (i in 0 until segmentCount) {
                        append(NativeWhisper.getTextSegment(ptr, i))
                    }
                }.trim()
            } finally {
                completionHandle?.dispose()
            }
        }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            NativeWhisper.freeContext(ptr)
            ptr = 0L
        }
    }

    @Suppress("deprecation")
    protected fun finalize() {
        runBlocking { release() }
    }

    companion object {
        fun createContextFromFile(filePath: String): WhisperContext {
            val ptr = NativeWhisper.initContext(filePath)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create Whisper context with path $filePath")
            }
            return WhisperContext(ptr)
        }

        fun getSystemInfo(): String = NativeWhisper.getSystemInfo()
    }
}

private interface NativeWhisperCallback {
    fun onProgress(progress: Int)
    fun onNewSegment(text: String)
    fun shouldAbort(): Boolean
}

private class NativeWhisper {
    companion object {
        init {
            Log.d(LOG_TAG, "Primary ABI: ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}")
            System.loadLibrary("transcriber_whisper")
        }

        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(
            contextPtr: Long,
            numThreads: Int,
            audioData: FloatArray,
            languageCode: String,
            callback: NativeWhisperCallback
        ): Int
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getSystemInfo(): String
    }
}
