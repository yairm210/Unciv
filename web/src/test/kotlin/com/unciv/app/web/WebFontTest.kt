package com.unciv.app.web

import com.badlogic.gdx.files.FileHandle
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class WebFontTest {
    @Test
    fun `mod font discovery uses file handles and supports ttf and otf`() = runBlocking {
        val root = Files.createTempDirectory("unciv-web-mod-fonts-").toFile()
        try {
            val fonts = root.resolve("mods/Example Mod/fonts")
            fonts.mkdirs()
            fonts.resolve("Display Face.ttf").writeBytes(byteArrayOf(1))
            fonts.resolve("Reading.otf").writeBytes(byteArrayOf(2))
            fonts.resolve("notes.txt").writeText("not a font")
            fonts.resolve("nested").mkdir()
            fonts.resolve("directory.ttf").mkdir()

            val found = WebFont().getModFonts(NoFileAccessHandle(FileHandle(root.resolve("mods"))))
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
        override fun file() = error("This backend does not support FileHandle.file()")
        override fun child(name: String): FileHandle = NoFileAccessHandle(delegate.child(name))
        override fun list(): Array<FileHandle> = delegate.list().map(::NoFileAccessHandle).toTypedArray()
        override fun isDirectory(): Boolean = delegate.isDirectory
        override fun exists(): Boolean = delegate.exists()
    }
}
