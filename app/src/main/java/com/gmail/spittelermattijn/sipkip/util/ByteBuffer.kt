package com.gmail.spittelermattijn.sipkip.util

import java.nio.ByteBuffer

fun ByteBuffer.getByteToFloat(): Float = get().toFloat() / Byte.MAX_VALUE
fun ByteBuffer.getShortToFloat(): Float = short.toFloat() / Short.MAX_VALUE
fun ByteBuffer.getIntToFloat(): Float = int.toFloat() / Int.MAX_VALUE

fun ByteBuffer.putFloatToByte(value: Float): ByteBuffer = put((value * Byte.MAX_VALUE).toInt().toByte())
fun ByteBuffer.putFloatToShort(value: Float): ByteBuffer = putShort((value * Short.MAX_VALUE).toInt().toShort())
fun ByteBuffer.putFloatToInt(value: Float): ByteBuffer = putInt((value * Int.MAX_VALUE).toInt())