package com.gmail.spittelermattijn.sipkip.util

import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import androidx.annotation.AnyRes
import androidx.core.graphics.toColor
import kotlin.reflect.KClass

inline fun <reified T : Any> Resources.getAny(@AnyRes id: Int) = mapOf<KClass<*>, (@AnyRes Int) -> Any>(
    Color::class to { getColor(it, null).toColor() },
    String::class to ::getString, Boolean::class to ::getBoolean, Int::class to ::getInteger,
    Float::class to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { ::getFloat } else {{ getString(it).toFloat() }}),
)[T::class]!!(id) as T