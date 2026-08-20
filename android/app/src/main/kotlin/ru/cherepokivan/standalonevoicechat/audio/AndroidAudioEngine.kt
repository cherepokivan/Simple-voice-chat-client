package ru.cherepokivan.standalonevoicechat.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android capture/playback bridge. It never writes microphone data to disk and never emits PCM to the network.
 * Opus packaging may only be attached after a verified, upstream-supported SVC protocol adapter is available.
 */
class AndroidAudioEngine(@Suppress("UNUSED_PARAMETER") context: Context) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private val isCapturing = AtomicBoolean(false)

    fun startCapture(onFrame: (ShortArray) -> Unit) {
        if (!isCapturing.compareAndSet(false, true)) return
        val sampleRate = 48_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 10 * 2)
        val localRecorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        recorder = localRecorder
        localRecorder.startRecording()
        captureJob = scope.launch {
            val buffer = ShortArray(960)
            while (isCapturing.get()) {
                val read = localRecorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) onFrame(buffer.copyOf(read))
            }
        }
    }

    fun play(frame: ShortArray) {
        val track = player ?: createPlayer().also { player = it }
        track.write(frame, 0, frame.size, AudioTrack.WRITE_NON_BLOCKING)
    }

    fun stopCapture() {
        if (!isCapturing.compareAndSet(true, false)) return
        captureJob?.cancel()
        captureJob = null
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
    }

    override fun close() {
        stopCapture()
        player?.release()
        player = null
    }

    private fun createPlayer(): AudioTrack {
        val sampleRate = 48_000
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 10 * 2)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }
    }
}
