package com.autopi.autopieApp.presentation.elements

import com.autopi.autopieapp.presentation.elements.toSliderValue
import org.junit.Assert.assertEquals
import org.junit.Test

class SliderSelectorTest {
    @Test
    fun `decimal slider values use at most two decimal places`() {
        assertEquals("1.23", 1.234f.toSliderValue(isInteger = false))
        assertEquals("1.2", 1.2f.toSliderValue(isInteger = false))
        assertEquals("2", 2f.toSliderValue(isInteger = false))
    }

    @Test
    fun `integer slider values remain whole numbers`() {
        assertEquals("2", 1.6f.toSliderValue(isInteger = true))
    }
}
