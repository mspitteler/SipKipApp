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
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

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
}
