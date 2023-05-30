package com.gmail.spittelermattijn.sipkip.serial

import com.gmail.spittelermattijn.sipkip.Preferences
import com.gmail.spittelermattijn.sipkip.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KFunction1
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// TODO: Check if command successfully executed on device.
class SerialCommand(private val cb: KFunction1<ByteArray, Unit>, private val command: String) {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private val results: ArrayList<ByteArray?> = ArrayList()
    private var allResultsReceived = false
    private val scope = CoroutineScope(Dispatchers.Main)

    init { executionInstance = null }

    private fun <T> KFunction1<T, Unit>.noException(data: T) {
        // We'll catch it later when the read in SerialService fails.
        try { this(data) } catch (ignored: Exception) {}
    }

    fun postAllExecutionResultsReceived() = scope.launch { with(lock) {
        if (!allResultsReceived) {
            try {
                allResultsReceived = true
                condition.signal()
            } finally {
                do {
                    /*
                     * TODO: Prevent a crash here if the executeBlocking() function is resumed after
                     *       the app was stopped temporarily for example.
                     */
                    unlock()
                } while (isLocked)
            }
        }
    }}

    fun postExecutionResults(datas: ArrayDeque<ByteArray?>) = scope.launch { with(lock) {
        lock()
        results.addAll(datas)
    }}

    suspend fun executeBlocking(longTimeout: Boolean = false) = with(lock) {
        lock()
        executionInstance = this@SerialCommand
        cb.noException(command.toByteArray())

        // Use Dispatchers.Main here to make sure signalCommandExecutionResultsReceived gets run on the same thread.
        scope.launch {
            lock()
            delay((if (longTimeout)
                Preferences.get<Int>(R.string.bluetooth_command_long_timeout_key)
            else
                Preferences[R.string.bluetooth_command_timeout_key]).toDuration(DurationUnit.MILLISECONDS))
            postAllExecutionResultsReceived()
        }

        try {
            while (!allResultsReceived)
                condition.await()
        } finally {
            executionInstance = null
            unlock()
        }
        results
    }

    companion object { var executionInstance: SerialCommand? = null }
}