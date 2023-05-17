package com.gmail.spittelermattijn.sipkip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KFunction1
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object CommandUtil {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private val results: ArrayList<ByteArray?> = ArrayList()
    private var resultsReceived = BooleanArray(UByte.MAX_VALUE.toInt() + 1) { false }
    private var resultsReceivedIndex = 0.toUByte()

    private fun <T> KFunction1<T, Unit>.noException(data: T) {
        // We'll catch it later when the read in SerialService fails.
        try { this(data) } catch (ignored: Exception) {}
    }

    fun signalCommandExecutionResultsReceived(index: UByte) = with(lock) {
        if (!resultsReceived[index.toInt()]) {
            try {
                resultsReceived[index.toInt()] = true
                condition.signal()
            } finally {
                do {
                    unlock()
                } while (isLocked)
            }
        }
    }

    fun addCommandExecutionResults(datas: ArrayDeque<ByteArray?>): UByte = with(lock) {
        CoroutineScope(Dispatchers.Main).launch { lock() }
        results.addAll(datas)
        return resultsReceivedIndex
    }

    fun blockingCommand(cb: KFunction1<ByteArray, Unit>, command: String): List<ByteArray?> = with(lock) {
        val ret: ArrayList<ByteArray?>
        lock()
        cb.noException(command.toByteArray())
        resultsReceived[(++resultsReceivedIndex + 1.toUByte()).toUByte().toInt()] = false

        // Use Dispatchers.Main here to make sure signalCommandExecutionResultsReceived gets run on the same thread.
        val index = resultsReceivedIndex
        CoroutineScope(Dispatchers.Main).launch {
            lock()
            delay(Constants.DEFAULT_BLUETOOTH_COMMAND_TIMEOUT.toDuration(DurationUnit.MILLISECONDS))
            signalCommandExecutionResultsReceived(index)
        }

        try {
            while (!resultsReceived[resultsReceivedIndex.toInt()])
                condition.await()
        } finally {
            ret = ArrayList(results)
            results.clear()
            unlock()
        }
        return ret
    }
}