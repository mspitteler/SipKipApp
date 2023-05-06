package com.gmail.spittelermattijn.sipkip

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.view.View
import android.view.ViewParent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

val coroutineScope = CoroutineScope(SupervisorJob())

val View.grandParent: ViewParent get() = parent.parent

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)!!
    else -> @Suppress("Deprecation") (getParcelableExtra(key) as T?)!!
}