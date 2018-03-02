package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

import java.util.HashMap

object ImageGetter {
    var textureRegionByFileName = HashMap<String, TextureRegion>()
    val WhiteDot = "skin/whiteDot.png"

    fun getImage(fileName: String): Image {
        return Image(getTextureRegion(fileName))
    }

    fun getDrawable(fileName: String): TextureRegionDrawable {
        val drawable = TextureRegionDrawable(getTextureRegion(fileName))
        drawable.minHeight = 0f
        drawable.minWidth = 0f
        return drawable
    }

    fun getSingleColorDrawable(color: Color): Drawable {
        return getDrawable(WhiteDot).tint(color)
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
