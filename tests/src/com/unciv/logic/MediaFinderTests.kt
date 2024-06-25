package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.files.IMediaFinder
import com.unciv.logic.files.UncivFiles
import com.unciv.models.UncivSound
import com.unciv.models.metadata.GameSettings
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 *  Notes:
 *  - These tests ***cannot*** test the most interesting aspect of IMediaFinder:
 *    dealing with Gdx `FileHandle` limitations limiting `list()`, `exists()` or `isDirectory` in specific situations.
 *    We're always running something close to desktop with unpacked assets files available.
 *  - This could use a private instance of Sounds() or subclass it directly, but I chose to demonstrate
 *    the (intended!) way to seamlessly attach the API to a class already deriving from another superclass.
 */
@RunWith(GdxTestRunner::class)
class MediaFinderTests : IMediaFinder by IMediaFinder.Sounds() {
    init {
        UncivGame.Current = UncivGame()
        UncivGame.Current.settings = GameSettings()
        UncivGame.Current.files = UncivFiles(Gdx.files)
    }

    @Test
    fun `Sound finder finds Chimes`() {
        val chimes = findMedia(UncivSound.Chimes.fileName)
        Assert.assertNotNull(chimes)
    }

    @Test
    fun `Sound finder does not find bullshit`() {
        val bullshit = findMedia("bullshit.nul")
        Assert.assertNull(bullshit)
    }

    @Test
    fun `Sound listing includes Chimes`() {
        val sounds = listMediaFiles()
        Assert.assertTrue(sounds.any { it.nameWithoutExtension() == "chimes" })
    }

    @Test
    @Ignore("""
        listMediaFiles can return an actual folder listing, which includes unit attack sounds,
        city ambient sounds per era, and e.g. WLTK -
        getInternalMediaNames fails in the sense that it does not derive or hardcode all of those
        """)
    fun `Sound listing does not include bullshit`() {
        val sounds = listMediaFiles()
        val internalSounds = getInternalMediaNames(Gdx.files.internal("")).toSet()
        Assert.assertTrue(sounds.all { it.nameWithoutExtension() in internalSounds })
    }
}
