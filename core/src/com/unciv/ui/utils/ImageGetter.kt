package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceType

object ImageGetter {
    const val WhiteDot = "OtherIcons/whiteDot.png"

    // When we used to load images directly from different files, without using a texture atlas,
    // The draw() phase of the main screen would take a really long time because the BatchRenderer would
    // always have to switch between like 170 different textures.
    // So, we now use TexturePacker in the DesktopLauncher class to pack all the different images into single images,
    // and the atlas is what tells us what was packed where.
    var atlas = TextureAtlas("game.atlas")

    init{
    }

    fun getExternalImage(fileName:String): Image {
        return Image(TextureRegion(Texture("ExtraImages/$fileName.png")))
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
        }
    }

    fun getStatIcon(statName: String): Image {
        return ImageGetter.getImage("StatIcons/$statName")
                .apply { setSize(20f,20f)}
    }

    fun getUnitIcon(unitName:String,color:Color= Color.BLACK):Image{
        return getImage("UnitIcons/$unitName").apply { this.color=color }
    }

    fun getImprovementIcon(improvementName:String, size:Float=20f):Actor{
        val group= Group()
        val circle = getImage("OtherIcons/Circle").apply { setSize(size,size) }

        val improvement = GameBasics.TileImprovements[improvementName]!!
        when {
            improvement.food>0 -> circle.color= Color.GREEN.cpy().lerp(Color.WHITE,0.5f)
            improvement.production>0 -> circle.color= Color.BROWN.cpy().lerp(Color.WHITE,0.5f)
            improvement.gold>0 -> circle.color= Color.GOLD.cpy().lerp(Color.WHITE,0.5f)
            improvement.science>0 -> circle.color= Color.GOLD.cpy().lerp(Color.BLUE,0.5f)
            improvement.culture>0 -> circle.color= Color.GOLD.cpy().lerp(Color.PURPLE,0.5f)
        }


        group.setSize(size,size)
        group.addActor(circle)
        group.addActor(getImage("ImprovementIcons/$improvementName")
                .apply { setSize(size*0.8f,size*0.8f); center(group) })
        return group
    }

    fun getConstructionImage(construction: String): Image {
        if(GameBasics.Buildings.containsKey(construction)) return getImage("BuildingIcons/$construction")
        if(GameBasics.Units.containsKey(construction)) return getUnitIcon(construction)
        if(construction=="Nothing") return getImage("OtherIcons/Stop")
        return getStatIcon(construction)
    }

    fun getPromotionIcon(promotionName:String):Image{
        return getImage("UnitPromotionIcons/" + promotionName.replace(' ', '_') + "_(Civ5)")
    }

    fun getBlue() = Color(0x004085bf)

    fun getBackground(color:Color): Drawable {
        return getDrawable(WhiteDot).tint(color)
    }

    fun refreshAltas() {
        atlas = TextureAtlas("game.atlas")
    }

    fun getResourceImage(resourceName: String, size:Float): Actor {
        val group= Group()
        val resource = GameBasics.TileResources[resourceName]!!
        val circle = getImage("OtherIcons/Circle").apply { setSize(size,size) }
        if(resource.food>0) circle.color= Color.GREEN.cpy().lerp(Color.WHITE,0.5f)
        else if(resource.production>0) circle.color= Color.BROWN.cpy().lerp(Color.WHITE,0.5f)
        else if(resource.gold>0) circle.color= Color.GOLD.cpy().lerp(Color.WHITE,0.5f)

        group.setSize(size,size)
        group.addActor(circle)
        group.addActor(getImage("ResourceIcons/${resourceName}")
                .apply { setSize(size*0.8f,size*0.8f); center(group) })
        if(resource.resourceType==ResourceType.Luxury){
            val happiness = getStatIcon("Happiness")
            happiness.setSize(size/2,size/2)
            happiness.x = group.width-happiness.width
            group.addActor(happiness)
        }
        if(resource.resourceType==ResourceType.Strategic){
            val production = getStatIcon("Production")
            production.setSize(size/2,size/2)
            production.x = group.width-production.width
            group.addActor(production)
        }
        return group
    }
}
