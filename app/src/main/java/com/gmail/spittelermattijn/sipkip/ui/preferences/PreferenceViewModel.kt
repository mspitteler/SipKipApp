package com.gmail.spittelermattijn.sipkip.ui.preferences

import android.app.Application
import com.gmail.spittelermattijn.sipkip.ui.ViewModelBase
import kotlin.reflect.KFunction1

class PreferenceViewModel(application: Application) : ViewModelBase(application) {
    // This property is only set to a valid callback between onSerialConnect() and disconnect().
    override var serialWriteCallback: KFunction1<ByteArray, Unit>? = null

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
    }
}