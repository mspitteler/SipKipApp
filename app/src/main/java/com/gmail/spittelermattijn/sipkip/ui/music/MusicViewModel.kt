package com.gmail.spittelermattijn.sipkip.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val _texts = MutableLiveData<List<String>>().apply {
        value = (1..16).mapIndexed { _, i ->
            "This is item # $i"
        }
    }

    val texts: LiveData<List<String>> = _texts
}