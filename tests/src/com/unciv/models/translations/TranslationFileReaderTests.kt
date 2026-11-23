package com.unciv.models.translations

import com.badlogic.gdx.files.FileHandle
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(GdxTestRunner::class)
class TranslationFileReaderTests {
    private val bom = "﻿"

    private fun tempProperties(content: String): FileHandle {
        val file = File.createTempFile("unciv-translation-test", ".properties")
        file.deleteOnExit()
        file.writeText(content, Charsets.UTF_8)
        return FileHandle(file)
    }

    @Test
    fun `reads a translation file`() {
        val translations = TranslationFileReader.read(tempProperties("Monument = Monumento\nShrine = Santuario\n"))
        Assert.assertEquals("Monumento", translations["Monument"])
        Assert.assertEquals("Santuario", translations["Shrine"])
    }

    /** Windows editors such as Notepad can save UTF-8 with a byte order mark, as mod files are often written. */
    @Test
    fun `reads the first entry of a translation file that starts with a byte order mark`() {
        val translations = TranslationFileReader.read(tempProperties("${bom}Monument = Monumento\nShrine = Santuario\n"))
        Assert.assertEquals("Monumento", translations["Monument"])
        Assert.assertEquals("Santuario", translations["Shrine"])
        Assert.assertFalse(translations.keys.any { it.startsWith(bom) })
    }

    @Test
    fun `reads a translation file that starts with a byte order mark and a comment`() {
        val translations = TranslationFileReader.read(tempProperties("$bom# Spanish\nMonument = Monumento\n"))
        Assert.assertEquals("Monumento", translations["Monument"])
        Assert.assertFalse(translations.keys.any { it.startsWith(bom) })
    }
}
