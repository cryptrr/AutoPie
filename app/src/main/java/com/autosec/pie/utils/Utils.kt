package com.autosec.pie.utils

import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class Utils{
    companion object{
        fun getRandomNumericalId(): String {
            val randomDigits = (1..6).map { Random.nextInt(10) }
            return randomDigits.joinToString("")
        }
        fun parseTimeInterval(input: String): Pair<Long, TimeUnit>? {
            if (input.length < 2) return null

            val timeValue = input.dropLast(1).toLongOrNull()
            val timeUnitChar = input.last()

            if (timeValue == null || timeValue <= 0) return null

            return when (timeUnitChar) {
                's' -> Pair(timeValue, TimeUnit.SECONDS)
                'm' -> Pair(timeValue, TimeUnit.MINUTES)
                'h' -> Pair(timeValue, TimeUnit.HOURS)
                else -> null  // Invalid time unit
            }
        }

        fun checkIfIntervalLessThan15Minutes(interval: Pair<Long, TimeUnit>): Boolean {
            val (timeValue, timeUnit) = interval
            val intervalInMillis = timeUnit.toMillis(timeValue)
            val fifteenMinutesInMillis = TimeUnit.MINUTES.toMillis(15)

            return intervalInMillis < fifteenMinutesInMillis
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