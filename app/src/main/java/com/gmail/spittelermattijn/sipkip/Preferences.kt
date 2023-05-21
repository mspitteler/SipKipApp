package com.gmail.spittelermattijn.sipkip

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import androidx.annotation.AnyRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager

object Preferences {
    var getContext: (() -> Context?) = { null }
    private val listeners = ArrayList<(Int, Any) -> Unit>()

    // Only String, Boolean, Float and Int are supported as return types.
    inline operator fun <reified T : Any> get(@StringRes key: Int): T {
        val context = getContext()!!
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val keyString = context.getString(key)

        val resourceTypeMap = mapOf(String::class to "string", Boolean::class to "bool", Float::class to "dimen", Int::class to "integer", Color::class to "color")

        @SuppressLint("DiscouragedApi") @AnyRes val id = context.resources.getIdentifier("default_$keyString", resourceTypeMap[T::class], context.packageName)
        return preferences.all[keyString]?.let { if (it is String) it.toComparable<T>() else it } as T? ?: context.resources.getAny(id)
    }

    fun registerOnChangeListener(listener: (Int, Any) -> Unit) {
        listeners.add(listener)
        val context = getContext()!!
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener { sharedPreferences, key ->
            R.string::class.java.fields.filter { it.name.endsWith("_key") && context.getString(it.getInt(null)) == key }.forEach {
                listener(it.getInt(null), sharedPreferences.all[key]!!)
            }
        }
    }
}