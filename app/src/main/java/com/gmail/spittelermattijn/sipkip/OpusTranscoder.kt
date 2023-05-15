package com.gmail.spittelermattijn.sipkip

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaExtractor
import android.os.ParcelFileDescriptor
import com.gmail.spittelermattijn.sipkip.opusjni.Opus
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.reflect.KFunction0
import kotlin.reflect.KFunction2

class OpusTranscoder(input: ParcelFileDescriptor) {
    // Use a dedicated Opus encoder, since MediaCodec only supports Opus encoding from Android 10 onwards.
    private val encoder = Opus()
    private val extractor = MediaExtractor()
    private var decoder: MediaCodec? = null
    private var resampleBuffer: ByteBuffer? = null
    private var sos = true

    var onStartedListener: KFunction0<Unit>? = null
    var onFinishedListener: KFunction2<OutputStream, OutputStream, Unit>? = null

    init {
        val encoderSampleRate = Constants.DEFAULT_ENCODER_SAMPLE_RATE
        val encoderChannelCount = Constants.DEFAULT_ENCODER_CHANNEL_COUNT
        val encoderBitRate = Constants.DEFAULT_ENCODER_BIT_RATE
        encoder.open(encoderChannelCount, encoderSampleRate, encoderBitRate)

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
        val encoderSampleRate = Constants.DEFAULT_ENCODER_SAMPLE_RATE
        val encoderChannelCount = Constants.DEFAULT_ENCODER_CHANNEL_COUNT
        val encoderFrameSize = Constants.DEFAULT_ENCODER_FRAME_SIZE
        decoder?.setCallback(object : MediaCodec.Callback() {
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
                    val rate = bufferFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val encoding = if (bufferFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        bufferFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    else
                        ENCODER_PCM_ENCODING

                    val count = bufferFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (rate != encoderSampleRate || encoding != ENCODER_PCM_ENCODING || count != encoderChannelCount) {
                        // Resample here
                        Resampler.resample(
                            AudioFormat.Builder().setSampleRate(rate)
                                .setEncoding(encoding)
                                .setChannelMask(if (count == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
                                .build(),
                            outputBuffer!!,
                            AudioFormat.Builder().setSampleRate(encoderSampleRate)
                                .setEncoding(ENCODER_PCM_ENCODING)
                                .setChannelMask(if (encoderChannelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
                                .build(),
                            resampleBuffer!!
                        )
                    } else {
                        resampleBuffer!!.put(outputBuffer!!)
                    }
                } else {
                    eos = true
                }

                if (resampleBuffer!!.position() >= encoderFrameSize * encoderChannelCount * 2 || eos) {
                    // Put buffer into read mode.
                    resampleBuffer!!.flip()
                    var size = resampleBuffer!!.remaining()
                    while (size >= encoderFrameSize * encoderChannelCount * 2) {
                        ByteArray(encoderFrameSize * encoderChannelCount * 2).let {
                            resampleBuffer!!.get(it)
                            val shortArray = ShortArray(it.size / 2)
                            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray)
                            val result = encoder.encode(shortArray, 0, shortArray.size)!!
                            opusOutput.write(result)
                            // Little endian.
                            opusPacketsOutput.write(byteArrayOf((result.size and 0xFF).toByte(), (result.size shl 8).toByte()))
                        }
                        size -= encoderFrameSize * encoderChannelCount * 2
                    }
                    // Make available for writing again.
                    resampleBuffer!!.compact()
                }

                mc.releaseOutputBuffer(outputBufferId, false)

                if (sos) {
                    onStartedListener!!()
                    sos = false
                }

                if (eos) {
                    resampleBuffer = null
                    onFinishedListener!!(opusOutput, opusPacketsOutput)
                    encoder.close()
                    extractor.release()
                    coroutineScope.launch {
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

    private class Resampler constructor(private val resampleRatio: Float, channelCount: Int) {

        constructor(inSampleRate: Int, outSampleRate: Int, channelCount: Int) :
                this(outSampleRate.toFloat() / inSampleRate, channelCount)

        private var index = 0
        private var lastPos = Float.NaN
        private val lastValues = FloatArray(channelCount)
        private val tempValues = FloatArray(channelCount)

        fun process(values: FloatArray, emit: (FloatArray) -> Unit) {
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
        }

        companion object {
            private const val EPSILON = 0.002f

            fun resample(inputFormat: AudioFormat, input: ByteBuffer, outputFormat: AudioFormat, output: ByteBuffer) {
                input.order(ByteOrder.LITTLE_ENDIAN)
                output.order(ByteOrder.LITTLE_ENDIAN)

                val resampler = Resampler(inputFormat.sampleRate, outputFormat.sampleRate, inputFormat.channelCount)
                val readFrame = inputFormat.newFrameReader()
                val writeFrame = outputFormat.newFrameWriter()

                try {
                    while (true) {
                        resampler.process(input.readFrame()) {
                            output.writeFrame(it)
                        }
                    }
                } catch (e: RuntimeException) {
                    // BufferUnderflowException / BufferOverflowException
                }
            }

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