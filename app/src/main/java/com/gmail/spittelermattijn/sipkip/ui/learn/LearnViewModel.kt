package com.gmail.spittelermattijn.sipkip.ui.learn

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LearnViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is learn Fragment"
    }
    val text: LiveData<String> = _text
}