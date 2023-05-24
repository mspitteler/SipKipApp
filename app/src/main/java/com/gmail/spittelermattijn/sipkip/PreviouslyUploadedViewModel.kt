package com.gmail.spittelermattijn.sipkip

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.util.filterValidOpusPaths

class PreviouslyUploadedViewModel(application: Application) : AndroidViewModel(application) {
    data class Item(@DrawableRes val drawable: Int, val path: String)

    private val _items = MutableLiveData<List<Item>>().apply {
        value = getApplication<Application>().filesDir.listFiles()
            ?.map { it.name }
            ?.filterValidOpusPaths()
            ?.map { Item(R.drawable.ic_note, it) }
    }
    val items: LiveData<List<Item>> = _items
}