package com.yufiplay.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object StartupChimePlayer {
    fun playHarmonicChime() {
        thread(start = true, isDaemon = true, name = "YufiChimeThread") {
            try {
                val sampleRate = 44100
                val durationSec = 1.25
                val numSamples = (sampleRate * durationSec).toInt()
                val pcm = ShortArray(numSamples)

                // Warm major-9th arpeggio notes: C5, E5, G5, D6
                val notes = doubleArrayOf(523.25, 659.25, 783.99, 1174.66)
                val noteOffsets = doubleArrayOf(0.0, 0.11, 0.22, 0.34)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    var sample = 0.0
                    for (n in notes.indices) {
                        val localT = t - noteOffsets[n]
                        if (localT >= 0.0) {
                            val env = (1.0 - exp(-localT * 60.0)) * exp(-localT * 3.8)
                            val fundamental = sin(2.0 * PI * notes[n] * localT)
                            val harmonic = 0.28 * sin(2.0 * PI * notes[n] * 2.0 * localT)
                            sample += env * (fundamental + harmonic) * 0.24
                        }
                    }
                    val clamped = (sample * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    pcm[i] = clamped.toShort()
                }

                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferBytes = maxOf(minBuf, numSamples * 2)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(1350)
                track.stop()
                track.release()
            } catch (_: Exception) {
                // Ignore audio hardware issues on emulators/restricted devices
            }
        }
    }
}
