package com.gmail.spittelermattijn.sipkip.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

val Context.activity: Activity? get() =
    if (this is ContextWrapper)
        if (this is Activity) this else baseContext.activity
    else
        null