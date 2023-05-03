package com.gmail.spittelermattijn.sipkip.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.coroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KFunction1

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    // This property is only valid between onServiceConnected
    // onServiceDisconnected.
    var writeCallback: KFunction1<ByteArray?, Unit>? = null

    fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        for (data in datas!!) {
            System.out.println(String(data!!))
        }
    }

    private val _texts = MutableLiveData<List<String>>().apply {
        value = (1..16).mapIndexed { _, i ->
            "This is item # $i"
        }
    }

    val texts: LiveData<List<String>> = _texts

    init {
        coroutineScope.launch {
            while (true) {
                writeCallback?.let { it("ls /littlefs".toByteArray()) }
                Thread.sleep(1_000)
            }
        }
    }
}