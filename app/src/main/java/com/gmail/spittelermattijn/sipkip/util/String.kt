package com.gmail.spittelermattijn.sipkip.util

import kotlin.reflect.KClass

inline fun <reified T : Any> String.toComparable() = mapOf<KClass<*>, () -> Comparable<*>>(
    String::class to ::toString, Boolean::class to ::toBoolean, Float::class to ::toFloat, Double::class to ::toDouble,
    Byte::class to ::toByte, Short::class to ::toShort, Int::class to ::toInt, Long::class to ::toLong,
    UByte::class to ::toUByte, UShort::class to ::toUShort, UInt::class to ::toUInt, ULong::class to ::toULong
)[T::class]?.invoke() as T?