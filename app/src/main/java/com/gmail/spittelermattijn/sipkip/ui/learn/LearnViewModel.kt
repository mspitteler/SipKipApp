package com.gmail.spittelermattijn.sipkip.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class LearnViewModel(application: Application) : AndroidViewModel(application) {

    private val _text = MutableLiveData<String>().apply {
        value = "This is learn Fragment"
    }
    val text: LiveData<String> = _text
}