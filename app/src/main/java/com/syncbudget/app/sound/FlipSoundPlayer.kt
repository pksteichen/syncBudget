package com.syncbudget.app.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class FlipSoundPlayer(context: Context) {

    private val soundPool: SoundPool
    private var clackSoundId: Int = 0
    private var loaded = false

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) loaded = true
        }

        clackSoundId = generateAndLoadClack(context)
    }

    private fun generateAndLoadClack(context: Context): Int {
        val sampleRate = 44100
        val durationMs = 45
        val numSamples = (sampleRate * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val random = java.util.Random(42)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate

            // Fast-attack exponential decay envelope
            val envelope = exp(-t.toDouble() * 120.0).toFloat()

            // Secondary mechanical bounce at ~10ms
            val bounceDelta = t - 0.010f
            val bounce = 0.3f * exp(-(bounceDelta * bounceDelta).toDouble() / 0.000002).toFloat()

            val totalEnvelope = (envelope + bounce).coerceAtMost(1.0f)

            // Band-limited noise: mechanical impact frequencies
            val signal = (
                0.5f * sin(2.0 * PI * 1200.0 * t + random.nextDouble() * PI).toFloat() +
                0.3f * sin(2.0 * PI * 2400.0 * t + random.nextDouble() * PI).toFloat() +
                0.2f * sin(2.0 * PI * 800.0 * t + random.nextDouble() * PI).toFloat() +
                0.4f * (random.nextFloat() * 2f - 1f)
            )

            val sample = (signal * totalEnvelope * Short.MAX_VALUE * 0.7f).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples[i] = sample.toShort()
        }

        // Encode as WAV and write to cache
        val wavBytes = encodeWav(samples, sampleRate)
        val cacheFile = File(context.cacheDir, "clack.wav")
        cacheFile.writeBytes(wavBytes)

        return soundPool.load(cacheFile.absolutePath, 1)
    }

    private fun encodeWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val fileSize = 36 + dataSize

        val buffer = ByteArrayOutputStream()
        val dos = DataOutputStream(buffer)

        fun writeLE16(v: Int) {
            dos.write(v and 0xFF)
            dos.write((v shr 8) and 0xFF)
        }

        fun writeLE32(v: Int) {
            dos.write(v and 0xFF)
            dos.write((v shr 8) and 0xFF)
            dos.write((v shr 16) and 0xFF)
            dos.write((v shr 24) and 0xFF)
        }

        // RIFF header
        dos.writeBytes("RIFF")
        writeLE32(fileSize)
        dos.writeBytes("WAVE")

        // fmt sub-chunk
        dos.writeBytes("fmt ")
        writeLE32(16)          // sub-chunk size
        writeLE16(1)           // PCM format
        writeLE16(1)           // mono
        writeLE32(sampleRate)
        writeLE32(sampleRate * 2) // byte rate (16-bit mono)
        writeLE16(2)           // block align
        writeLE16(16)          // bits per sample

        // data sub-chunk
        dos.writeBytes("data")
        writeLE32(dataSize)
        for (s in samples) {
            writeLE16(s.toInt() and 0xFFFF)
        }
        dos.flush()

        return buffer.toByteArray()
    }

    fun playClack() {
        if (loaded) {
            soundPool.play(clackSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
