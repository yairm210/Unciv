package com.unciv.ui.components.fonts

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.unciv.testing.GdxTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(GdxTestRunner::class)
class NativeBitmapFontDataTest {
    private class TestFont(override val useMipMaps: Boolean, private val glyphSize: Int = 50) : FontImplementation {
        override fun setFontFamily(fontFamilyData: FontFamilyData, size: Int) {}
        override fun getFontSize() = 100
        override fun getCharPixmap(symbolString: String) = Pixmap(glyphSize, glyphSize, Pixmap.Format.RGBA8888).apply {
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
    fun multipleLabelsShareOneMipmapUpdateAtBatchFlush() {
        val data = NativeBitmapFontData(TestFont(true))
        `when`(Gdx.gl.glGenBuffer()).thenReturn(1)
        val batch = SpriteBatch(1000, mock(ShaderProgram::class.java))
        try {
            val texture = data.regions.first().texture
            assertTrue(texture.textureData.useMipMaps())
            assertEquals(TextureFilter.MipMapLinearLinear, texture.minFilter)
            assertEquals(TextureFilter.Linear, texture.magFilter)

            clearInvocations(Gdx.gl)
            batch.begin()
            // Simulate two labels laying out new characters and queuing draws. Neither
            // the layout nor the second subimage upload should regenerate mipmaps.
            for (text in listOf("A", "B")) {
                data.getGlyphs(GlyphLayout.GlyphRun(), text, 0, text.length, null)
                batch.draw(texture, 0f, 0f, 10f, 10f)
                assertFalse("Premature mipmap update for $text", mipmapsUploaded())
            }
            assertEquals(2, mockingDetails(Gdx.gl).invocations.count { it.method.name == "glTexSubImage2D" })
            assertFalse(mockingDetails(Gdx.gl).invocations.any { it.method.name == "glTexImage2D" })
            batch.end()
            assertEquals(1, mockingDetails(Gdx.gl).invocations.count { it.method.name == "glGenerateMipmap" })
            val calls = mockingDetails(Gdx.gl).invocations.map { it.method.name }
            assertTrue("Mipmaps must be ready before drawing", calls.indexOf("glGenerateMipmap") < calls.indexOf("glDrawElements"))

            clearInvocations(Gdx.gl)
            data.getGlyphs(GlyphLayout.GlyphRun(), "AB", 0, 2, null)
            texture.bind()
            assertFalse("Cached glyphs should not regenerate mipmaps", mipmapsUploaded())

            // A later addition must also become visible on the very next bind.
            data.getGlyphs(GlyphLayout.GlyphRun(), "C", 0, 1, null)
            assertFalse(mipmapsUploaded())
            texture.bind(0)
            assertEquals(1, mockingDetails(Gdx.gl).invocations.count { it.method.name == "glGenerateMipmap" })
        } finally {
            batch.dispose()
            data.regions.forEach { it.texture.dispose() }
            data.dispose()
        }
    }

    @Test
    fun newPageDoesNotFlushPendingMipmapsOnAnotherPage() {
        val data = NativeBitmapFontData(TestFont(true, 400))
        try {
            val firstTexture = data.regions.first().texture
            data.getGlyphs(GlyphLayout.GlyphRun(), "ABC", 0, 3, null)
            assertEquals(1, data.regions.size)
            data.getGlyphs(GlyphLayout.GlyphRun(), "D", 0, 1, null)
            assertEquals(2, data.regions.size)
            clearInvocations(Gdx.gl)
            data.regions[1].texture.bind()
            assertFalse("New page already has mipmaps", mipmapsUploaded())
            firstTexture.bind()
            assertEquals(1, mockingDetails(Gdx.gl).invocations.count { it.method.name == "glGenerateMipmap" })
        } finally {
            data.dispose()
        }
    }

    @Test
    fun fullReloadRetainsNewGlyphsWithoutGeneratingStaleMipmaps() {
        val data = NativeBitmapFontData(TestFont(true))
        try {
            data.getGlyphs(GlyphLayout.GlyphRun(), "A", 0, 1, null)
            val texture = data.regions.first().texture
            val glyph = data.getGlyph('A')
            assertEquals(-1, texture.textureData.consumePixmap().getPixel(glyph.srcX, glyph.srcY))
            clearInvocations(Gdx.gl)
            texture.load(texture.textureData)
            assertTrue("Full upload must rebuild mipmaps", mipmapsUploaded())
            val calls = mockingDetails(Gdx.gl).invocations.map { it.method.name }
            assertTrue("No generation from stale contents before the full upload",
                calls.indexOf("glGenerateMipmap") == -1 || calls.indexOf("glTexImage2D") < calls.indexOf("glGenerateMipmap"))
            clearInvocations(Gdx.gl)
            texture.bind()
            assertFalse(mipmapsUploaded())
        } finally {
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
