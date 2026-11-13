package com.unciv.logic.files

import com.badlogic.gdx.Files
import com.badlogic.gdx.files.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.*

class SaveLocationTest {
    @Test
    fun listsVirtualLocalSavesWithoutNativeFiles() {
        val backend = mock(Files::class.java)
        val folder = handle("SaveFiles", exists = true)
        val save = handle("SaveFiles/game", exists = true)
        `when`(backend.local("SaveFiles")).thenReturn(folder)
        `when`(folder.list()).thenReturn(arrayOf(save))
        `when`(backend.external(anyString())).thenThrow(UnsupportedOperationException("No external storage"))

        assertEquals(listOf(save), UncivFiles(backend).getSaves().toList())
        verify(backend, never()).external(anyString())
        verify(save, never()).file()
    }

    @Test
    fun unavailableExternalStorageUsesVirtualLocalHandle() {
        val backend = mock(Files::class.java)
        val local = handle("SaveFiles/game", exists = false)
        `when`(backend.local("SaveFiles/game")).thenReturn(local)
        `when`(backend.external(anyString())).thenThrow(UnsupportedOperationException("No external storage"))

        assertSame(local, UncivFiles(backend).getSave("game"))
        verify(backend, never()).external(anyString())
        verify(local, never()).file()
        verify(local.parent()).mkdirs()
    }

    @Test
    fun availableStoragePreservesExistingSaveAndPreferenceSelection() {
        val previousPreference = UncivFiles.preferExternalStorage
        try {
            for (preferExternal in listOf(false, true)) {
                UncivFiles.preferExternalStorage = preferExternal
                for (localExists in listOf(false, true)) {
                    for (externalExists in listOf(false, true)) {
                        val backend = mock(Files::class.java)
                        val local = handle("local/SaveFiles/game", localExists)
                        val external = handle("external/SaveFiles/game", externalExists)
                        `when`(backend.isExternalStorageAvailable).thenReturn(true)
                        `when`(backend.local("SaveFiles/game")).thenReturn(local)
                        `when`(backend.external("SaveFiles/game")).thenReturn(external)
                        val expected = when {
                            localExists && !externalExists -> local
                            externalExists && !localExists -> external
                            preferExternal -> external
                            else -> local
                        }
                        assertSame(expected, UncivFiles(backend).getSave("game"))
                        verify(expected.parent()).mkdirs()
                    }
                }
            }
        } finally {
            UncivFiles.preferExternalStorage = previousPreference
        }
    }

    private fun handle(path: String, exists: Boolean): FileHandle {
        val handle = mock(FileHandle::class.java)
        `when`(handle.path()).thenReturn(path)
        `when`(handle.exists()).thenReturn(exists)
        `when`(handle.parent()).thenReturn(mock(FileHandle::class.java))
        `when`(handle.file()).thenThrow(UnsupportedOperationException("Virtual file"))
        return handle
    }
}
