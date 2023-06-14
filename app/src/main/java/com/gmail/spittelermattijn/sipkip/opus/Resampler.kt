package com.gmail.spittelermattijn.sipkip.opus

import android.media.AudioFormat
import com.gmail.spittelermattijn.sipkip.util.getByteToFloat
import com.gmail.spittelermattijn.sipkip.util.getIntToFloat
import com.gmail.spittelermattijn.sipkip.util.getShortToFloat
import com.gmail.spittelermattijn.sipkip.util.putFloatToByte
import com.gmail.spittelermattijn.sipkip.util.putFloatToInt
import com.gmail.spittelermattijn.sipkip.util.putFloatToShort
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.sqrt

class Resampler(inputFormat: AudioFormat, outputFormat: AudioFormat, useAGC: Boolean) {
    private var index = 0
    private var lastPos = Float.NaN

    private class Squares(initialSum: Float = 0f) {
        private var sum = initialSum
        private var sumElements = 0

        val mean get() = sum / sumElements
        operator fun plusAssign(value: Float) {
            sum += value
            sumElements++
        }
        private fun assign(value: Float) {
            sum = value
            sumElements = 0
        }
        infix fun asg(value: Float) = assign(value)
    }

    private var squares = Squares()
    private var clipped = false

    private val channelCount = inputFormat.channelCount
    private val resampleRatio = outputFormat.sampleRate.toFloat() / inputFormat.sampleRate
    private val readFrame = inputFormat.newFrameReader()
    private val writeFrame = outputFormat.newFrameWriter()
    private val process: (FloatArray, Float, (FloatArray) -> Unit) -> Int = if (useAGC) ::processWithAGC else ::processWithoutAGC

    private val lastValues = FloatArray(channelCount)
    private val tempValues = FloatArray(channelCount)

    fun processWithoutAGC(values: FloatArray, @Suppress("UNUSED_PARAMETER") gain: Float, emit: (FloatArray) -> Unit): Int {
        val pos = index * resampleRatio
        if (lastPos.isNaN()) {
            val initialValues = values.copyInto(tempValues)
            emit(initialValues)
        } else {
            for (p in ceil(lastPos + EPSILON).toInt()..pos.toInt()) {
                val interpolated = tempValues.apply {
                    indices.forEach { channel ->
                        set(channel, (values[channel] * (p - lastPos) + lastValues[channel] * (pos - p)) / (pos - lastPos))
                    }
                }

                emit(interpolated)
            }
        }

        values.copyInto(lastValues)
        lastPos = pos
        index++

        return 0
    }

    fun processWithAGC(values: FloatArray, gain: Float, emit: (FloatArray) -> Unit): Int {
        val pos = index * resampleRatio
        if (lastPos.isNaN()) {
            val initialValues = values.map { it * gain }.toFloatArray()
            emit(initialValues)
        } else {
            for (p in ceil(lastPos + EPSILON).toInt()..pos.toInt()) {
                val interpolated = tempValues.apply {
                    indices.forEach { channel ->
                        val value = (values[channel] * (p - lastPos) + lastValues[channel] * (pos - p)) / (pos - lastPos) * gain
                        squares += value * value
                        if (value !in -AGC_PEAK_CLIP_DETECT_THRESHOLD..AGC_PEAK_CLIP_DETECT_THRESHOLD)
                            clipped = true
                        set(channel, value)
                    }
                }

                emit(interpolated)
            }
        }

        values.copyInto(lastValues)
        lastPos = pos
        index++

        return when {
            clipped -> {
                clipped = false
                -1
            }
            index % 64 == 0 -> {
                val rootMeanSquare = sqrt(squares.mean)
                squares asg 0f
                if (rootMeanSquare in 0.01f..AGC_RMS_GAIN_INCREASE_THRESHOLD) 1 else 0
            }
            else -> 0
        }
    }

    fun resample(input: ByteBuffer, output: ByteBuffer) {
        input.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)

        index = 0
        lastPos = Float.NaN
        squares asg 0f
        clipped = false
        try {
            while (true) {
                val operation = process(input.readFrame(), agcGain) {
                    output.writeFrame(it)
                }
                agcGain = (agcGain + operation * if (operation > 0) AGC_GAIN_INCREMENT else AGC_GAIN_DECREMENT).coerceAtLeast(1f)
            }
        } catch (e: RuntimeException) {
            // BufferUnderflowException / BufferOverflowException
        }
    }

    companion object {
        private const val EPSILON = 0.002f

        private const val AGC_GAIN_INCREMENT = 0.012f
        private const val AGC_GAIN_DECREMENT = 0.16f
        private const val AGC_PEAK_CLIP_DETECT_THRESHOLD = 0.8f
        private const val AGC_RMS_GAIN_INCREASE_THRESHOLD = 0.4f
        private var agcGain = 1f

        private fun AudioFormat.newFrameReader(): ByteBuffer.() -> FloatArray {
            val readSample: ByteBuffer.() -> Float = when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> ByteBuffer::getByteToFloat
                AudioFormat.ENCODING_PCM_16BIT -> ByteBuffer::getShortToFloat
                AudioFormat.ENCODING_PCM_32BIT -> ByteBuffer::getIntToFloat
                AudioFormat.ENCODING_PCM_FLOAT -> ByteBuffer::getFloat
                else -> throw IllegalArgumentException()
            }

            val frame = FloatArray(channelCount)

            return {
                frame.apply {
                    indices.forEach { index ->
                        set(index, readSample())
                    }
                }
            }
        }

        private fun AudioFormat.newFrameWriter(): ByteBuffer.(FloatArray) -> Unit {
            val writeSample: ByteBuffer.(Float) -> Any = when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> ByteBuffer::putFloatToByte
                AudioFormat.ENCODING_PCM_16BIT -> ByteBuffer::putFloatToShort
                AudioFormat.ENCODING_PCM_32BIT -> ByteBuffer::putFloatToInt
                AudioFormat.ENCODING_PCM_FLOAT -> ByteBuffer::putFloat
                else -> throw IllegalArgumentException()
            }

            return { frame ->
                (0 until channelCount).forEach { index ->
                    val value = frame[index % frame.size]
                    writeSample(value)
                }
            }
        }
    }
}