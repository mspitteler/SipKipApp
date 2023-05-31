package com.gmail.spittelermattijn.sipkip.util

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)!!
    else -> @Suppress("Deprecation") (getParcelableExtra(key) as T?)!!
}

@Suppress("Unused")
val Intent.ACTION_MUSIC_PLAYER get() = "android.intent.action.MUSIC_PLAYER"

fun Intent.getUriWithType(typePrefix: String): Uri? {
    var uri: Uri? = null
    if (type?.startsWith(typePrefix) == true) {
        if (action == Intent.ACTION_SEND)
            uri = parcelable(Intent.EXTRA_STREAM)
        else if (scheme == "content")
            uri = data

        var itemCount = clipData?.itemCount ?: 0
        while (itemCount > 0 && (uri == null || uri.scheme == "file")) { // We don't have access to the internal storage.
            itemCount--
            uri = clipData?.getItemAt(itemCount)?.uri
        }
        if (uri?.scheme == "file")
            uri = null
    }
    return uri
}

val Intent.actionIsShared: Boolean
    get() = action in setOf(Intent.ACTION_SEND, Intent.ACTION_VIEW, Intent().ACTION_MUSIC_PLAYER)