package com.gmail.spittelermattijn.sipkip

import android.content.ContentResolver
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewParent
import android.widget.EditText
import androidx.annotation.AnyRes
import androidx.core.graphics.toColor
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import java.nio.ByteBuffer

val coroutineScope = CoroutineScope(SupervisorJob())

val View.grandParent: ViewParent get() = parent.parent

inline fun <reified T : Any> String.toComparable() = mapOf<KClass<*>, () -> Comparable<*>>(
    String::class to ::toString, Boolean::class to ::toBoolean, Float::class to ::toFloat, Double::class to ::toDouble,
    Byte::class to ::toByte, Short::class to ::toShort, Int::class to ::toInt, Long::class to ::toLong,
    UByte::class to ::toUByte, UShort::class to ::toUShort, UInt::class to ::toUInt, ULong::class to ::toULong
)[T::class]?.invoke()

inline fun <reified T : Any> Resources.getAny(@AnyRes id: Int) = mapOf<KClass<*>, (@AnyRes Int) -> Any>(
    Color::class to { getColor(it, null).toColor() },
    String::class to ::getString, Boolean::class to ::getBoolean, Int::class to ::getInteger,
    Float::class to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { ::getFloat } else {{ getString(it).toFloat() }}),
)[T::class]!!(id) as T

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

fun MaterialAlertDialogBuilder.showFirstDirectoryPicker(default: String? = null, cb: (String) -> Unit) {
    val items = arrayOf(
        R.string.first_directory_star_clip to "star_clip", R.string.first_directory_triangle_clip to "triangle_clip",
        R.string.first_directory_square_clip to "square_clip", R.string.first_directory_heart_clip to "heart_clip",
        R.string.first_directory_beak_switch to "beak_switch"
    )
    var checkedItem = items.map { it.second }.indexOf(default)
    checkedItem = if (checkedItem == -1) 0 else checkedItem
    setTitle(R.string.dialog_pick_first_directory)
        .setSingleChoiceItems(items.map { context.getString(it.first) }.toTypedArray(), checkedItem) { dialog, which -> checkedItem = which }
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { dialog, which -> cb(items[checkedItem].second) }
        .show()
}

fun MaterialAlertDialogBuilder.showRenameEditText(default: String? = null, cb: (String) -> Unit) {
    val editText = EditText(context).apply { setText(default) }
    setTitle(R.string.dialog_enter_path)
        .setView(editText)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { dialog, which ->
            val text = if (editText.text.isNullOrEmpty()) "default" else editText.text
            cb(text.replace("""\s""".toRegex(), "_"))
        }
        .show()
}