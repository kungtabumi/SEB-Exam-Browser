package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.sin

object SoundUtils {

    private const val TAG = "SoundUtils"

    @Volatile
    private var lastBreachPlayTimestamp: Long = 0L

    @Volatile
    private var lastUnlockPlayTimestamp: Long = 0L

    @Volatile
    private var isBreachAlertActive: Boolean = false

    private var activeBreachAudioTrack: AudioTrack? = null
    private var activeUnlockAudioTrack: AudioTrack? = null

    /**
     * 1. ALARM MELEPASKAN SEMATAN DENGAN PAKSA (Forced Unpin / Security Breach Siren)
     * Dipicu ketika siswa berusaha melepas sematan (Recent Apps + Home / Back / Gestur Unpin).
     * Menghasilkan sirene darurat melengking berulang-ulang dengan volume maksimal (alarm asli AudioTrack).
     */
    fun playForcedUnpinBreachSiren(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastBreachPlayTimestamp < 1500) {
            return
        }
        lastBreachPlayTimestamp = now

        val appContext = context.applicationContext

        // Maximize stream volume for USAGE_ALARM / STREAM_ALARM
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                try {
                    val maxAlarmVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot override STREAM_ALARM volume: ${e.message}")
                }

                try {
                    val maxMusicVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot override STREAM_MUSIC volume: ${e.message}")
                }

                try {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed adjusting volume for breach alert", e)
        }

        // Stop any previous instance
        stopAlert()
        isBreachAlertActive = true

        // Synthesize Piercing High-Pitch Pulsating Police Siren via AudioTrack (Alarm Asli)
        Thread {
            try {
                playSynthesizedBreachSiren()
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack breach siren failed", e)
            }
        }.start()
    }

    /**
     * Alias for backward compatibility
     */
    fun playExitExamLoudAlert(context: Context) {
        playForcedUnpinBreachSiren(context)
    }

    /**
     * 2. ALARM / NADA RESMI BERHASIL KELUAR DENGAN PIN PENGAWAS (Proctor Validated Checkout Fanfare)
     * Dipicu HANYA ketika Pengawas memasukkan PIN yang benar di dialog keluar.
     * Menghasilkan nada lonceng 4-nada harmonik (G4 -> C5 -> E5 -> G5) + nada konfirmasi sonifikasi.
     * Berbunyi jelas dan berwibawa, berbeda dengan sirene bahaya pelepasan paksa.
     */
    fun playProctorPinSuccessUnlockSound(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastUnlockPlayTimestamp < 1500) {
            return
        }
        lastUnlockPlayTimestamp = now

        val appContext = context.applicationContext
        stopAlert()

        // Set optimal notification/music volume for confirmation
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85).toInt(), 0)
            }
        } catch (_: Exception) {}

        Thread {
            try {
                val sampleRate = 44100
                // 4-note ascending fanfare chime: G4 (392Hz), C5 (523Hz), E5 (659Hz), G5 (784Hz)
                val frequencies = doubleArrayOf(392.00, 523.25, 659.25, 783.99)
                val noteDurations = intArrayOf(180, 180, 200, 450) // Last note rings out longer

                var totalSamples = 0
                for (dur in noteDurations) {
                    totalSamples += (sampleRate * (dur / 1000.0)).toInt()
                }

                val pcmData = ShortArray(totalSamples)
                var offset = 0

                for (idx in frequencies.indices) {
                    val freq = frequencies[idx]
                    val durMs = noteDurations[idx]
                    val numSamples = (sampleRate * (durMs / 1000.0)).toInt()

                    for (i in 0 until numSamples) {
                        val t = i.toDouble() / sampleRate
                        val progress = i.toDouble() / numSamples

                        // Bell-like harmonic synthesis (Fundamental + 2nd overtone + soft decay envelope)
                        val envelope = (1.0 - progress) * (1.0 - progress)
                        val wave1 = sin(2.0 * Math.PI * freq * t)
                        val wave2 = sin(4.0 * Math.PI * freq * t) * 0.3
                        val wave3 = sin(6.0 * Math.PI * freq * t) * 0.15

                        val mixed = (wave1 + wave2 + wave3) * envelope * Short.MAX_VALUE * 0.8
                        pcmData[offset + i] = mixed.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    offset += numSamples
                }

                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(pcmData.size * 2)

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeUnlockAudioTrack = audioTrack
                audioTrack.write(pcmData, 0, pcmData.size)
                audioTrack.play()

                Thread.sleep(1100)
                try {
                    audioTrack.release()
                    if (activeUnlockAudioTrack == audioTrack) {
                        activeUnlockAudioTrack = null
                    }
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "Proctor unlock chime failed", e)
            }
        }.start()
    }

    /**
     * Alias for proctor success tone
     */
    fun playSuccessUnlockTone(context: Context) {
        playProctorPinSuccessUnlockSound(context)
    }

    /**
     * Synthesize a loud 2.6-second piercing European police emergency siren for forced unpin.
     */
    private fun playSynthesizedBreachSiren() {
        val sampleRate = 44100
        val durationSeconds = 2.6
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val pcmBuffer = ShortArray(totalSamples)

        val fLow = 650.0
        val fHigh = 1450.0
        val modulationPeriodSec = 0.30 // Fast urgent siren sweep

        var currentPhase = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val cycleProgress = (t % modulationPeriodSec) / modulationPeriodSec
            val modFactor = if (cycleProgress < 0.5) {
                cycleProgress * 2.0
            } else {
                (1.0 - cycleProgress) * 2.0
            }
            val instantaneousFreq = fLow + (fHigh - fLow) * modFactor

            currentPhase += 2.0 * Math.PI * instantaneousFreq / sampleRate
            if (currentPhase > 2.0 * Math.PI) {
                currentPhase -= 2.0 * Math.PI
            }

            // Harsh square-softened wave for high penetrative emergency sound
            val sinVal = sin(currentPhase)
            val sampleVal = (sinVal * 0.70 + if (sinVal > 0) 0.30 else -0.30) * Short.MAX_VALUE * 0.90
            pcmBuffer[i] = sampleVal.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcmBuffer.size * 2)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        activeBreachAudioTrack = audioTrack
        audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
        audioTrack.play()

        Thread.sleep((durationSeconds * 1000).toLong() + 300)
        try {
            audioTrack.release()
            if (activeBreachAudioTrack == audioTrack) {
                activeBreachAudioTrack = null
            }
        } catch (_: Exception) {}
        isBreachAlertActive = false
    }

    fun stopAlert() {
        isBreachAlertActive = false
        try {
            activeBreachAudioTrack?.let {
                it.stop()
                it.release()
            }
            activeBreachAudioTrack = null
        } catch (_: Exception) {}

        try {
            activeUnlockAudioTrack?.let {
                it.stop()
                it.release()
            }
            activeUnlockAudioTrack = null
        } catch (_: Exception) {}
    }
}
