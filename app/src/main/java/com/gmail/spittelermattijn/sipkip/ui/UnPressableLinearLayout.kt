package com.gmail.spittelermattijn.sipkip.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

class UnPressableLinearLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    override fun dispatchSetPressed(pressed: Boolean) {
        // Skip dispatching the pressed key state to the children so that they don't trigger any
        // pressed state animation on their stateful drawables.
    }
}