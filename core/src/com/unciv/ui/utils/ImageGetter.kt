package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import java.util.*

object ImageGetter {
    private var textureRegionByFileName = HashMap<String, TextureRegion>()
    const val WhiteDot = "skin/whiteDot.png"

    fun getImage(fileName: String): Image {
        return Image(getTextureRegion(fileName))
    }

    fun getDrawable(fileName: String): TextureRegionDrawable {
        val drawable = TextureRegionDrawable(getTextureRegion(fileName))
        drawable.minHeight = 0f
        drawable.minWidth = 0f
        return drawable
    }

    private fun getTextureRegion(fileName: String): TextureRegion {
        try {
            if (!textureRegionByFileName.containsKey(fileName))
                textureRegionByFileName[fileName] = TextureRegion(Texture(Gdx.files.internal(fileName)))
        } catch (ex: Exception) {
            print("File $fileName not found!")
            throw ex
        }

        return textureRegionByFileName[fileName]!!
    }

    fun getStatIcon(name: String): Image {
        return getImage("StatIcons/20x" + name + "5.png")
    }

}
