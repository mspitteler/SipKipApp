package com.gmail.spittelermattijn.sipkip.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.Constants
import com.gmail.spittelermattijn.sipkip.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KFunction1
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    // This property is only set to a valid callback between onSerialConnect() and disconnect().
    var writeCallback: KFunction1<ByteArray, Unit>? = null

    private enum class FileType { File, Directory }
    private data class File(val type: FileType, val name: String)
    private val exploredPaths: ArrayList<File> = ArrayList()

    private val _texts = MutableLiveData<List<String>>().apply {
        value = (1..30).mapIndexed { _, i ->
            "This is item # $i"
        }
    }

    val texts: LiveData<List<String>> = _texts

    private fun ArrayList<File>.update(callback: KFunction1<ByteArray, Unit>, path: String) {
        val pathSlash = "$path${if (path.last() == '/') "" else "/"}"
        val results = blockingCommand(callback, "ls /littlefs/$pathSlash")
        for (result in results.map { String(it!!) }) {
            // One result might contain multiple lines, and don't do anything with the prompt.
            for (line in result.split('\n').filter { !(it matches """\d+@${Constants.BLUETOOTH_DEVICE_NAME} > \s*""".toRegex()) }) {
                val newPath = "$pathSlash$line"
                // If line has trailing slash
                if (line matches """[^/]+/\s*""".toRegex()) { // Directory
                    println("Adding directory: $newPath")
                    add(File(FileType.Directory, newPath))
                    update(callback, newPath)
                } else if (line.isNotEmpty()) { // Regular file
                    println("Adding file: $newPath")
                    add(File(FileType.File, newPath))
                }
            }
        }
    }

    private fun ArrayList<File>.clear(path: String) {
        for (i in indices.reversed()) {
            if (this[i].name matches """$path${if (path.last() == '/') "" else "/"}.*""".toRegex())
                removeAt(i)
        }
    }

    private fun updateLiveData() {
        if (exploredPaths.isEmpty())
            return
        _texts.postValue(exploredPaths.indices.mapIndexed { _, i ->
            exploredPaths[i].name
        })
    }

    fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        commandExecutionLock.lock()
        commandExecutionResults.addAll(datas!!)
        if (commandFirstRead) {
            commandFirstRead = false
            commandExecutionLock.unlock()

            coroutineScope.launch {
                delay(500.toDuration(DurationUnit.MILLISECONDS))
                with(commandExecutionLock) {
                    lock()
                    try {
                        commandExecutionResultsReceived = true
                        commandExecutionCondition.signal()
                    } finally {
                        unlock()
                    }
                }
            }
        } else {
            commandExecutionLock.unlock()
        }
    }

    // TODO Make this a service or something
    init {
        coroutineScope.launch { while (true) {
            exploredPaths.clear("/music")
            writeCallback?.let { exploredPaths.update(it, "/music") }
            updateLiveData()
            delay(Constants.BLUETOOTH_GET_DEVICE_FILES_DELAY.toDuration(DurationUnit.SECONDS))
        }}
    }

    private companion object {
        private val commandExecutionLock = ReentrantLock()
        private val commandExecutionCondition = commandExecutionLock.newCondition()

        private var commandFirstRead = true
        private var commandExecutionResults: ArrayList<ByteArray?> = ArrayList()
        private var commandExecutionResultsReceived = false

        private fun write(callback: KFunction1<ByteArray, Unit>, data: ByteArray) {
            // We'll catch it later when the read in SerialService fails.
            try { callback(data) } catch (ignored: Exception) {}
        }

        private fun blockingCommand(callback: KFunction1<ByteArray, Unit>, command: String): List<ByteArray?> {
            write(callback, command.toByteArray())
            val results: ArrayList<ByteArray?>
            with(commandExecutionLock) {
                lock()
                commandFirstRead = true
                try {
                    commandExecutionResultsReceived = false
                    while (!commandExecutionResultsReceived)
                        commandExecutionCondition.await()
                } finally {
                    results = ArrayList(commandExecutionResults)
                    commandExecutionResults.clear()
                    unlock()
                }
            }

            return results
        }
    }
}