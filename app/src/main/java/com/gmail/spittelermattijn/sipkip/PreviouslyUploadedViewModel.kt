package com.gmail.spittelermattijn.sipkip

import android.app.Application
import android.icu.text.DateFormat
import androidx.annotation.DrawableRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.util.filterValidOpusPaths
import java.io.File
import kotlin.math.max

class PreviouslyUploadedViewModel(application: Application) : AndroidViewModel(application) {
    data class Item(@DrawableRes val drawable: Int, val path: String, val lastModified: String)

    private val _items = MutableLiveData<List<Item>>().apply {
        val dateTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        val filesDir = getApplication<Application>().filesDir
        value = filesDir.listFiles()?.map { it.name }?.filterValidOpusPaths()?.map {
            Item(R.drawable.ic_note, it, dateTime.format(
                max(File("$filesDir/$it.opus").lastModified(), File("$filesDir/$it.opus_packets").lastModified())
            ))
        }
    }
    val items: LiveData<List<Item>> = _items
}