package com.gmail.spittelermattijn.sipkip

import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KFunction1

object CommandUtil {
    val commandExecutionLock = ReentrantLock()
    private val commandExecutionCondition = commandExecutionLock.newCondition()

    val commandExecutionResults: ArrayList<ByteArray?> = ArrayList()
    private var commandExecutionResultsReceived = BooleanArray(UByte.MAX_VALUE.toInt() + 1) { false }
    var commandExecutionResultsReceivedIndex = 0.toUByte()
        private set

    private fun write(callback: KFunction1<ByteArray, Unit>, data: ByteArray) {
        // We'll catch it later when the read in SerialService fails.
        try { callback(data) } catch (ignored: Exception) {}
    }

    fun signalCommandExecutionResultsReceived(index: UByte) {
        with(commandExecutionLock) {
            lock()
            if (!commandExecutionResultsReceived[index.toInt()]) {
                try {
                    commandExecutionResultsReceived[index.toInt()] = true
                    commandExecutionCondition.signal()
                } finally {
                    unlock()
                }
            } else {
                unlock()
            }
        }
    }

    fun blockingCommand(callback: KFunction1<ByteArray, Unit>, command: String): List<ByteArray?> {
        write(callback, command.toByteArray())
        val results: ArrayList<ByteArray?>
        with(commandExecutionLock) {
            lock()
            try {
                commandExecutionResultsReceived[(++commandExecutionResultsReceivedIndex + 1.toUByte()).toUByte().toInt()] = false
                while (!commandExecutionResultsReceived[commandExecutionResultsReceivedIndex.toInt()])
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