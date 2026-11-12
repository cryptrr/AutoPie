package com.autopi.use_case

import com.autopi.autopieapp.data.CommandModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetCommandTagsTest {
    @Test
    fun `only my tags hides suggestions and retains matching user tags`() = runTest {
        val useCases = mockk<AutoPieUseCases>()
        val getLatestUsedPackages = mockk<GetLatestUsedPackages>()
        val getUserTags = mockk<GetUserTags>()
        val userTags = MutableStateFlow(listOf("personal", "shared"))
        every { useCases.getLatestUsedPackages } returns getLatestUsedPackages
        every { useCases.getUserTags } returns getUserTags
        every { getLatestUsedPackages(3) } returns flowOf(listOf("recent", "shared"))
        every { getUserTags() } returns userTags

        val commands = flowOf(listOf(CommandModel(exec = "frequent"), CommandModel(exec = "shared")))
        val hideAutomaticTags = MutableStateFlow(false)
        val visibleTags = Channel<List<String>>()
        backgroundScope.launch {
            useCases.getCommandTags(commands, hideAutomaticTags).collect { visibleTags.send(it) }
        }

        assertEquals(listOf("frequent", "recent", "shared", "personal"), visibleTags.receive())

        hideAutomaticTags.value = true
        assertEquals(listOf("personal", "shared"), visibleTags.receive())

        userTags.value = listOf("personal")
        assertEquals(listOf("personal"), visibleTags.receive())

        hideAutomaticTags.value = false
        assertEquals(listOf("frequent", "recent", "shared", "personal"), visibleTags.receive())
    }
}
