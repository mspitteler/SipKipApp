package com.gmail.spittelermattijn.sipkip

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import androidx.annotation.AnyRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.gmail.spittelermattijn.sipkip.util.getAny
import com.gmail.spittelermattijn.sipkip.util.toComparable

object Preferences {
    private val contextGetterList = ArrayList<() -> Context>()
    private val listeners = HashMap<Context, ArrayList<OnSharedPreferenceChangeListener>>()
    val topContextGetter get() = contextGetterList.last()

    fun addContextGetter(get: () -> Context) {
        contextGetterList.add(get)
        listeners[get()] = ArrayList()
    }

    fun removeContextGetter(context: Context) {
        contextGetterList.removeIf { it() == context }
        listeners[context]!!.forEach {
            PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(it)
        }
        listeners.remove(context)
    }

    // Only String, Boolean, Float and Int are supported as return types.
    inline operator fun <reified T : Any> get(@StringRes key: Int): T {
        val context = topContextGetter()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val keyString = context.getString(key)

        val resourceTypeMap = mapOf(String::class to "string", Boolean::class to "bool", Float::class to "dimen", Int::class to "integer", Color::class to "color")

        @SuppressLint("DiscouragedApi") @AnyRes val id = context.resources.getIdentifier("default_$keyString", resourceTypeMap[T::class], context.packageName)
        return preferences.all[keyString]?.let { if (it is String) it.toComparable() else (it as T) } ?: context.resources.getAny(id)
    }

    fun registerOnChangeListener(listener: (Int, Any) -> Unit) {
        val context = topContextGetter()
        val onChangeListener = OnSharedPreferenceChangeListener { sharedPreferences, key ->
            R.string::class.java.fields.filter {
                it.name.endsWith("_key") && topContextGetter().getString(it.getInt(null)) == key
            }.forEach { listener(it.getInt(null), sharedPreferences.all[key]!!) }
        }
        listeners[context]!!.add(onChangeListener)
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(onChangeListener)
    }
}