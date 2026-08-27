package com.autopi.autopieapp.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvironmentExtraDefaultTest {
    @Test
    fun `double-dollar default is fetched from the shell environment`() = runTest {
        var requestedVariable: String? = null

        val resolved = resolveEnvironmentBackedValue("\$\$AUTOPIE_DEFAULT") { variableName ->
            requestedVariable = variableName
            "value-from-current-shell"
        }

        assertEquals("AUTOPIE_DEFAULT", requestedVariable)
        assertEquals("value-from-current-shell", resolved)
    }

    @Test
    fun `missing shell variable keeps the configured default`() = runTest {
        val resolved = resolveEnvironmentBackedValue("\$\$MISSING_VALUE") { null }

        assertEquals("\$\$MISSING_VALUE", resolved)
    }

    @Test
    fun `only a complete double-dollar reference is treated as an environment default`() {
        assertEquals("VALID_NAME_2", "\$\$VALID_NAME_2".environmentVariableReferenceOrNull())
        assertNull("prefix-\$\$VALID_NAME_2".environmentVariableReferenceOrNull())
        assertNull("\$\$2_INVALID".environmentVariableReferenceOrNull())
    }
}
