package com.unciv.scripting.api

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.Base64Coder
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.toPixmap
import java.io.ByteArrayOutputStream

object ScriptingApiAppHelpers {
    //Debug/dev identity function for both Kotlin and scripts. Check if value survives serialization, force something to be added to ScriptingProtocol.instanceSaver, etc.

    // @param path Path of an internal file as exposed in Gdx.files.internal.
    // @return The contents of the internal file read as a text string.
    fun assetFileString(path: String) = Gdx.files.internal(path).readString()

    // @param path Path of an internal file as exposed in Gdx.files.internal.
    // @return The contents of the internal file encoded as a Base64 string.
    fun assetFileB64(path: String) = String(Base64Coder.encode(Gdx.files.internal(path).readBytes()))

    // @param path Path of an internal image as exposed in ImageGetter as a TextureRegionDrawable from an atlas.
    // @return The image encoded as a PNG file encoded as a Base64 string.
    fun assetImageB64(path: String): String {
        // To test in Python:
        // import PIL.Image, io, base64; PIL.Image.open(io.BytesIO(base64.b64decode(apiHelpers.assetImage("StatIcons/Resistance")))).show()
        val fakepng = ByteArrayOutputStream()
        //Close this stream? Well, the docs say doing so "has no effect", and it should clearly get GC'd anyway.
        val pixmap = ImageGetter.getDrawable(path).getRegion().toPixmap()
        val exporter = PixmapIO.PNG() // Could be kept and "reused to encode multiple PNGs with minimal allocation", according to the docs. Probably not a sufficient bottleneck to justify the complexity and risk, though.
        exporter.setFlipY(false)
        exporter.write(fakepng, pixmap)
        pixmap.dispose() // In theory needed to avoid memory leak. Doesn't seem to actually have any impact, compared to the .dispose() inside .toPixmap(). Maybe the exporter's dispose also calls this?
        exporter.dispose() // This one should be called automatically by GC anyway.
        return String(Base64Coder.encode(fakepng.toByteArray()))
    }

    //val isMainThread get() = Thread().getCurrentThread() == UncivGame.mainThread

    //fun runInThread(func: () -> Unit) {}

    //fun runInMainLoop(func: () -> Unit) {}

}
