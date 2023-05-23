package com.gmail.spittelermattijn.sipkip.ui.music

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.SerialCommand
import com.gmail.spittelermattijn.sipkip.R
import com.gmail.spittelermattijn.sipkip.coroutineScope
import com.gmail.spittelermattijn.sipkip.ui.ViewModelBase
import kotlinx.coroutines.launch
import kotlin.reflect.KFunction1

class MusicViewModel(application: Application) : ViewModelBase(application) {
    override val littleFsPath = "/music"
    // This property is only set to a valid callback between onSerialConnect() and disconnect().
    override var serialWriteCallback: KFunction1<ByteArray, Unit>? = null
        set(cb) {
            field = cb
            coroutineScope.launch {
                synchronized(getApplication<Application>().applicationContext) {
                    // Wait for first prompt by sending empty command.
                    field?.let { SerialCommand(it, "\n").executeBlocking() }
                    update()
                }
            }
        }

    private enum class FileType { File, Directory }
    private data class File(val type: FileType, val name: String)
    private val exploredPaths: ArrayList<File> = ArrayList()
    private val promptRegex = """\d+@SipKip > \s*""".toRegex()

    data class Item(@DrawableRes val drawable: Int, val displayPath: String, val fullPath: String)

    private val _texts: MutableLiveData<List<Item>> = MutableLiveData()
    val texts: LiveData<List<Item>> = _texts

    private fun ArrayList<File>.update(cb: KFunction1<ByteArray, Unit>, path: String) {
        val pathSlash = "$path${if (path.last() == '/') "" else "/"}"
        val results = SerialCommand(cb, "ls /littlefs/$pathSlash\n").executeBlocking()
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
        SerialCommand.executionInstance?.postExecutionResults(datas!!)

        if (datas!!.any { data -> String(data!!).split('\n').last { it.isNotEmpty() } matches promptRegex })
            SerialCommand.executionInstance?.postAllExecutionResultsReceived()
    }

    fun removeItem(fullPath: String) {
        serialWriteCallback?.let {
            SerialCommand(it, "rm /littlefs/$fullPath.opus\n").executeBlocking()
            SerialCommand(it, "rm /littlefs/$fullPath.opus_packets\n").executeBlocking()
        }
    }

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

    fun update() {
        exploredPaths.clear(littleFsPath)
        serialWriteCallback?.let { exploredPaths.update(it, littleFsPath) }
        updateLiveData()
    }
}