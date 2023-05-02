package com.gmail.spittelermattijn.sipkip.ui.finddevice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.R

class FindDeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val _text = MutableLiveData<String>().apply {
        value = application.resources.getString(R.string.waiting_for_device)
    }

    private val _progress = MutableLiveData<Int>().apply {
        value = 0
    }
    val text: LiveData<String> = _text
    val progress: LiveData<Int> = _progress
}