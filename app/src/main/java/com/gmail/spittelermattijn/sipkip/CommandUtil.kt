package com.gmail.spittelermattijn.sipkip

import android.os.Handler
import android.os.Looper
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KFunction1

object CommandUtil {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private val results: ArrayList<ByteArray?> = ArrayList()
    private var resultsReceived = BooleanArray(UByte.MAX_VALUE.toInt() + 1) { false }
    private var resultsReceivedIndex = 0.toUByte()
    private var handler = Handler(Looper.getMainLooper())

    private fun <T> KFunction1<T, Unit>.noException(data: T) {
        // We'll catch it later when the read in SerialService fails.
        try { this(data) } catch (ignored: Exception) {}
    }

    fun signalCommandExecutionResultsReceived(index: UByte) = with(lock) {
        if (!resultsReceived[index.toInt()]) {
            handler.post {
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
    }

    fun addCommandExecutionResults(datas: ArrayDeque<ByteArray?>): UByte = with(lock) {
        handler.post { lock() }
        results.addAll(datas)
        return resultsReceivedIndex
    }

    fun blockingCommand(cb: KFunction1<ByteArray, Unit>, command: String, longTimeout: Boolean = false): List<ByteArray?> = with(lock) {
        val ret: ArrayList<ByteArray?>
        lock()
        cb.noException(command.toByteArray())
        resultsReceived[(++resultsReceivedIndex + 1.toUByte()).toUByte().toInt()] = false

        // Use Dispatchers.Main here to make sure signalCommandExecutionResultsReceived gets run on the same thread.
        val index = resultsReceivedIndex
        handler.post { lock() }
        handler.postDelayed({ signalCommandExecutionResultsReceived(index) }, (
                if (longTimeout)
                    Constants.DEFAULT_BLUETOOTH_COMMAND_LONG_TIMEOUT
                else
                    Constants.DEFAULT_BLUETOOTH_COMMAND_TIMEOUT
                ).toLong())

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