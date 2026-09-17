package com.unciv.ui.screens.basescreen

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.unciv.ui.components.fonts.MipmappedFontTexture

/** Shared desktop and GLES font sampling, with LOD bias applied only to font atlas pages. */
class FontLodBiasBatch(size: Int, private val fontShader: ShaderProgram = createFontShader()) :
    SpriteBatch(size, fontShader) {

    private var fontTexture = false

    override fun switchTexture(texture: Texture) {
        // This flushes the previous texture first, with its own bias still in effect.
        super.switchTexture(texture)
        fontTexture = texture is MipmappedFontTexture
    }

    override fun flush() {
        // Set this at draw time, including capacity flushes and after shader/context
        // changes. An actor's custom shader need not declare our uniform.
        if (isDrawing && shader === fontShader)
            fontShader.setUniformf("u_lodBias", if (fontTexture) -0.5f else 0f)
        super.flush()
    }

    override fun dispose() {
        super.dispose()
        // SpriteBatch does not own a shader supplied to its constructor.
        fontShader.dispose()
    }

    companion object {
        private fun createFontShader(): ShaderProgram {
            // Match SpriteBatch's attributes, tint and packed-alpha correction.
            val vertexShader = """
                attribute vec4 a_position;
                attribute vec4 a_color;
                attribute vec2 a_texCoord0;
                uniform mat4 u_projTrans;
                varying vec4 v_color;
                varying vec2 v_texCoords;
                void main() {
                    v_color = a_color;
                    v_color.a *= 255.0 / 254.0;
                    v_texCoords = a_texCoord0;
                    gl_Position = u_projTrans * a_position;
                }
            """.trimIndent()
            // GLSL ES 1.00 supports a fragment texture2D bias argument, even though
            // GLES has no GL_TEXTURE_LOD_BIAS texture parameter. Negative bias
            // selects finer mip levels without remapping glyph alpha or colours.
            val fragmentShader = """
                #ifdef GL_ES
                precision mediump float;
                #define LOWP lowp
                #else
                #define LOWP
                #endif
                varying LOWP vec4 v_color;
                varying vec2 v_texCoords;
                uniform sampler2D u_texture;
                uniform float u_lodBias;
                void main() {
                    gl_FragColor = v_color * texture2D(u_texture, v_texCoords, u_lodBias);
                }
            """.trimIndent()
            val shader = ShaderProgram(vertexShader, fragmentShader)
            if (!shader.isCompiled) {
                val log = shader.log
                shader.dispose()
                throw IllegalArgumentException("Error compiling font LOD bias shader: $log")
            }
            return shader
        }
    }
}
