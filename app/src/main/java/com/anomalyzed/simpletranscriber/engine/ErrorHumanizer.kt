package com.anomalyzed.simpletranscriber.engine

import android.content.Context
import com.anomalyzed.simpletranscriber.R

/**
 * Converts raw technical exception messages into user-friendly, localized strings.
 *
 * Call [humanize] with a raw message (from an engine's TranscriptionResult.Error or a caught
 * Exception) and the service Context. The function returns the most appropriate human-readable
 * string, or falls back to [R.string.error_generic] when no pattern matches.
 */
object ErrorHumanizer {

    /**
     * Humanize a caught [Exception].
     */
    fun humanize(exception: Throwable, context: Context): String =
        humanize(exception.localizedMessage ?: exception.message ?: "", context, exception)

    /**
     * Humanize a raw error message string, optionally informed by the originating [throwable].
     *
     * Already-friendly messages (short, no Java class names) are returned as-is so that
     * engine-level messages like "API Key is missing." are not overwritten.
     */
    fun humanize(
        rawMessage: String,
        context: Context,
        throwable: Throwable? = null
    ): String {
        val msg = rawMessage.lowercase()

        // ── Network errors ────────────────────────────────────────────────────────────
        if (throwable is java.net.UnknownHostException
            || msg.contains("unknownhost")
            || msg.contains("unable to resolve host")
            || msg.contains("no address associated")
        ) return context.getString(R.string.error_no_internet)

        if (throwable is java.net.SocketTimeoutException
            || throwable is java.net.SocketException
            || msg.contains("sockettimeout")
            || msg.contains("timed out")
            || msg.contains("read timed out")
        ) return context.getString(R.string.error_timeout)

        if (throwable is java.net.ConnectException
            || msg.contains("connection refused")
            || msg.contains("failed to connect")
            || msg.contains("econnrefused")
        ) return context.getString(R.string.error_cannot_connect)

        if (throwable is javax.net.ssl.SSLException
            || msg.contains("sslexception")
            || msg.contains("ssl handshake")
            || msg.contains("certificate")
            || msg.contains("handshake_failure")
        ) return context.getString(R.string.error_ssl)

        // ── API key / auth errors ─────────────────────────────────────────────────────
        if (msg.contains("api key")
            || msg.contains("api_key")
            || msg.contains("invalid_api_key")
            || msg.contains("permission_denied")
            || msg.contains("api_key_invalid")
            || (msg.contains("403") && !msg.contains("quota"))
            || msg.contains("unauthorized")
            || msg.contains("unauthenticated")
        ) return context.getString(R.string.error_api_key_invalid)

        // ── Quota / rate limit ────────────────────────────────────────────────────────
        if (msg.contains("quota")
            || msg.contains("rate limit")
            || msg.contains("rate_limit")
            || msg.contains("429")
            || msg.contains("resource_exhausted")
            || msg.contains("too many request")
        ) return context.getString(R.string.error_quota_exceeded)

        // ── Server-side errors ────────────────────────────────────────────────────────
        if (msg.contains("internal server")
            || msg.contains("internal_server")
            || msg.contains("500")
            || msg.contains("503")
            || msg.contains("service_unavailable")
            || msg.contains("unavailable")
            || msg.contains("overloaded")
            || (msg.contains("internal") && msg.contains("error"))
        ) return context.getString(R.string.error_server_unavailable)

        // ── Safety / content policy ───────────────────────────────────────────────────
        if (msg.contains("safety")
            || msg.contains("blocked")
            || msg.contains("harm_category")
            || msg.contains("finish_reason_safety")
        ) return context.getString(R.string.error_safety_blocked)

        // ── Audio / speech ────────────────────────────────────────────────────────────
        if (msg.contains("no text")
            || msg.contains("returned no text")
            || msg.contains("no speech")
            || msg.contains("empty transcript")
        ) return context.getString(R.string.error_no_speech)

        if (msg.contains("cannot read")
            || msg.contains("can't read")
            || msg.contains("read file")
            || (throwable is java.io.IOException && msg.contains("stream"))
        ) return context.getString(R.string.error_cannot_read_file)

        if (msg.contains("unsupported")
            || msg.contains("invalid format")
            || msg.contains("codec")
            || msg.contains("decod") && msg.contains("fail")
        ) return context.getString(R.string.error_unsupported_format)

        // ── Local model errors ────────────────────────────────────────────────────────
        if (msg.contains("no whisper model")
            || msg.contains("no local model")
            || msg.contains("no model selected")
            || msg.contains("please download")
        ) return context.getString(R.string.error_no_local_model)

        if (msg.contains("model file not found")
            || msg.contains("no such file")
            || (msg.contains("model") && msg.contains("not found"))
        ) return context.getString(R.string.error_model_file_missing)

        // ── Memory ────────────────────────────────────────────────────────────────────
        if (throwable is OutOfMemoryError
            || msg.contains("out of memory")
            || msg.contains("outofmemory")
            || msg.contains("oom")
        ) return context.getString(R.string.error_out_of_memory)

        // ── AICore ────────────────────────────────────────────────────────────────────
        if (msg.contains("aicore is not available")
            || msg.contains("requires pixel")
            || msg.contains("samsung galaxy s23")
        ) return context.getString(R.string.error_aicore_unavailable)

        if (msg.contains("aicore engine integration pending")
            || msg.contains("ml kit genai")
        ) return context.getString(R.string.error_aicore_pending)

        // ── Already user-friendly? Return as-is ──────────────────────────────────────
        // A message is considered already friendly if it:
        //   • is reasonably short
        //   • does not contain Java/Kotlin class-path fragments or stack-trace keywords
        val isTechnical = msg.contains("exception")
            || msg.contains("java.")
            || msg.contains("kotlin.")
            || msg.contains("android.")
            || msg.contains("com.google.")
            || msg.contains("at com.")
            || msg.contains("caused by")
            || rawMessage.length > 200

        if (!isTechnical && rawMessage.isNotBlank()) return rawMessage

        // ── Generic fallback ──────────────────────────────────────────────────────────
        return context.getString(R.string.error_generic)
    }
}
