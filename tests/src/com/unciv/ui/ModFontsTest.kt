package com.unciv.ui

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.FontImplementation
import com.unciv.ui.components.fonts.FontMetricsCommon
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
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

            val found = NativeFont().getModFonts(FileHandle(root.resolve("mods")))
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

    private class NativeFont : FontImplementation {
        override fun setFontFamily(fontFamilyData: FontFamilyData, size: Int) = error("Not used")
        override fun getFontSize() = error("Not used")
        override fun getCharPixmap(symbolString: String): Pixmap = error("Not used")
        override fun getSystemFonts(): Sequence<FontFamilyData> = emptySequence()
        override fun getMetrics(): FontMetricsCommon = error("Not used")
    }

}
