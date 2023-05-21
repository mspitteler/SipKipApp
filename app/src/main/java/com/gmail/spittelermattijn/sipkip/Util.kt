package com.gmail.spittelermattijn.sipkip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

val coroutineScope = CoroutineScope(SupervisorJob())