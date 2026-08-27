package com.autopi.autopieapp.data.services

import com.autopi.autopieapp.data.ExtraFlags
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ExtraPathResolutionTest {
    private val externalStorage = File("/storage/emulated/0")

    @Test
    fun `relative file extra resolves from external storage`() {
        val result = resolveExtraPathValue(
            name = "SOURCE_FILE",
            type = "STRING",
            flags = emptyList(),
            value = "Download/source.txt",
            externalStorageRoot = externalStorage
        )

        assertEquals("/storage/emulated/0/Download/source.txt", result)
    }

    @Test
    fun `picker flags resolve paths even without a conventional extra name`() {
        val result = resolveExtraPathValue(
            name = "SOURCE",
            type = "STRING",
            flags = listOf(ExtraFlags.FOLDER_PICKER.value),
            value = "Documents/Project",
            externalStorageRoot = externalStorage
        )

        assertEquals("/storage/emulated/0/Documents/Project", result)
    }

    @Test
    fun `multiple paths resolve individually and preserve absolute paths`() {
        val result = resolveExtraPathValue(
            name = "INPUT_FILES",
            type = "STRING",
            flags = emptyList(),
            value = "Download/one.txt,/storage/emulated/0/Documents/two.txt",
            externalStorageRoot = externalStorage
        )

        assertEquals(
            "/storage/emulated/0/Download/one.txt,/storage/emulated/0/Documents/two.txt",
            result
        )
    }

    @Test
    fun `absolute folder path is unchanged`() {
        val result = resolveExtraPathValue(
            name = "OUTPUT_FOLDER",
            type = "STRING",
            flags = listOf(ExtraFlags.FOLDER_PICKER.value),
            value = "/data/local/tmp/output",
            externalStorageRoot = externalStorage
        )

        assertEquals("/data/local/tmp/output", result)
    }

    @Test
    fun `ordinary string extras are unchanged`() {
        val result = resolveExtraPathValue(
            name = "MESSAGE",
            type = "STRING",
            flags = emptyList(),
            value = "Download/not-a-path.txt",
            externalStorageRoot = externalStorage
        )

        assertEquals("Download/not-a-path.txt", result)
    }
}
