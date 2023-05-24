package com.gmail.spittelermattijn.sipkip.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

val coroutineScope = CoroutineScope(SupervisorJob())