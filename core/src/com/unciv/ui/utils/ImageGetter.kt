package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import java.util.*

object ImageGetter {
    private var textureRegionByFileName = HashMap<String, TextureRegion>()
    const val WhiteDot = "OtherIcons/whiteDot.png"

    // When we used to load images directly from different files, without using a texture atlas,
    // The draw() phase of the main screen would take a really long time because the BatchRenderer would
    // always have to switch between like 170 different textures.
    // So, we now use TexturePacker in the DesktopLauncher class to pack all the different images into single images,
    // and the atlas is what tells us what was packed where.
    val atlas = TextureAtlas("Images/game.atlas")

    init{
    }

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
            val region = atlas.findRegion(fileName.replace(".png",""))

            if(region==null)
                throw Exception("Could not find $fileName")
            return region
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

    fun getPromotionIcon(promotionName:String):Image{
        return getImage("UnitPromotionIcons/" + promotionName.replace(' ', '_') + "_(Civ5).png")
    }

    fun getBlue() = Color(0x004085bf)

    fun getBackground(color:Color): Drawable {
        return getDrawable(WhiteDot).tint(color)
    }
}
