package com.unciv.ui.components.fonts

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.glutils.PixmapTextureData

/** A font atlas page whose mipmaps are refreshed when a batch binds it for drawing.
 * The packer retains the complete CPU image for context restoration; glyph uploads
 * update only level zero, so multiple labels can share one mipmap regeneration.
 */
internal class MipmappedFontTexture(
    private val pagePixmap: Pixmap,
    private val fontImplementation: FontImplementation
) : Texture(PixmapTextureData(pagePixmap, pagePixmap.format, true, false, true)) {
    private var mipmapsDirty = false
    private var disposed = false

    fun uploadGlyph(pixmap: Pixmap, x: Int, y: Int) {
        // Bypass our draw-time bind hook: adding another glyph must not regenerate
        // mipmaps left dirty by a previous label. The packer already updated pagePixmap.
        super.bind()
        Gdx.gl.glTexSubImage2D(glTarget, 0, x, y, pixmap.width, pixmap.height,
            pixmap.glFormat, pixmap.glType, pixmap.pixels)
        mipmapsDirty = true
    }

    override fun bind() {
        super.bind()
        updateMipmaps()
    }

    override fun bind(unit: Int) {
        super.bind(unit)
        updateMipmaps()
    }

    private fun updateMipmaps() {
        if (!mipmapsDirty) return
        Gdx.gl.glGenerateMipmap(glTarget)
        mipmapsDirty = false
    }

    override fun load(data: TextureData) {
        // A full upload (including context restoration) builds mipmaps from the CPU
        // page. Do not regenerate the old GPU contents when Texture.load calls bind().
        mipmapsDirty = false
        super.load(data)
    }

    override fun reload() {
        super.reload()
        fontImplementation.configureFontTexture(this)
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        super.dispose()
        pagePixmap.dispose()
    }
}
