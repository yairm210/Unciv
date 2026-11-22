package com.unciv.ui.components.fonts

import com.badlogic.gdx.Gdx
import com.unciv.logic.files.UncivFiles
import com.unciv.testing.BaseTestRunner
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(BaseTestRunner::class)
class ModFontsTests {
    private lateinit var dataDir: File

    @Before
    fun createDataDir() {
        dataDir = kotlin.io.path.createTempDirectory("unciv-mod-fonts").toFile()
        val fontsDir = File(dataDir, "mods/Font Mod/fonts")
        fontsDir.mkdirs()
        File(fontsDir, "MyFont.ttf").writeText("not a real font")
        File(fontsDir, "Other.OTF").writeText("not a real font")
        File(fontsDir, "readme.txt").writeText("not a font")
        File(dataDir, "mods/Mod Without Fonts").mkdirs()
    }

    @After
    fun deleteDataDir() {
        dataDir.deleteRecursively()
    }

    private fun scan(files: UncivFiles) = runBlocking { ModFonts.scan(files.getModsFolder()).toList() }

    @Test
    fun `finds fonts in the mods folder of a custom data directory`() {
        val files = UncivFiles(Gdx.files, dataDir.absolutePath)
        val fonts = scan(files)
        Assert.assertEquals(
            setOf("MyFont (Font Mod)", "Other (Font Mod)"),
            fonts.map { it.localName }.toSet()
        )
        // The stored path must resolve the same way DesktopFont and AndroidFont load it
        for (font in fonts)
            Assert.assertTrue(font.filePath!!, files.getLocalFile(font.filePath!!).file().isFile)
    }

    @Test
    fun `finds fonts in a local mods folder`() {
        val localMods = Gdx.files.local("mods")
        // Skip instead of fail - a dev running this with mods installed in android/assets shouldn't get a red test
        Assume.assumeFalse("Test would clobber an existing local mods folder", localMods.exists())
        try {
            File(dataDir, "mods").copyRecursively(localMods.file())
            val files = UncivFiles(Gdx.files)
            val fonts = scan(files)
            Assert.assertEquals(
                setOf("MyFont (Font Mod)", "Other (Font Mod)"),
                fonts.map { it.localName }.toSet()
            )
            for (font in fonts)
                Assert.assertTrue(font.filePath!!, files.getLocalFile(font.filePath!!).file().isFile)
        } finally {
            localMods.deleteDirectory()
        }
    }

    @Test
    fun `skips handles that are not plain filesystem folders`() {
        Assert.assertTrue(runBlocking { ModFonts.scan(Gdx.files.internal("mods")).toList() }.isEmpty())
        Assert.assertTrue(runBlocking { ModFonts.scan(Gdx.files.classpath("mods")).toList() }.isEmpty())
    }

    @Test
    fun `missing mods folder yields no fonts`() {
        Assert.assertTrue(scan(UncivFiles(Gdx.files, File(dataDir, "nonexistent").absolutePath)).isEmpty())
    }
}
