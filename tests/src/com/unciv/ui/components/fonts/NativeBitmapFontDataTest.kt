package com.unciv.ui.components.fonts

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.unciv.testing.GdxTestRunner
import com.unciv.ui.screens.basescreen.FontLodBiasBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verify
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(GdxTestRunner::class)
class NativeBitmapFontDataTest {
    private class TestFont(private val glyphSize: Int = 50) : FontImplementation {
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
    fun bulkFontPixelsMatchPerPixelConversion() {
        // Non-square image catches row/column mixups; include asymmetric channels,
        // partial alpha, and non-black RGB hidden under zero alpha.
        val argb = intArrayOf(0xff123456.toInt(), 0x807fc321.toInt(), 0x00123456,
            0xffffffff.toInt(), 0x01020304, 0)
        val reference = Pixmap(3, 2, Pixmap.Format.RGBA8888)
        reference.blending = Pixmap.Blending.None
        val actual = Fonts.pixmapFromArgb(3, 2, argb.copyOf())
        try {
            for (i in argb.indices) {
                val rgba = Integer.rotateLeft(argb[i], 8)
                reference.drawPixel(i % 3, i / 3, if ((rgba and 255) == 0) 0xffffff00.toInt() else rgba)
            }
            assertEquals(0, actual.pixels.position())
            assertEquals(3 * 2 * 4, actual.pixels.remaining())
            assertEquals(reference.pixels, actual.pixels)
            assertEquals(0x123456ff, actual.getPixel(0, 0))
            assertEquals(0x7fc32180, actual.getPixel(1, 0))
            assertEquals(0xffffff00.toInt(), actual.getPixel(2, 0))
        } finally {
            reference.dispose()
            actual.dispose()
        }
    }

    @Test
    fun fontBiasFollowsDrawnTextureAcrossFlushesAndShaderChanges() {
        val data = NativeBitmapFontData(TestFont())
        val image = Texture(4, 4, Pixmap.Format.RGBA8888)
        val shader = mock(ShaderProgram::class.java)
        val customShader = mock(ShaderProgram::class.java)
        `when`(Gdx.gl.glGenBuffer()).thenReturn(1)
        // One sprite per batch forces the capacity-flush path as well.
        val batch = FontLodBiasBatch(1, shader)
        var bias = Float.NaN
        val drawnBiases = mutableListOf<Float>()
        doAnswer {
            bias = it.getArgument(1)
            null
        }.`when`(shader).setUniformf(eq("u_lodBias"), anyFloat())
        doAnswer {
            drawnBiases.add(if (batch.shader === shader) bias else Float.NaN)
            null
        }.`when`(Gdx.gl).glDrawElements(anyInt(), anyInt(), anyInt(), anyInt())
        try {
            val fontTexture = data.regions.first().texture
            batch.begin()
            batch.draw(fontTexture, 0f, 0f)
            batch.draw(fontTexture, 0f, 0f) // capacity flush
            batch.draw(image, 0f, 0f) // flush the font with its old bias
            batch.draw(fontTexture, 0f, 0f) // flush the image with zero bias
            batch.shader = customShader
            batch.draw(fontTexture, 0f, 0f)
            batch.shader = null
            batch.draw(fontTexture, 0f, 0f)
            batch.end()
            batch.begin()
            batch.draw(image, 0f, 0f)
            batch.end()
            assertEquals(listOf(-0.5f, -0.5f, 0f, -0.5f, Float.NaN, -0.5f, 0f), drawnBiases)
            assertFalse(mockingDetails(customShader).invocations.any {
                it.method.name == "setUniformf" && it.arguments[0] == "u_lodBias"
            })
        } finally {
            // Do not leave a draw callback referencing this batch on the shared GL mock.
            doAnswer { null }.`when`(Gdx.gl).glDrawElements(anyInt(), anyInt(), anyInt(), anyInt())
            batch.dispose()
            image.dispose()
            data.dispose()
        }
        verify(shader).dispose()
    }

    @Test
    fun multipleLabelsShareOneMipmapUpdateAtBatchFlush() {
        val data = NativeBitmapFontData(TestFont())
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
        val data = NativeBitmapFontData(TestFont(400))
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
        val data = NativeBitmapFontData(TestFont())
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
    fun defaultImplementationUsesMipmapsForDirectGlyphLookups() {
        val data = NativeBitmapFontData(TestFont())
        try {
            val texture = data.regions.first().texture
            assertTrue(texture.textureData.useMipMaps())
            assertEquals(TextureFilter.MipMapLinearLinear, texture.minFilter)
            assertEquals(TextureFilter.Linear, texture.magFilter)
            clearInvocations(Gdx.gl)
            data.getGlyph('A')
            data.getGlyph('B')
            assertFalse(mipmapsUploaded())
            assertTrue(mockingDetails(Gdx.gl).invocations.any { it.method.name == "glTexSubImage2D" })
            texture.bind()
            assertEquals(1, mockingDetails(Gdx.gl).invocations.count { it.method.name == "glGenerateMipmap" })
        } finally {
            data.regions.forEach { it.texture.dispose() }
            data.dispose()
        }
    }
}
