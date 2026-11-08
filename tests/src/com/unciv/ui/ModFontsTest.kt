package com.unciv.ui

import com.badlogic.gdx.files.FileHandle
import com.unciv.ui.components.fonts.ModFonts
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModFontsTest {
    @Test
    fun `native scan retains filename invariant and relative file path`() = runBlocking {
        val root = Files.createTempDirectory("unciv-native-mod-fonts-").toFile()
        try {
            for (modName in listOf("Example", "Other")) {
                val fonts = root.resolve("mods/$modName/fonts")
                fonts.mkdirs()
                fonts.resolve("Display.ttf").writeBytes(byteArrayOf(1))
            }

            val found = ModFonts.scan(FileHandle(root.resolve("mods")))
                .toList(mutableListOf())
                .map { Triple(it.localName, it.invariantName, it.filePath) }
                .sortedBy { it.first }

            assertEquals(
                listOf(
                    Triple("Display (Example)", "Display", "mods/Example/fonts/Display.ttf"),
                    Triple("Display (Other)", "Display", "mods/Other/fonts/Display.ttf"),
                ),
                found
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `file handle scan finds supported mod fonts without java file access`() = runBlocking {
        val root = Files.createTempDirectory("unciv-mod-fonts-").toFile()
        try {
            val fonts = root.resolve("mods/Example Mod/fonts")
            fonts.mkdirs()
            fonts.resolve("Display Face.ttf").writeBytes(byteArrayOf(1))
            fonts.resolve("Reading.otf").writeBytes(byteArrayOf(2))
            fonts.resolve("notes.txt").writeText("not a font")
            fonts.resolve("nested").mkdir()
            fonts.resolve("directory.ttf").mkdir()

            val found = ModFonts.scanWithFileHandles(NoFileAccessHandle(FileHandle(root.resolve("mods"))))
                .toList(mutableListOf())

            assertEquals(
                listOf(
                    Triple("Display Face (Example Mod)", "mods/Example Mod/fonts/Display Face.ttf", "mods/Example Mod/fonts/Display Face.ttf"),
                    Triple("Reading (Example Mod)", "mods/Example Mod/fonts/Reading.otf", "mods/Example Mod/fonts/Reading.otf"),
                ),
                found.map { Triple(it.localName, it.invariantName, it.filePath) }.sortedBy { it.first }
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private class NoFileAccessHandle(private val delegate: FileHandle) : FileHandle(delegate.path(), delegate.type()) {
        override fun file(): File = error("This backend does not support FileHandle.file()")

        override fun child(name: String): FileHandle = NoFileAccessHandle(delegate.child(name))

        override fun list(): Array<FileHandle> = delegate.list().map(::NoFileAccessHandle).toTypedArray()

        override fun isDirectory(): Boolean = delegate.isDirectory

        override fun exists(): Boolean = delegate.exists()
    }
}
