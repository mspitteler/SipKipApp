package com.gmail.spittelermattijn.sipkip.util

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent

@Suppress("Unused")
val View.grandParent: ViewParent get() = parent.parent
val View.parentViewGroup: ViewGroup get() = parent as ViewGroup