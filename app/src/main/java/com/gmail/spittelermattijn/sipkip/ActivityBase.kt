package com.gmail.spittelermattijn.sipkip

import android.os.Bundle
import android.os.PersistableBundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity

abstract class ActivityBase : AppCompatActivity() {
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        Preferences.addContextGetter { this }
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Preferences.addContextGetter { this }
    }

    @CallSuper
    override fun onDestroy() {
        Preferences.removeContextGetter(this)
        super.onDestroy()
    }
}