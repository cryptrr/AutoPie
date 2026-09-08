package com.autopi.autopieapp.data.services

import com.google.gson.JsonSyntaxException
import org.junit.Assert.*
import org.junit.Test

class ShareCommandRequestTest {
    private val command = """{"name":"test","command":"echo ok","type":"SHARE"}"""

    @Test fun omittedAndJsonNullOptionalInputsAreEmptyLists() {
        for (value in listOf(null, "null", "[]")) {
            val request = parseShareCommandRequest(command, "text", value, value)
            assertEquals(emptyList<String>(), request.inputFiles)
            assertTrue(request.extras.isEmpty())
            assertEquals("text", request.inputText)
        }
    }

    @Test fun preservesSpacesQuotesAndMultipleInputPaths() {
        val request = parseShareCommandRequest(command, null, """["/tmp/a b","/tmp/c'd"]""", null)
        assertEquals(listOf("/tmp/a b", "/tmp/c'd"), request.inputFiles)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingCommandFailsBeforeExecution() {
        parseShareCommandRequest(null, null, null, null)
    }

    @Test(expected = JsonSyntaxException::class)
    fun malformedExtrasAreNotSilentlyReplacedWithDefaults() {
        parseShareCommandRequest(command, null, null, "{")
    }

    @Test(expected = IllegalArgumentException::class)
    fun nullFileEntryIsRejected() {
        parseShareCommandRequest(command, null, "[null]", null)
    }
}
