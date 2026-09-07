package com.unciv.ui.components.fonts

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.unciv.testing.GdxTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mockingDetails

@RunWith(GdxTestRunner::class)
class NativeBitmapFontDataTest {
    private class TestFont(override val useMipMaps: Boolean) : FontImplementation {
        override fun setFontFamily(fontFamilyData: FontFamilyData, size: Int) {}
        override fun getFontSize() = 100
        override fun getCharPixmap(symbolString: String) = Pixmap(50, 100, Pixmap.Format.RGBA8888).apply {
            setColor(1f, 1f, 1f, 1f)
            fill()
        }
        override fun getSystemFonts() = emptySequence<FontFamilyData>()
        override fun getMetrics() = FontMetricsCommon(80f, 20f, 100f, 0f)
    }

    private fun mipmapsUploaded() = mockingDetails(Gdx.gl).invocations.any {
        it.method.name == "glGenerateMipmap" ||
            (it.method.name == "glTexImage2D" && (it.arguments[1] as Int) > 0)
    }

    @Test
    fun mipmapsIncludeCharactersAddedAfterTextureCreation() {
        val data = NativeBitmapFontData(TestFont(true))
        try {
            val texture = data.regions.first().texture
            assertTrue(texture.textureData.useMipMaps())
            assertEquals(TextureFilter.MipMapLinearLinear, texture.minFilter)
            assertEquals(TextureFilter.Linear, texture.magFilter)

            // The space glyph already created the page texture. Both subsequent additions
            // must refresh lower levels, even after the incremental packing path is active.
            for (text in listOf("A", "B")) {
                clearInvocations(Gdx.gl)
                data.getGlyphs(GlyphLayout.GlyphRun(), text, 0, text.length, null)
                assertTrue("Missing mipmap update for $text", mipmapsUploaded())
            }
            clearInvocations(Gdx.gl)
            data.getGlyphs(GlyphLayout.GlyphRun(), "AB", 0, 2, null)
            assertFalse("Cached glyphs should not regenerate mipmaps", mipmapsUploaded())
        } finally {
            data.regions.forEach { it.texture.dispose() }
            data.dispose()
        }
    }

    @Test
    fun otherPlatformsKeepLinearFilteringWithoutMipmaps() {
        val data = NativeBitmapFontData(TestFont(false))
        try {
            val texture = data.regions.first().texture
            assertFalse(texture.textureData.useMipMaps())
            assertEquals(TextureFilter.Linear, texture.minFilter)
            assertEquals(TextureFilter.Linear, texture.magFilter)
            clearInvocations(Gdx.gl)
            data.getGlyphs(GlyphLayout.GlyphRun(), "AB", 0, 2, null)
            assertFalse(mipmapsUploaded())
            assertTrue(mockingDetails(Gdx.gl).invocations.any { it.method.name == "glTexSubImage2D" })
        } finally {
            data.regions.forEach { it.texture.dispose() }
            data.dispose()
        }
    }
}
