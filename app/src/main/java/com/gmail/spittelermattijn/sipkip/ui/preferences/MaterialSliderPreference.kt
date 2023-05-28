package com.gmail.spittelermattijn.sipkip.ui.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.preference.R
import com.google.android.material.slider.Slider
import kotlin.math.abs
import kotlin.properties.Delegates

class MaterialSliderPreference constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    Preference(context, attrs, defStyleAttr, defStyleRes) {
    var sliderValue = 0
    var slider: Slider? = null

    // Whether the Slider should respond to the left/right keys
    var isAdjustable by Delegates.notNull<Boolean>()

    // Whether the MaterialSliderPreference should continuously save the Slider value while it is being
    // dragged.
    private val onSliderChangeListener = Slider.OnChangeListener { slider, _, fromUser -> if (fromUser) syncValueInternal(slider) }
    private val sliderKeyListener: View.OnKeyListener = object : View.OnKeyListener {
        override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
            if (event.action != KeyEvent.ACTION_DOWN)
                return false
            if (!isAdjustable && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT))
                // Right or left keys are pressed when in non-adjustable mode; Skip the keys.
                return false
            // We don't want to propagate the click keys down to the Slider view since it will
            // create the ripple effect for the thumb.
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                return false
            if (slider == null) {
                Log.e(TAG, "Slider view is null and hence cannot be adjusted.")
                return false
            }
            return slider!!.onKeyDown(keyCode, event)
        }
    }

    var min = Int.MIN_VALUE
        set(m) {
            var min = m
            if (min > max) min = max
            if (min != field) {
                field = min
                notifyChanged()
            }
        }
    var sliderIncrement = 0
        set(increment) {
            if (increment != field) {
                field = (max - min).coerceAtMost(abs(increment))
                notifyChanged()
            }
        }
    var max = Int.MAX_VALUE
        set(m) {
            var max = m
            if (max < min) max = min
            if (max != field) {
                field = max
                notifyChanged()
            }
        }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SeekBarPreference, defStyleAttr, defStyleRes)
        // The ordering of these two statements are important. If we want to set max first, we need
        // to perform the same steps by changing min/max to max/min as following:
        // mMax = a.getInt(...) and setMin(...).
        @SuppressLint("PrivateResource")
        min = a.getInt(R.styleable.SeekBarPreference_min, 0)
        @SuppressLint("PrivateResource")
        max = a.getInt(R.styleable.SeekBarPreference_android_max, 100)
        @SuppressLint("PrivateResource")
        sliderIncrement = a.getInt(R.styleable.SeekBarPreference_seekBarIncrement, 0)
        @SuppressLint("PrivateResource")
        isAdjustable = a.getBoolean(R.styleable.SeekBarPreference_adjustable, true)
        a.recycle()
    }

    @Suppress("Unused")
    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.seekBarPreferenceStyle) : this(context, attrs, defStyleAttr, 0)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.setOnKeyListener(sliderKeyListener)
        slider = holder.findViewById(R.id.seekbar) as Slider
        if (slider == null) {
            Log.e(TAG, "Slider view is null in onBindViewHolder.")
            return
        }
        slider!!.addOnChangeListener(onSliderChangeListener)
        slider!!.valueTo = max.toFloat()
        slider!!.valueFrom = min.toFloat()
        // If the increment is not zero, use that. Otherwise, use the default mKeyProgressIncrement
        // in AbsSeekBar when it's zero. This default increment value is set by AbsSeekBar
        // after calling setMax. That's why it's important to call setKeyProgressIncrement after
        // calling setMax() since setMax() can change the increment value.
        if (sliderIncrement != 0)
            slider!!.stepSize = sliderIncrement.toFloat()
        else
            sliderIncrement = slider!!.stepSize.toInt()
        slider!!.value = sliderValue.toFloat()
        slider!!.isEnabled = isEnabled
    }

    override fun onSetInitialValue(value: Any?) {
        var defaultValue = value
        if (defaultValue == null)
            defaultValue = 0
        this.value = getPersistedInt((defaultValue as Int?)!!)
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = a.getInt(index, 0)

    private fun setValueInternal(value: Int, notifyChanged: Boolean) {
        var sliderValue = value
        if (sliderValue < min)
            sliderValue = min
        if (sliderValue > max)
            sliderValue = max
        if (sliderValue != this.sliderValue) {
            this.sliderValue = sliderValue
            persistInt(sliderValue)
            if (notifyChanged)
                notifyChanged()
        }
    }

    var value: Int
        get() = sliderValue
        set(sliderValue) = setValueInternal(sliderValue, true)

    private fun syncValueInternal(slider: Slider) {
        val value = slider.value.toInt()
        if (value != sliderValue) {
            if (callChangeListener(value))
                setValueInternal(value, false)
            else
                slider.value = sliderValue.toFloat()
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        if (isPersistent)
            // No need to save instance state since it's persistent
            return superState
        // Save the instance state
        val myState = SavedState(superState)
        myState.sliderValue = sliderValue
        myState.min = min
        myState.max = max
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state == null || state.javaClass != SavedState::class.java) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state)
            return
        }
        // Restore the instance state
        val myState = state as SavedState
        super.onRestoreInstanceState(myState.superState)
        sliderValue = myState.sliderValue
        min = myState.min
        max = myState.max
        notifyChanged()
    }

    private class SavedState : BaseSavedState {
        var sliderValue = 0
        var min = 0
        var max = 0

        constructor(source: Parcel) : super(source) {
            // Restore the click counter
            sliderValue = source.readInt()
            min = source.readInt()
            max = source.readInt()
        }

        constructor(superState: Parcelable?) : super(superState)

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            // Save the click counter
            dest.writeInt(sliderValue)
            dest.writeInt(min)
            dest.writeInt(max)
        }

        companion object {
            @Suppress("Unused")
            @JvmField
            val CREATOR: Creator<SavedState> = object : Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel) = SavedState(`in`)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    companion object {
        private const val TAG = "MaterialSliderPreference"
    }
}