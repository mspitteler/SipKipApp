package com.gmail.spittelermattijn.sipkip.serial

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KFunction1
import kotlin.time.Duration

class SerialCommand(private val cb: KFunction1<ByteArray, Unit>, private val command: String) {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private val results: ArrayList<ByteArray?> = ArrayList()
    private var allResultsReceived = false
    private var failed = false
    private val scope = CoroutineScope(Dispatchers.Main)

    init { executionInstance = null }

    private fun <T> KFunction1<T, Unit>.noException(data: T) {
        // We'll catch it later when the read in SerialService fails.
        try { this(data) } catch (ignored: Exception) {}
    }

    private fun CoroutineScope.launchIf(
        condition: Boolean, context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit
    ) {
        if (condition)
            launch(context = context, block = block)
    }

    fun postAllExecutionResultsReceived(fail: Boolean) = scope.launch { with(lock) {
        if (!allResultsReceived) {
            try {
                failed = fail
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

    fun executeBlocking(timeout: Duration) = with(lock) {
        lock()
        executionInstance = this@SerialCommand
        cb.noException(command.toByteArray())

        // Use Dispatchers.Main here to make sure signalCommandExecutionResultsReceived gets run on the same thread.
        scope.launchIf(timeout != Duration.INFINITE) {
            lock()
            delay(timeout)
            postAllExecutionResultsReceived(false)
        }

        try {
            while (!allResultsReceived)
                condition.await()
        } finally {
            executionInstance = null
            unlock()
        }
        if (failed) throw ExecutionFailedException(results.joinToString("\n") { String(it!!) }) else results
    }

    class ExecutionFailedException(message: String) : Exception(message)

    companion object { var executionInstance: SerialCommand? = null }
}