package com.gmail.spittelermattijn.sipkip.ui.play

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class PlayViewModel(application: Application) : AndroidViewModel(application) {

    private val _text = MutableLiveData<String>().apply {
        value = "This is play Fragment"
    }
    val text: LiveData<String> = _text
}