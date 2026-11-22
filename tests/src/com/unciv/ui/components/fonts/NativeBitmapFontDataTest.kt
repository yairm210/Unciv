package com.unciv.ui.components.fonts

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.IntBuffer

@RunWith(GdxTestRunner::class)
class NativeBitmapFontDataTest {
    @Before
    fun configureMockGl() {
        var nextTexture = 1
        `when`(Gdx.gl.glGenTexture()).thenAnswer { nextTexture++ }
        `when`(Gdx.gl.glGenBuffer()).thenReturn(1)
        `when`(Gdx.gl.glCreateShader(anyInt())).thenReturn(1)
        `when`(Gdx.gl.glCreateProgram()).thenReturn(1)
        doAnswer {
            val value = if (it.getArgument<Int>(0) == GL20.GL_MAX_TEXTURE_IMAGE_UNITS) 2 else 0
            it.getArgument<IntBuffer>(1).put(0, value)
            null
        }.`when`(Gdx.gl).glGetIntegerv(anyInt(), org.mockito.ArgumentMatchers.any())
        doAnswer {
            it.getArgument<IntBuffer>(2).put(0, 1)
            null
        }.`when`(Gdx.gl).glGetShaderiv(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any())
        doAnswer {
            val value = if (it.getArgument<Int>(1) == GL20.GL_LINK_STATUS) 1 else 0
            it.getArgument<IntBuffer>(2).put(0, value)
            null
        }.`when`(Gdx.gl).glGetProgramiv(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any())
    }

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
    fun mixedTexturesKeepPerSlotBiasAcrossCapacityFlushesEvictionAndShaderChanges() {
        val data = NativeBitmapFontData(TestFont())
        val image = Texture(4, 4, Pixmap.Format.RGBA8888)
        val otherImage = Texture(4, 4, Pixmap.Format.RGBA8888)
        val customShader = mock(ShaderProgram::class.java)
        val batch = FontLodBiasBatch(2)
        val drawnBiases = mutableListOf<List<Float>>()
        var bias = emptyList<Float>()
        doAnswer {
            bias = it.getArgument<FloatArray>(2).toList()
            null
        }.`when`(Gdx.gl).glUniform1fv(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any<FloatArray>(), anyInt())
        doAnswer {
            drawnBiases.add(if (batch.shader === customShader) emptyList() else bias)
            null
        }.`when`(Gdx.gl).glDrawElements(anyInt(), anyInt(), anyInt(), anyInt())
        try {
            val font = data.regions.first().texture
            batch.begin()
            batch.draw(font, 0f, 0f)
            batch.draw(image, 0f, 0f)
            batch.flush()
            assertEquals("Text and images must share a draw", 1, batch.renderCalls)
            assertEquals(listOf(-0.5f, 0f), drawnBiases.last())
            batch.draw(font, 0f, 0f)
            batch.draw(image, 0f, 0f)
            batch.draw(font, 0f, 0f) // capacity flush
            batch.flush()
            assertEquals(listOf(-0.5f, 0f), drawnBiases.last())
            // Make the font least-used, then evict it with an ordinary image.
            repeat(4) { batch.draw(image, 0f, 0f) }
            batch.draw(otherImage, 0f, 0f)
            batch.draw(image, 0f, 0f)
            batch.flush()
            assertEquals(listOf(0f, 0f), drawnBiases.last())
            batch.shader = customShader
            batch.draw(font, 0f, 0f)
            batch.shader = null
            assertTrue(drawnBiases.last().isEmpty())
            assertFalse(mockingDetails(customShader).invocations.any {
                it.method.name == "setUniform1fv" && it.arguments[0] == "u_lodBias"
            })
            batch.end()
            batch.begin()
            batch.draw(image, 0f, 0f)
            batch.draw(otherImage, 0f, 0f)
            batch.end()
            assertEquals(listOf(0f, 0f), drawnBiases.last())
        } finally {
            doAnswer { null }.`when`(Gdx.gl).glDrawElements(anyInt(), anyInt(), anyInt(), anyInt())
            doAnswer { null }.`when`(Gdx.gl).glUniform1fv(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any<FloatArray>(), anyInt())
            batch.dispose()
            image.dispose()
            otherImage.dispose()
            data.dispose()
        }
    }

    @Test
    fun cachedFontUpdatesRestoreBindingsAndRegenerateMipmapsBeforeDrawing() {
        val data = NativeBitmapFontData(TestFont())
        val image = Texture(4, 4, Pixmap.Format.RGBA8888)
        val batch = FontLodBiasBatch(10)
        val bindings = mutableMapOf<Int, Int>()
        var activeUnit = 0
        doAnswer {
            activeUnit = it.getArgument<Int>(0) - GL20.GL_TEXTURE0
            null
        }.`when`(Gdx.gl).glActiveTexture(anyInt())
        doAnswer {
            bindings[activeUnit] = it.getArgument(1)
            null
        }.`when`(Gdx.gl).glBindTexture(anyInt(), anyInt())
        try {
            val font = data.regions.first().texture
            batch.begin()
            batch.draw(font, 0f, 0f)
            batch.draw(image, 0f, 0f) // leaves image slot active
            data.getGlyph('A') // overwrites that slot's binding
            data.getGlyph('B')
            clearInvocations(Gdx.gl)
            batch.flush()
            assertEquals(font.textureObjectHandle, bindings[0])
            assertEquals(image.textureObjectHandle, bindings[1])
            val calls = mockingDetails(Gdx.gl).invocations.map { it.method.name }
            assertEquals(1, calls.count { it == "glGenerateMipmap" })
            assertTrue(calls.indexOf("glGenerateMipmap") < calls.indexOf("glDrawElements"))
            clearInvocations(Gdx.gl)
            batch.draw(font, 0f, 0f)
            batch.draw(image, 0f, 0f)
            batch.flush()
            assertFalse("Unchanged cached textures must not be rebound", mockingDetails(Gdx.gl).invocations.any {
                it.method.name == "glBindTexture" || it.method.name == "glGenerateMipmap"
            })
            data.getGlyph('C') // update a page already resident in the cache
            clearInvocations(Gdx.gl)
            batch.draw(font, 0f, 0f)
            batch.end()
            assertEquals(1, mockingDetails(Gdx.gl).invocations.count { it.method.name == "glGenerateMipmap" })
            assertEquals(image.textureObjectHandle, bindings[1])
        } finally {
            doAnswer { null }.`when`(Gdx.gl).glActiveTexture(anyInt())
            doAnswer { null }.`when`(Gdx.gl).glBindTexture(anyInt(), anyInt())
            batch.dispose()
            image.dispose()
            data.dispose()
        }
    }

    @Test
    fun nestedBatchRestoresOuterTextureBindings() {
        val image = Texture(4, 4, Pixmap.Format.RGBA8888)
        val other = Texture(4, 4, Pixmap.Format.RGBA8888)
        val outer = FontLodBiasBatch(10)
        val inner = FontLodBiasBatch(10)
        try {
            outer.begin()
            outer.draw(image, 0f, 0f)
            inner.begin()
            inner.draw(other, 0f, 0f)
            inner.end()
            clearInvocations(Gdx.gl)
            outer.end()
            assertTrue(mockingDetails(Gdx.gl).invocations.any {
                it.method.name == "glBindTexture" && it.arguments[1] == image.textureObjectHandle
            })
            assertEquals(1, outer.renderCalls)
        } finally {
            inner.dispose()
            outer.dispose()
            image.dispose()
            other.dispose()
        }
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
