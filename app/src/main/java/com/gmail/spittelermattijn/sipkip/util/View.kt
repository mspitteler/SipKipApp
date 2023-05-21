package com.gmail.spittelermattijn.sipkip.util

import android.view.View
import android.view.ViewParent

val View.grandParent: ViewParent get() = parent.parent