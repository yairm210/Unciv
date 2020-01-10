package com.unciv

import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class UncivGameTests {

    @Test
    fun `viewEntireMapForDebug is set to false`() {
        val uncivGame = UncivGame("desktop")
        Assert.assertFalse(uncivGame.viewEntireMapForDebug)
    }

    @Test
    fun `superchargedForDebug is set to false`() {
        val uncivGame = UncivGame("desktop")
        Assert.assertFalse(uncivGame.superchargedForDebug)
    }

    @Test
    fun `rewriteTranslationFiles is set to false`() {
        val uncivGame = UncivGame("desktop")
        Assert.assertFalse(uncivGame.rewriteTranslationFiles)
    }

}