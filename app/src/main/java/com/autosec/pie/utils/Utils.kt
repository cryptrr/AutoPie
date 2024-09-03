package com.autosec.pie.utils

import kotlinx.coroutines.*
import kotlin.random.Random

class Utils{
    companion object{
        fun getRandomNumericalId(): String {
            val randomDigits = (1..6).map { Random.nextInt(10) }
            return randomDigits.joinToString("")
        }
    }
}

class Debouncer(
    private val coroutineScope: CoroutineScope,
    private val delayMillis: Long
) {
    private var debounceJob: Job? = null

    fun debounce(function: () -> Unit) {
        debounceJob?.cancel()  // Cancel any previously scheduled job
        debounceJob = coroutineScope.launch {
            delay(delayMillis)  // Wait for the specified delay
            function()  // Execute the function after the delay
        }
    }
}