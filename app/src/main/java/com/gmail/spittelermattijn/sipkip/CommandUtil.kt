package com.gmail.spittelermattijn.sipkip

import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KFunction1

object CommandUtil {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private val results: ArrayList<ByteArray?> = ArrayList()
    private var resultsReceived = BooleanArray(UByte.MAX_VALUE.toInt() + 1) { false }
    private var resultsReceivedIndex = 0.toUByte()

    private fun write(cb: KFunction1<ByteArray, Unit>, data: ByteArray) {
        // We'll catch it later when the read in SerialService fails.
        try { cb(data) } catch (ignored: Exception) {}
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
        lock()
        results.addAll(datas)
        return resultsReceivedIndex
    }

    fun blockingCommand(cb: KFunction1<ByteArray, Unit>, command: String): List<ByteArray?> = with(lock) {
        val ret: ArrayList<ByteArray?>
        lock()
        write(cb, command.toByteArray())
        resultsReceived[(++resultsReceivedIndex + 1.toUByte()).toUByte().toInt()] = false
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