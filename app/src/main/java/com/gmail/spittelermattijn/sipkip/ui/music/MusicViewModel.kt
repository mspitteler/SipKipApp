package com.gmail.spittelermattijn.sipkip.ui.music

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmail.spittelermattijn.sipkip.Preferences
import com.gmail.spittelermattijn.sipkip.serial.SerialCommand
import com.gmail.spittelermattijn.sipkip.R
import com.gmail.spittelermattijn.sipkip.ui.ViewModelBase
import com.gmail.spittelermattijn.sipkip.util.filterValidOpusPaths
import com.gmail.spittelermattijn.sipkip.util.runInSecondaryScope
import kotlinx.coroutines.CoroutineScope
import kotlin.properties.Delegates
import kotlin.reflect.KFunction1
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class MusicViewModel(application: Application) : ViewModelBase(application) {
    override val littleFsPath = "/music"
    // This property is only set to a valid callback between onSerialConnect() and disconnect().
    override var serialWriteCallback by Delegates.observable(null as KFunction1<ByteArray, Unit>?) { _, _, new ->
        runInSecondaryScope { scope ->
            // Wait for first prompt by sending empty command.
            // TODO: Check why this rarely doesn't work.
            new?.let { SerialCommand(it, "\n").executeBlocking(DurationLength.Normal.toDuration(), scope) }
            update(scope)
        }
    }

    private enum class DurationLength { Normal, Long, Infinite }
    private enum class FileType { File, Directory }
    private data class File(val type: FileType, val name: String)
    private val exploredPaths: ArrayList<File> = ArrayList()
    private var executionFailed = false

    data class Item(@DrawableRes val drawable: Int, val displayPath: String, val fullPath: String)

    private val _items: MutableLiveData<List<Item>> = MutableLiveData()
    val items: LiveData<List<Item>> = _items

    private fun DurationLength.toDuration() = when (this) {
        DurationLength.Normal -> Preferences.get<Int>(R.string.bluetooth_command_timeout_key).toDuration(DurationUnit.MILLISECONDS)
        DurationLength.Long -> Preferences.get<Int>(R.string.bluetooth_command_long_timeout_key).toDuration(DurationUnit.MILLISECONDS)
        DurationLength.Infinite -> Duration.INFINITE
    }

    private fun ArrayList<File>.update(cb: KFunction1<ByteArray, Unit>, path: String, executionResultsScope: CoroutineScope) {
        val pathSlash = "$path${if (path.last() == '/') "" else "/"}"
        val command = SerialCommand(cb, "ls /littlefs/$pathSlash\n")
        val results = command.executeBlocking(DurationLength.Normal.toDuration(), executionResultsScope)
        for (result in results.map { String(it!!) }) {
            // One result might contain multiple lines, and don't do anything with the prompt.
            for (line in result.split('\n').filter { !(it matches Regexes.PROMPT) }) {
                val newPath = "$pathSlash$line"
                // If line has trailing slash
                if (line matches Regexes.DIRECTORY) { // Directory
                    println("Adding directory: $newPath")
                    add(File(FileType.Directory, newPath))
                    update(cb, newPath, executionResultsScope)
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
        SerialCommand.executionInstance?.addExecutionResults(datas)
        val lines = datas.map { String(it!!).split('\n') }.flatten()
        // It's okay to do this general of a check, because we would never upload a filename that contains spaces anyway.
        if (lines.any { line -> setOf("Invalid ", "Failed ", "Couldn't ").any { line.startsWith(it) } || line matches Regexes.COMMAND_ERROR })
            executionFailed = true

        if (lines.last { it.isNotEmpty() } matches Regexes.PROMPT) {
            SerialCommand.executionInstance?.setAllExecutionResultsReceived(executionFailed)
            executionFailed = false
        }
    }

    fun removeItem(fullPath: String, executionResultsScope: CoroutineScope) {
        serialWriteCallback?.let {
            var command = SerialCommand(it, "rm /littlefs/$fullPath.opus\n")
            command.executeBlocking(DurationLength.Normal.toDuration(), executionResultsScope)
            command = SerialCommand(it, "rm /littlefs/$fullPath.opus_packets\n")
            command.executeBlocking(DurationLength.Normal.toDuration(), executionResultsScope)
        }
    }

    // TODO: Check if the destination directory exists in these functions.
    fun renameItem(fullPath: String, newFullPath: String, executionResultsScope: CoroutineScope) {
        serialWriteCallback?.let {
            var command = SerialCommand(it, "mv /littlefs/$fullPath.opus /littlefs/$newFullPath.opus\n")
            command.executeBlocking(DurationLength.Long.toDuration(), executionResultsScope)
            command = SerialCommand(it, "mv /littlefs/$fullPath.opus_packets /littlefs/$newFullPath.opus_packets\n")
            command.executeBlocking(DurationLength.Long.toDuration(), executionResultsScope)
        }
    }

    fun changeItemFirstDirectory(fullPath: String, firstDirectory: String, executionResultsScope: CoroutineScope) {
        val path = fullPath.removePrefix(littleFsPath)
        val newFullPath = "$littleFsPath/${path.replaceFirst("[^/]+/".toRegex(), "$firstDirectory/")}"
        renameItem(fullPath, newFullPath, executionResultsScope)
    }

    fun playItem(fullPath: String, executionResultsScope: CoroutineScope) {
        serialWriteCallback?.let {
            val command = SerialCommand(it, "speak /littlefs/$fullPath.opus /littlefs/$fullPath.opus_packets\n")
            command.executeBlocking(DurationLength.Infinite.toDuration(), executionResultsScope)
        }
    }

    fun update(executionResultsScope: CoroutineScope) {
        exploredPaths.clear(littleFsPath)
        serialWriteCallback?.let { exploredPaths.update(it, littleFsPath, executionResultsScope) }
        updateLiveData()
    }

    private object Regexes {
        val PROMPT = """\d+@SipKip > \s*""".toRegex()
        val COMMAND_ERROR = """(Unknown command: .*!)|(Command .* error: .*!)""".toRegex()
        val DIRECTORY = """[^/]+/\s*""".toRegex()
    }
}