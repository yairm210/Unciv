package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
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
            if (!textureRegionByFileName.containsKey(fileName)) {
                val texture = Texture(Gdx.files.internal(fileName),true)
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.MipMapLinearLinear)
                textureRegionByFileName[fileName] = TextureRegion(texture)
            }
        } catch (ex: Exception) {
            return getTextureRegion(WhiteDot)
            throw Exception("File $fileName not found!",ex)
        }

        return textureRegionByFileName[fileName]!!
    }

    fun getStatIcon(name: String): Image {
        return getImage("StatIcons/20x" + name + "5.png")
    }

    fun getUnitIcon(unitName:String):Image{
        return getImage("UnitIcons/$unitName.png")
    }

    fun getImprovementIcon(improvementName:String):Image{
        return getImage("ImprovementIcons/" + improvementName.replace(' ', '_') + "_(Civ5).png")
    }

    fun getBlue() = Color(0x004085bf)
}
