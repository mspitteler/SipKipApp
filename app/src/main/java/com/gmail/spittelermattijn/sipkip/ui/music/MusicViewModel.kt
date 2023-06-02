package com.gmail.spittelermattijn.sipkip.ui.music

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.serial.SerialCommand
import com.gmail.spittelermattijn.sipkip.R
import com.gmail.spittelermattijn.sipkip.ui.ViewModelBase
import com.gmail.spittelermattijn.sipkip.util.coroutineScope
import com.gmail.spittelermattijn.sipkip.util.filterValidOpusPaths
import kotlinx.coroutines.launch
import kotlin.properties.Delegates
import kotlin.reflect.KFunction1

class MusicViewModel(application: Application) : ViewModelBase(application) {
    override val littleFsPath = "/music"
    // This property is only set to a valid callback between onSerialConnect() and disconnect().
    override var serialWriteCallback by Delegates.observable(null as KFunction1<ByteArray, Unit>?) { _, _, new ->
        coroutineScope.launch { synchronized(getApplication<Application>().applicationContext) {
            // Wait for first prompt by sending empty command.
            // TODO: Check why this rarely doesn't work.
            new?.let { SerialCommand(it, "\n").executeBlocking() }
            update()
        }}
    }

    private enum class FileType { File, Directory }
    private data class File(val type: FileType, val name: String)
    private val exploredPaths: ArrayList<File> = ArrayList()
    private var executionFailed = false

    data class Item(@DrawableRes val drawable: Int, val displayPath: String, val fullPath: String)

    private val _items: MutableLiveData<List<Item>> = MutableLiveData()
    val items: LiveData<List<Item>> = _items

    private fun ArrayList<File>.update(cb: KFunction1<ByteArray, Unit>, path: String) {
        val pathSlash = "$path${if (path.last() == '/') "" else "/"}"
        val results = SerialCommand(cb, "ls /littlefs/$pathSlash\n").executeBlocking()
        for (result in results.map { String(it!!) }) {
            // One result might contain multiple lines, and don't do anything with the prompt.
            for (line in result.split('\n').filter { !(it matches Regexes.PROMPT) }) {
                val newPath = "$pathSlash$line"
                // If line has trailing slash
                if (line matches Regexes.DIRECTORY) { // Directory
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
        // Only display regular files.
        val opusPaths = exploredPaths.filter { it.type == FileType.File }.map { it.name }.filterValidOpusPaths()

        _items.postValue(opusPaths.indices.mapIndexed { _, i ->
            val path = opusPaths[i].removePrefix(littleFsPath)
            val firstDirectory = path.split('/').first { it.isNotEmpty() }
            Item(when (firstDirectory) {
                "star_clip" -> R.drawable.ic_purple_star_button
                "triangle_clip" -> R.drawable.ic_red_triangle_button
                "square_clip" -> R.drawable.ic_blue_square_button
                "heart_clip" -> R.drawable.ic_yellow_heart_button
                "beak_switch" -> R.drawable.ic_beak_switch
                else -> R.drawable.ic_unknown_grey
            }, path.removePrefix("/$firstDirectory/"), opusPaths[i])
        })
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        if (datas == null)
            return
        SerialCommand.executionInstance?.postExecutionResults(datas)
        val lines = datas.map { String(it!!).split('\n') }.flatten()
        // It's okay to do this general of a check, because we would never upload a filename that contains spaces anyway.
        if (lines.any { line -> setOf("Invalid ", "Failed ", "Couldn't ").any { line.startsWith(it) } || line matches Regexes.COMMAND_ERROR })
            executionFailed = true

        if (lines.last { it.isNotEmpty() } matches Regexes.PROMPT) {
            SerialCommand.executionInstance?.postAllExecutionResultsReceived(executionFailed)
            executionFailed = false
        }
    }

    fun removeItem(fullPath: String) {
        serialWriteCallback?.let {
            SerialCommand(it, "rm /littlefs/$fullPath.opus\n").executeBlocking()
            SerialCommand(it, "rm /littlefs/$fullPath.opus_packets\n").executeBlocking()
        }
    }

    // TODO: Check if the destination directory exists in these functions.
    fun renameItem(fullPath: String, newFullPath: String) {
        serialWriteCallback?.let {
            SerialCommand(it, "mv /littlefs/$fullPath.opus /littlefs/$newFullPath.opus\n").executeBlocking(true)
            SerialCommand(it, "mv /littlefs/$fullPath.opus_packets /littlefs/$newFullPath.opus_packets\n").executeBlocking(true)
        }
    }

    fun changeItemFirstDirectory(fullPath: String, firstDirectory: String) {
        val path = fullPath.removePrefix(littleFsPath)
        val newFullPath = "$littleFsPath/${path.replaceFirst("[^/]+/".toRegex(), "$firstDirectory/")}"
        renameItem(fullPath, newFullPath)
    }

    fun playItem(fullPath: String) {
        serialWriteCallback?.let {
            SerialCommand(it, "speak /littlefs/$fullPath.opus /littlefs/$fullPath.opus_packets\n").executeBlocking(true)
        }
    }

    fun update() {
        exploredPaths.clear(littleFsPath)
        serialWriteCallback?.let { exploredPaths.update(it, littleFsPath) }
        updateLiveData()
    }

    private object Regexes {
        val PROMPT = """\d+@SipKip > \s*""".toRegex()
        val COMMAND_ERROR = """(Unknown command: .*!)|(Command .* error: .*!)""".toRegex()
        val DIRECTORY = """[^/]+/\s*""".toRegex()
    }
}