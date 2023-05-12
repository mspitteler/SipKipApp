package com.gmail.spittelermattijn.sipkip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlin.reflect.KFunction1

abstract class ViewModelBase(application: Application) : AndroidViewModel(application) {
    abstract fun onSerialRead(datas: ArrayDeque<ByteArray?>?)
    // This property is only set to a valid callback between onSerialConnect() and disconnect().
    abstract var serialWriteCallback: KFunction1<ByteArray, Unit>?
    open val littleFsPath: String? = null
}