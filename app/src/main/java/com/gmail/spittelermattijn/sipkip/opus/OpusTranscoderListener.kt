package com.gmail.spittelermattijn.sipkip.opus

import java.io.OutputStream

interface OpusTranscoderListener {
    fun onTranscoderStarted(): Any?
    fun onTranscoderFinished(opusOutput: OutputStream, opusPacketsOutput: OutputStream, args: Any?)
}