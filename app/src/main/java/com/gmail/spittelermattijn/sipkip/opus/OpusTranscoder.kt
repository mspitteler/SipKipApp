package com.gmail.spittelermattijn.sipkip.opus

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import com.gmail.spittelermattijn.sipkip.Preferences
import com.gmail.spittelermattijn.sipkip.R
import com.gmail.spittelermattijn.sipkip.opusjni.Opus
import com.gmail.spittelermattijn.sipkip.util.secondaryCoroutineScope
import com.gmail.spittelermattijn.sipkip.util.getByteToFloat
import com.gmail.spittelermattijn.sipkip.util.getIntToFloat
import com.gmail.spittelermattijn.sipkip.util.getShortToFloat
import com.gmail.spittelermattijn.sipkip.util.putFloatToByte
import com.gmail.spittelermattijn.sipkip.util.putFloatToInt
import com.gmail.spittelermattijn.sipkip.util.putFloatToShort
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.properties.Delegates

class OpusTranscoder(val listener: OpusTranscoderListener, input: ParcelFileDescriptor) {
    @Suppress("Unused")
    constructor(input: ParcelFileDescriptor) : this(object : OpusTranscoderListener {
        override fun onTranscoderStarted() = null
        override fun onTranscoderFinished(opusOutput: OutputStream, opusPacketsOutput: OutputStream, args: Any?) {
            opusOutput.close()
            opusPacketsOutput.close()
        }
    }, input)
    // Use a dedicated Opus encoder, since MediaCodec only supports Opus encoding from Android 10 onwards.
    private val encoder = Opus()
    private val extractor = MediaExtractor()
    private var decoder: MediaCodec? = null
    private var args: Any? = null

    private val encoderSampleRate: Int = Preferences[R.string.encoder_sample_rate_key]
    private val encoderChannelCount: Int = if (Preferences[R.string.encoder_stereo_key]) 2 else 1
    private val encoderBitrate: Int = Preferences[R.string.encoder_bitrate_key]
    private val encoderUseAGC: Boolean = Preferences[R.string.encoder_use_agc_key]

    private data class Format(val rate: Int = -1, val encoding: Int = AudioFormat.ENCODING_INVALID, val count: Int = -1)

    init {
        encoder.open(encoderChannelCount, encoderSampleRate, encoderBitrate)

        extractor.setDataSource(input.fileDescriptor)
        var sampleRate = 0
        var mimeType = ""
        var channelCount = 0
        var codecSpecificData0: ByteBuffer? = null
        var codecSpecificData1: ByteBuffer? = null
        var codecSpecificData2: ByteBuffer? = null
        for (trackNumber in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(trackNumber)
            format.getString(MediaFormat.KEY_MIME).takeIf { it?.startsWith("audio/") == true }?.let {
                extractor.selectTrack(trackNumber)

                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                mimeType = it
                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                codecSpecificData0 = format.getByteBuffer("csd-0")
                codecSpecificData1 = format.getByteBuffer("csd-1")
                codecSpecificData2 = format.getByteBuffer("csd-2")
            }
        }

        if (mimeType.isNotEmpty()) {
            val format = MediaFormat().also { it.setString(MediaFormat.KEY_MIME, mimeType) }
                .also { it.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate) }
                .also { it.setInteger(MediaFormat.KEY_PCM_ENCODING, ENCODER_PCM_ENCODING) }
                .also { it.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount) }
                // Set codec specific data if it is applicable for this container format.
                .also { codecSpecificData0?.let { csd0 -> it.setByteBuffer("csd-0", csd0) }}
                .also { codecSpecificData1?.let { csd1 -> it.setByteBuffer("csd-1", csd1) }}
                .also { codecSpecificData2?.let { csd2 -> it.setByteBuffer("csd-2", csd2) }}

            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            var name = ""
            for (codec in codecs.codecInfos) {
                if (codec.supportedTypes.contains(mimeType) && !codec.isEncoder &&
                    codec.getCapabilitiesForType(mimeType).isFormatSupported(format)) {
                    name = codec.name

                    if (name.startsWith("OMX.google.")) // We prefer OpenMAX codecs.
                        break
                }
            }

            if (name.isNotEmpty()) {
                decoder = MediaCodec.createByCodecName(name)
                decoder!!.configure(format, null, null, 0)
            }
        }
    }

    fun start(opusOutput: OutputStream, opusPacketsOutput: OutputStream) {
        val encoderFrameSize = Preferences.get<Float>(R.string.encoder_frame_size_key) * encoderSampleRate / 1000f

        decoder?.setCallback(object : MediaCodec.Callback() {
            private var lastFormat = Format()
            private val encoderFormat = Format(encoderSampleRate, ENCODER_PCM_ENCODING, encoderChannelCount)
            private lateinit var resampler: Resampler

            private var resampleBuffer: ByteBuffer? = null
            private var sos = true

            override fun onInputBufferAvailable(mc: MediaCodec, inputBufferId: Int) {
                val inputBuffer = mc.getInputBuffer(inputBufferId)!!
                // Fill inputBuffer with valid data
                var size = extractor.readSampleData(inputBuffer, 0)
                val flags = if (size <= 0) { size = 0; MediaCodec.BUFFER_FLAG_END_OF_STREAM } else extractor.sampleFlags

                mc.queueInputBuffer(inputBufferId, 0, size, extractor.sampleTime, flags)
                extractor.advance()
            }

            override fun onOutputBufferAvailable(
                mc: MediaCodec,
                outputBufferId: Int,
                outputBufferInfo: MediaCodec.BufferInfo
            ) {
                // outputBuffer is ready to be processed or rendered.
                val outputBuffer = mc.getOutputBuffer(outputBufferId)
                val bufferFormat = mc.getOutputFormat(outputBufferId)

                var eos = false

                if (resampleBuffer == null)
                    resampleBuffer = ByteBuffer.allocate(outputBuffer!!.capacity())

                if (outputBufferInfo.flags != MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    val format = Format(
                        bufferFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        if (bufferFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) bufferFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else ENCODER_PCM_ENCODING,
                        bufferFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    )
                    if (format != lastFormat) {
                        lastFormat = format
                        @SuppressLint("WrongConstant")
                        resampler = Resampler(
                            AudioFormat.Builder().setSampleRate(format.rate)
                                .setEncoding(format.encoding)
                                .setChannelMask(if (format.count == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
                                .build(),
                            AudioFormat.Builder().setSampleRate(encoderSampleRate)
                                .setEncoding(ENCODER_PCM_ENCODING)
                                .setChannelMask(if (encoderChannelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
                                .build(),
                            encoderUseAGC
                        )
                    }

                    if (format != encoderFormat)
                        // Resample here
                        resampler.resample(outputBuffer!!, resampleBuffer!!)
                    else
                        resampleBuffer!!.put(outputBuffer!!)
                } else {
                    eos = true
                }

                if (resampleBuffer!!.position() >= encoderFrameSize * encoderChannelCount * 2f || eos) {
                    // Put buffer into read mode.
                    resampleBuffer!!.flip()
                    var size = resampleBuffer!!.remaining()
                    while (size >= encoderFrameSize * encoderChannelCount * 2f) {
                        ByteArray((encoderFrameSize * encoderChannelCount * 2f).roundToInt()).let {
                            resampleBuffer!!.get(it)
                            val shortArray = ShortArray(it.size / 2)
                            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray)
                            val result = encoder.encode(shortArray, 0, shortArray.size)!!
                            opusOutput.write(result)
                            // Little endian.
                            opusPacketsOutput.write(byteArrayOf((result.size and 0xFF).toByte(), (result.size shl 8).toByte()))
                        }
                        size -= (encoderFrameSize * encoderChannelCount * 2f).roundToInt()
                    }
                    // Make available for writing again.
                    resampleBuffer!!.compact()
                }

                mc.releaseOutputBuffer(outputBufferId, false)

                if (sos) {
                    args = listener.onTranscoderStarted()
                    sos = false
                }

                if (eos) {
                    resampleBuffer = null
                    listener.onTranscoderFinished(opusOutput, opusPacketsOutput, args)
                    encoder.close()
                    extractor.release()
                    secondaryCoroutineScope.launch {
                        decoder!!.stop()
                        decoder!!.release()
                    }
                }
            }

            override fun onError(mc: MediaCodec, e: MediaCodec.CodecException) { throw e }

            override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
                // Subsequent data will conform to new format.
                // Can ignore if using getOutputFormat(outputBufferId)
            }
        })

        decoder?.start()
    }

    private companion object {
        const val ENCODER_PCM_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private class Resampler(inputFormat: AudioFormat, outputFormat: AudioFormat, useAGC: Boolean) {
        private var index = 0
        private var lastPos = Float.NaN

        private object Squares {
            private var sumElements = 0
            val mean get() = sum / sumElements
            var sum by Delegates.observable(0f) { _, _, new -> sumElements = if (new == 0f) 0 else sumElements + 1 }
        }

        private var clipped = false

        private val channelCount = inputFormat.channelCount
        private val resampleRatio = outputFormat.sampleRate.toFloat() / inputFormat.sampleRate
        private val readFrame = inputFormat.newFrameReader()
        private val writeFrame = outputFormat.newFrameWriter()
        private val processor: (FloatArray, Float, (FloatArray) -> Unit) -> Int = if (useAGC) ::process else ::processIgnoreGain

        private val lastValues = FloatArray(channelCount)
        private val tempValues = FloatArray(channelCount)

        fun processIgnoreGain(values: FloatArray, @Suppress("UNUSED_PARAMETER") gain: Float, emit: (FloatArray) -> Unit): Int {
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

        fun process(values: FloatArray, gain: Float, emit: (FloatArray) -> Unit): Int {
            val pos = index * resampleRatio
            if (lastPos.isNaN()) {
                val initialValues = values.map { it * gain }.toFloatArray()
                emit(initialValues)
            } else {
                for (p in ceil(lastPos + EPSILON).toInt()..pos.toInt()) {
                    val interpolated = tempValues.apply {
                        indices.forEach { channel ->
                            val value = (values[channel] * (p - lastPos) + lastValues[channel] * (pos - p)) / (pos - lastPos) * gain
                            Squares.sum += value * value
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
                    val rootMeanSquare = sqrt(Squares.mean)
                    println(rootMeanSquare)
                    Squares.sum = 0f
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
            Squares.sum = 0f
            clipped = false
            try {
                while (true) {
                    val operation = processor(input.readFrame(), agcGain) {
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
}
