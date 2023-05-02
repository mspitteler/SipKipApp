package com.gmail.spittelermattijn.sipkip.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _text = MutableLiveData<String>().apply {
        value = "This is settings Fragment"
    }
    val text: LiveData<String> = _text
}