package com.gmail.spittelermattijn.sipkip.ui.music

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.CommandUtil
import com.gmail.spittelermattijn.sipkip.Constants
import com.gmail.spittelermattijn.sipkip.R
import com.gmail.spittelermattijn.sipkip.coroutineScope
import com.gmail.spittelermattijn.sipkip.ui.ViewModelBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.reflect.KFunction1
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class MusicViewModel(application: Application) : ViewModelBase(application) {
    override val littleFsPath = "/music"
    // This property is only set to a valid callback between onSerialConnect() and disconnect().
    override var serialWriteCallback: KFunction1<ByteArray, Unit>? = null

    private enum class FileType { File, Directory }
    private data class File(val type: FileType, val name: String)
    private val exploredPaths: ArrayList<File> = ArrayList()
    private val promptRegex = """\d+@${Constants.BLUETOOTH_DEVICE_NAME} > \s*""".toRegex()

    data class Item(@DrawableRes val drawable: Int, val displayPath: String, val fullPath: String)

    private val _texts: MutableLiveData<List<Item>> = MutableLiveData()
    val texts: LiveData<List<Item>> = _texts

    private fun ArrayList<File>.update(cb: KFunction1<ByteArray, Unit>, path: String) {
        val pathSlash = "$path${if (path.last() == '/') "" else "/"}"
        val results = CommandUtil.blockingCommand(cb, "ls /littlefs/$pathSlash")
        for (result in results.map { String(it!!) }) {
            // One result might contain multiple lines, and don't do anything with the prompt.
            for (line in result.split('\n').filter { !(it matches promptRegex) }) {
                val newPath = "$pathSlash$line"
                // If line has trailing slash
                if (line matches """[^/]+/\s*""".toRegex()) { // Directory
                    println("Adding directory: $newPath")
                    add(File(FileType.Directory, newPath))
                    update(cb, newPath)
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
        val paths = exploredPaths.filter { it.type == FileType.File } // Only display regular files.
        val opusPaths: ArrayList<String> = ArrayList()
        for (path in paths) {
            if (path.name matches """.+\.opus""".toRegex(RegexOption.IGNORE_CASE)) {
                // Do this substring dance, because then we preserve the case insensitivity of the Opus file extension.
                val pathWithoutExtension = path.name.substring(0 until path.name.length - ".opus".length)
                if (paths.any { it.name matches """.+\.opus_packets""".toRegex(RegexOption.IGNORE_CASE) &&
                            it.name.substring(0 until it.name.length - ".opus_packets".length) == pathWithoutExtension })
                    opusPaths.add(pathWithoutExtension)
            }
        }

        _texts.postValue(opusPaths.indices.mapIndexed { _, i ->
            val path = opusPaths[i].removePrefix(littleFsPath)
            val firstDirectory = path.split('/').first { it.isNotEmpty() }
            Item(when (firstDirectory) {
                "star_clip" -> R.drawable.ic_purple_star_button
                "triangle_clip" -> R.drawable.ic_red_triangle_button
                "square_clip" -> R.drawable.ic_blue_square_button
                "heart_clip" -> R.drawable.ic_yellow_heart_button
                "beak_switch" -> R.drawable.ic_beak_switch
                else -> R.drawable.ic_unknown
            }, path.removePrefix("/$firstDirectory/"), opusPaths[i])
        })
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        val index = CommandUtil.addCommandExecutionResults(datas!!)

        if (datas.any { data -> String(data!!).split('\n').last { it.isNotEmpty() } matches promptRegex })
            CommandUtil.signalCommandExecutionResultsReceived(index)

        coroutineScope.launch {
            delay(Constants.BLUETOOTH_COMMAND_TIMEOUT.toDuration(DurationUnit.MILLISECONDS))
            CommandUtil.signalCommandExecutionResultsReceived(index)
        }
    }

    fun removeItem(fullPath: String) {
        coroutineScope.launch {
            serialWriteCallback?.let {
                CommandUtil.blockingCommand(it, "rm /littlefs/$fullPath.opus")
                CommandUtil.blockingCommand(it, "rm /littlefs/$fullPath.opus_packets")
            }
        }
    }

    // TODO Make this a service or something
    init {
        coroutineScope.launch { while (true) {
            exploredPaths.clear(littleFsPath)
            serialWriteCallback?.let { exploredPaths.update(it, littleFsPath) }
            updateLiveData()
            delay(Constants.BLUETOOTH_GET_DEVICE_FILES_DELAY.toDuration(DurationUnit.SECONDS))
        }}
    }
}