package com.unciv.ui.screens.basescreen

import com.unciv.ui.components.fonts.MipmappedFontTexture

/** Keeps font sharpening per texture slot so images and text can share a draw call. */
class FontLodBiasBatch(size: Int) : TextureArraySpriteBatch(size) {
    // Shader generation is called by the superclass constructor: it must not use subclass fields.
    private val fontShader = shader
    private val biases = FloatArray(maxTextureUnits)

    override fun getFragmentShader(maximumTextureUnits: Int): String {
        // Preserve the library's constant sampler indices for GLES 2 driver compatibility.
        var source = super.getFragmentShader(maximumTextureUnits)
        source = source.replace("uniform sampler2D u_textures[",
            "uniform float u_lodBias[$maximumTextureUnits];\nuniform sampler2D u_textures[")
        for (slot in 0 until maximumTextureUnits)
            source = source.replace("texture2D(u_textures[$slot], uv)",
                "texture2D(u_textures[$slot], uv, u_lodBias[$slot])")
        return source
    }

    override fun prepareFlush() {
        for (slot in 0 until textureLFUSize) {
            val texture = getTextureAtSlot(slot)
            biases[slot] = if (texture is MipmappedFontTexture) -0.5f else 0f
            // Cached pages can acquire more glyphs without being bound again.
            if (texture is MipmappedFontTexture) texture.prepareForDraw(slot)
        }
        // Custom shaders are responsible for their own sampling policy.
        if (shader === fontShader)
            fontShader.setUniform1fv("u_lodBias", biases, 0, biases.size)
    }
}
