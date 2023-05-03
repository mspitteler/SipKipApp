package com.gmail.spittelermattijn.sipkip.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.Constants
import com.gmail.spittelermattijn.sipkip.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.reflect.KFunction1
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    // This property is only valid between onServiceConnected
    // onServiceDisconnected.
    var writeCallback: KFunction1<ByteArray, Unit>? = null

    private val _texts = MutableLiveData<List<String>>().apply {
        value = (1..16).mapIndexed { _, i ->
            "This is item # $i"
        }
    }

    val texts: LiveData<List<String>> = _texts

    fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        for (data in datas!!) {
            writeCallback?.let {
                if (data!![data.size - 2].toInt().toChar() == '/')
                    updateFileList(it, String(data))
            }
            println(String(data!!))
        }
    }

    init {
        coroutineScope.launch { while (true) {
            writeCallback?.let { updateFileList(it, "/") }
            delay(Constants.BLUETOOTH_GET_DEVICE_FILES_DELAY.toDuration(DurationUnit.SECONDS))
        }}
    }

    private companion object {
        private fun write(callback: KFunction1<ByteArray, Unit>, data: ByteArray) {
            // We'll catch it later when the read in SerialService fails.
            try { callback(data) } catch (ignored: Exception) {}
        }

        private fun updateFileList(callback: KFunction1<ByteArray, Unit>, path: String) {
            write(callback, "ls /littlefs/$path".toByteArray())
        }
    }
}