package mu.moris.agent.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Fully local speech engine.
 *
 * Audio is recorded to app-internal storage, transcribed with a bundled
 * multilingual Whisper model, then the temporary WAV is deleted.
 * No network API is used.
 */
class LocalWhisperVoiceEngine(private val context: Context) {
    private val recording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var recordThread: Thread? = null
    private var wavFile: File? = null
    private var model: WhisperModel? = null

    fun isRecording(): Boolean = recording.get()

    fun start(): Result<Unit> {
        if (recording.get()) return Result.failure(IllegalStateException("Already recording"))
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("Microphone permission is required"))
        }

        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return Result.failure(IllegalStateException("Unable to initialize microphone"))

        val output = File(context.cacheDir, "voice-command.wav")
        wavFile = output

        return try {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                return Result.failure(IllegalStateException("Microphone could not be initialized"))
            }

            recorder = audioRecord
            writeWavHeader(output, sampleRate, 1, 16)
            audioRecord.startRecording()
            recording.set(true)

            recordThread = thread(name = "moris-agent-recorder") {
                val buffer = ByteArray(minBuffer)
                FileOutputStream(output, true).use { stream ->
                    while (recording.get()) {
                        val count = audioRecord.read(buffer, 0, buffer.size)
                        if (count > 0) stream.write(buffer, 0, count)
                    }
                }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            recording.set(false)
            recorder?.release()
            recorder = null
            Result.failure(t)
        }
    }

    suspend fun stopAndTranscribe(): Result<String> {
        if (!recording.get()) return Result.failure(IllegalStateException("Not recording"))

        recording.set(false)
        try {
            recorder?.stop()
        } catch (_: Throwable) {}
        recordThread?.join(1500)
        recorder?.release()
        recorder = null

        val file = wavFile ?: return Result.failure(IllegalStateException("Recording file missing"))
        finalizeWavHeader(file)

        return try {
            val loaded = model ?: Whisper.loadModelFromAsset(context, "models/ggml-base.bin").also { model = it }
            val result = Whisper.transcribe(loaded, file.absolutePath, WhisperConfig())
            val text = result.text.trim()
            file.delete()
            if (text.isBlank()) Result.failure(IllegalStateException("I could not understand the recording"))
            else Result.success(text)
        } catch (t: Throwable) {
            file.delete()
            Result.failure(t)
        }
    }

    fun release() {
        recording.set(false)
        try { recorder?.stop() } catch (_: Throwable) {}
        recorder?.release()
        recorder = null
        model?.let { Whisper.releaseModel(it) }
        model = null
    }

    private fun writeWavHeader(file: File, sampleRate: Int, channels: Int, bits: Int) {
        FileOutputStream(file).use { out ->
            val byteRate = sampleRate * channels * bits / 8
            val header = ByteArray(44)
            "RIFF".toByteArray().copyInto(header, 0)
            writeIntLE(header, 4, 36)
            "WAVE".toByteArray().copyInto(header, 8)
            "fmt ".toByteArray().copyInto(header, 12)
            writeIntLE(header, 16, 16)
            writeShortLE(header, 20, 1)
            writeShortLE(header, 22, channels)
            writeIntLE(header, 24, sampleRate)
            writeIntLE(header, 28, byteRate)
            writeShortLE(header, 32, channels * bits / 8)
            writeShortLE(header, 34, bits)
            "data".toByteArray().copyInto(header, 36)
            writeIntLE(header, 40, 0)
            out.write(header)
        }
    }

    private fun finalizeWavHeader(file: File) {
        val dataSize = (file.length() - 44).coerceAtLeast(0).toInt()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(intToLeBytes(dataSize + 36))
            raf.seek(40)
            raf.write(intToLeBytes(dataSize))
        }
    }

    private fun writeIntLE(target: ByteArray, offset: Int, value: Int) {
        val b = intToLeBytes(value)
        for (i in b.indices) target[offset + i] = b[i]
    }

    private fun writeShortLE(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = ((value shr 8) and 0xff).toByte()
    }

    private fun intToLeBytes(value: Int) = byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte(),
        ((value shr 16) and 0xff).toByte(),
        ((value shr 24) and 0xff).toByte()
    )
}
