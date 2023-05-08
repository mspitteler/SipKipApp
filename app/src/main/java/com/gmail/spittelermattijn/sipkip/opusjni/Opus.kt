package com.gmail.spittelermattijn.sipkip.opusjni

class Opus {
    private val handle = 0L
    external fun open(channels: Int, sampleRate: Int, bitrate: Int)
    external fun encode(buf: ShortArray?, pos: Int, len: Int): ByteArray?
    external fun encodeFloat(buf: FloatArray?, pos: Int, len: Int): ByteArray?
    external fun close()

    private companion object {
        init {
            System.loadLibrary("opusjni")
        }
    }
}