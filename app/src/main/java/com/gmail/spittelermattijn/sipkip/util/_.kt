package com.gmail.spittelermattijn.sipkip.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val secondaryCoroutineScope = CoroutineScope(SupervisorJob())

fun runInSecondaryScope(block: CoroutineScope.(CoroutineScope) -> Unit) {
    val primaryScope = CoroutineScope(Dispatchers.Main)
    secondaryCoroutineScope.launch { synchronized(secondaryCoroutineScope) { block(primaryScope) } }
}