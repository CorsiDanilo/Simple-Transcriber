package com.anomalyzed.simpletranscriber.data

import android.content.Context

/**
 * Final, non-cancellable outcome of a transcription job.
 * Only Success and Error are stored — intermediate states (Loading, Streaming, Setup)
 * are transient and not relevant after process death.
 */
sealed class FinalState {
    data class Success(val text: String) : FinalState()
    data class Error(val message: String) : FinalState()
}

/**
 * Lightweight SharedPreferences store that persists the final outcome of a transcription
 * job keyed by its transcriptionId.
 *
 * Design contract:
 * - Write-once: [persist] stores the result when the Service finishes.
 * - Read-once-and-delete: [consume] returns the value and immediately removes it, so
 *   entries are never stale across multiple app opens.
 * - TTL: entries older than [TTL_MS] (1 hour) are ignored and cleaned up on [consume].
 *
 * This is used to recover the UI state when the OS has killed the process between the
 * moment the Service posts the completion notification and the moment the user taps it.
 */
class TranscriptionStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Persists [state] for [id]. Safe to call from any thread (SharedPreferences apply() is async).
     */
    fun persist(id: Long, state: FinalState) {
        val type = when (state) {
            is FinalState.Success -> TYPE_SUCCESS
            is FinalState.Error   -> TYPE_ERROR
        }
        val text = when (state) {
            is FinalState.Success -> state.text
            is FinalState.Error   -> state.message
        }
        prefs.edit()
            .putString(keyType(id), type)
            .putString(keyText(id), text)
            .putLong(keyTs(id), System.currentTimeMillis())
            .apply()
    }

    /**
     * Returns the persisted [FinalState] for [id] and immediately deletes it, or null if:
     * - no entry exists for this id
     * - the entry is older than [TTL_MS]
     */
    fun consume(id: Long): FinalState? {
        val type = prefs.getString(keyType(id), null) ?: return null
        val text = prefs.getString(keyText(id), null) ?: run { delete(id); return null }
        val ts   = prefs.getLong(keyTs(id), 0L)

        delete(id)

        if (System.currentTimeMillis() - ts > TTL_MS) return null

        return when (type) {
            TYPE_SUCCESS -> FinalState.Success(text)
            TYPE_ERROR   -> FinalState.Error(text)
            else         -> null
        }
    }

    private fun delete(id: Long) {
        prefs.edit()
            .remove(keyType(id))
            .remove(keyText(id))
            .remove(keyTs(id))
            .apply()
    }

    private fun keyType(id: Long) = "result_${id}_type"
    private fun keyText(id: Long) = "result_${id}_text"
    private fun keyTs(id: Long)   = "result_${id}_ts"

    companion object {
        private const val PREFS_NAME  = "transcription_state_store"
        private const val TYPE_SUCCESS = "success"
        private const val TYPE_ERROR   = "error"
        private const val TTL_MS       = 60 * 60 * 1_000L // 1 hour
    }
}
