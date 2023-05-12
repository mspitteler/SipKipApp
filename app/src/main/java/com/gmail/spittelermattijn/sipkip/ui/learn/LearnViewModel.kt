package com.gmail.spittelermattijn.sipkip.ui.learn

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.ui.ViewModelBase
import kotlin.reflect.KFunction1

class LearnViewModel(application: Application) : ViewModelBase(application) {
    override val littleFsPath = "/learn"
    // This property is only set to a valid callback between onSerialConnect() and disconnect().
    override var serialWriteCallback: KFunction1<ByteArray, Unit>? = null

    private val _text = MutableLiveData<String>().apply {
        value = "This is learn Fragment"
    }
    val text: LiveData<String> = _text

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {

    }
}