package com.openair.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Thin MediaRecorder wrapper: records mic input to an AAC .m4a file in app
 * storage, mono 44.1kHz — matching the eventual -16 LUFS normalization
 * pipeline's expected input.
 */
class ClipRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean {
        stopQuietly()
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
        return try {
            @Suppress("DEPRECATION")
            recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setAudioChannels(1)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            outputFile = file
            true
        } catch (t: Throwable) {
            Log.w(TAG, "record start failed", t)
            recorder?.release()
            recorder = null
            file.delete()
            false
        }
    }

    /** Stops and returns the recorded file, or null if nothing usable was captured. */
    fun stop(): File? {
        val file = outputFile
        try {
            recorder?.stop()
        } catch (t: Throwable) {
            // stop() throws if the recording is too short to contain audio.
            Log.w(TAG, "record stop failed", t)
            file?.delete()
            outputFile = null
        } finally {
            recorder?.release()
            recorder = null
        }
        return outputFile.also { outputFile = null }
    }

    fun discard() {
        stop()?.delete()
    }

    private fun stopQuietly() {
        try {
            recorder?.stop()
        } catch (_: Throwable) {
        }
        recorder?.release()
        recorder = null
    }

    private companion object {
        const val TAG = "ClipRecorder"
    }
}
