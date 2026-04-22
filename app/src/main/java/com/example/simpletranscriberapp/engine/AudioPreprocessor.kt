package com.example.simpletranscriberapp.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility per il preprocessing dell'audio prima dell'inferenza on-device.
 *
 * I modelli on-device (LiteRT-LM, AICore) tipicamente richiedono:
 * - Sample rate: 16 kHz
 * - Canali: mono
 * - Formato: PCM 16-bit signed little-endian
 * - Durata massima segmento: 30 secondi
 */
class AudioPreprocessor {

    companion object {
        const val TARGET_SAMPLE_RATE = 16000
        const val TARGET_CHANNELS = 1
        const val BYTES_PER_SAMPLE = 2 // PCM 16-bit
        const val MAX_SEGMENT_DURATION_SEC = 30

        /** Bytes per 30 secondi di audio PCM 16kHz mono 16-bit */
        val MAX_SEGMENT_BYTES = TARGET_SAMPLE_RATE * TARGET_CHANNELS * BYTES_PER_SAMPLE * MAX_SEGMENT_DURATION_SEC
    }

    /**
     * Decodifica audio da qualsiasi formato supportato da Android a PCM 16kHz mono 16-bit.
     * Usa MediaExtractor + MediaCodec per la decodifica hardware-accelerated.
     *
     * @param audioBytes Audio nei bytes originali (OGG, MP4, MP3, WAV, etc.)
     * @param mimeType MIME type dell'audio
     * @return ByteArray con PCM 16kHz mono 16-bit
     */
    suspend fun decodeToRawPcm(audioBytes: ByteArray, mimeType: String): ByteArray =
        withContext(Dispatchers.IO) {
            // Scrivi l'audio in un file temporaneo per MediaExtractor
            val tempFile = File.createTempFile("audio_input_", ".tmp")
            try {
                FileOutputStream(tempFile).use { it.write(audioBytes) }
                decodeFile(tempFile)
            } finally {
                tempFile.delete()
            }
        }

    /**
     * Divide un buffer PCM in segmenti di massimo [MAX_SEGMENT_DURATION_SEC] secondi.
     *
     * @param pcmBytes Audio PCM 16kHz mono 16-bit
     * @return Lista di segmenti PCM. Se l'audio è ≤ 30s, restituisce una lista con un solo elemento.
     */
    fun splitIntoSegments(pcmBytes: ByteArray): List<ByteArray> {
        if (pcmBytes.size <= MAX_SEGMENT_BYTES) {
            return listOf(pcmBytes)
        }

        val segments = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < pcmBytes.size) {
            val end = minOf(offset + MAX_SEGMENT_BYTES, pcmBytes.size)
            segments.add(pcmBytes.copyOfRange(offset, end))
            offset = end
        }
        return segments
    }

    /**
     * Calcola la durata in secondi di un buffer PCM.
     */
    fun getDurationSeconds(pcmBytes: ByteArray): Float {
        return pcmBytes.size.toFloat() / (TARGET_SAMPLE_RATE * TARGET_CHANNELS * BYTES_PER_SAMPLE)
    }

    // ── Decodifica interna ───────────────────────────────────────────

    private fun decodeFile(file: File): ByteArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)

        // Trova la traccia audio
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }

        if (audioTrackIndex == -1 || audioFormat == null) {
            extractor.release()
            throw IllegalArgumentException("No audio track found in file")
        }

        extractor.selectTrack(audioTrackIndex)

        val sourceSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val sourceChannels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codecMime = audioFormat.getString(MediaFormat.KEY_MIME)!!

        // Configura il decoder
        val codec = MediaCodec.createDecoderByType(codecMime)
        codec.configure(audioFormat, null, null, 0)
        codec.start()

        val outputStream = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var isEOS = false

        try {
            while (true) {
                // Feed input
                if (!isEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                // Drain output
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                    val pcmChunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmChunk)
                    outputStream.write(pcmChunk)
                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Formato output cambiato, continua
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        var pcmBytes = outputStream.toByteArray()

        // Converti a mono se necessario
        if (sourceChannels > 1) {
            pcmBytes = convertToMono(pcmBytes, sourceChannels)
        }

        // Resample se necessario
        if (sourceSampleRate != TARGET_SAMPLE_RATE) {
            pcmBytes = resample(pcmBytes, sourceSampleRate, TARGET_SAMPLE_RATE)
        }

        return pcmBytes
    }

    /**
     * Converte audio multi-canale a mono facendo la media dei canali.
     */
    private fun convertToMono(pcmBytes: ByteArray, channels: Int): ByteArray {
        val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalSamples = shortBuffer.remaining()
        val monoSamples = totalSamples / channels
        val output = ByteBuffer.allocate(monoSamples * BYTES_PER_SAMPLE)
            .order(ByteOrder.LITTLE_ENDIAN)
        val shortOutput = output.asShortBuffer()

        for (i in 0 until monoSamples) {
            var sum = 0L
            for (ch in 0 until channels) {
                sum += shortBuffer.get(i * channels + ch)
            }
            shortOutput.put((sum / channels).toShort())
        }

        return output.array()
    }

    /**
     * Resample lineare semplice. Per la trascrizione audio, questa approssimazione
     * è sufficiente — non serve un resampler di qualità audiophile.
     */
    private fun resample(pcmBytes: ByteArray, fromRate: Int, toRate: Int): ByteArray {
        val inputBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val inputLength = inputBuffer.remaining()
        val outputLength = (inputLength.toLong() * toRate / fromRate).toInt()
        val output = ByteBuffer.allocate(outputLength * BYTES_PER_SAMPLE)
            .order(ByteOrder.LITTLE_ENDIAN)
        val shortOutput = output.asShortBuffer()

        for (i in 0 until outputLength) {
            val srcPos = i.toDouble() * fromRate / toRate
            val srcIdx = srcPos.toInt().coerceIn(0, inputLength - 1)
            shortOutput.put(inputBuffer.get(srcIdx))
        }

        return output.array()
    }

    /**
     * Aggiunge un header WAV standard (44 byte) ai dati PCM raw.
     * Necessario perché LiteRT-LM/miniaudio si aspetta un formato file (WAV/MP3/etc)
     * e non PCM raw senza header.
     */
    fun addWavHeader(pcmBytes: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF header
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcmBytes.size)
        header.put("WAVE".toByteArray())
        
        // fmt sub-chunk
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size per PCM
        header.putShort(1.toShort()) // AudioFormat: 1 = PCM
        header.putShort(TARGET_CHANNELS.toShort())
        header.putInt(TARGET_SAMPLE_RATE)
        header.putInt(TARGET_SAMPLE_RATE * TARGET_CHANNELS * BYTES_PER_SAMPLE) // ByteRate
        header.putShort((TARGET_CHANNELS * BYTES_PER_SAMPLE).toShort()) // BlockAlign
        header.putShort((BYTES_PER_SAMPLE * 8).toShort()) // BitsPerSample
        
        // data sub-chunk
        header.put("data".toByteArray())
        header.putInt(pcmBytes.size)
        
        val result = ByteArray(44 + pcmBytes.size)
        System.arraycopy(header.array(), 0, result, 0, 44)
        System.arraycopy(pcmBytes, 0, result, 44, pcmBytes.size)
        
        return result
    }
}
