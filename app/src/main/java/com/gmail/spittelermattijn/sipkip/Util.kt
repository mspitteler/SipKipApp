package com.gmail.spittelermattijn.sipkip

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewParent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.nio.ByteBuffer

val coroutineScope = CoroutineScope(SupervisorJob())

val View.grandParent: ViewParent get() = parent.parent

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)!!
    else -> @Suppress("Deprecation") (getParcelableExtra(key) as T?)!!
}

fun ContentResolver.queryName(uri: Uri): String {
    val returnCursor = query(uri, null, null, null, null)!!
    val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    returnCursor.moveToFirst()
    val name = returnCursor.getString(nameIndex)
    returnCursor.close()
    return name
}

fun ByteBuffer.getByteToFloat(): Float = get().toFloat() / Byte.MAX_VALUE
fun ByteBuffer.getShortToFloat(): Float = short.toFloat() / Short.MAX_VALUE
fun ByteBuffer.getIntToFloat(): Float = int.toFloat() / Int.MAX_VALUE

fun ByteBuffer.putFloatToByte(value: Float) = put((value * Byte.MAX_VALUE).toInt().toByte())
fun ByteBuffer.putFloatToShort(value: Float) = putShort((value * Short.MAX_VALUE).toInt().toShort())
fun ByteBuffer.putFloatToInt(value: Float) = putInt((value * Int.MAX_VALUE).toInt())