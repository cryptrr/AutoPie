package com.autopi.autopieApp.data

import com.autopi.autopieapp.data.resolveMultiSelectableDefaults
import com.autopi.autopieapp.data.toMultiSelectableValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiSelectableTest {
    private val options = linkedMapOf(
        "First" to "raw-one",
        "Second" to "raw-two",
        "Third" to "raw-three",
    )

    @Test
    fun `defaults accept labels and raw values separated by new lines`() {
        assertEquals(
            listOf("raw-one", "raw-two"),
            resolveMultiSelectableDefaults("First\nraw-two", options)
        )
    }

    @Test
    fun `empty default selects the first option like selectable`() {
        assertEquals(listOf("raw-one"), resolveMultiSelectableDefaults("", options))
    }

    @Test
    fun `selected raw values are joined with new lines`() {
        assertEquals("raw-one\nraw-three", listOf("raw-one", "raw-three").toMultiSelectableValue())
    }
}
