package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.Nation
import com.unciv.models.gamebasics.tile.ResourceType
import core.java.nativefont.NativeFont
import core.java.nativefont.NativeFontPaint

object ImageGetter {
    private const val whiteDotLocation = "OtherIcons/whiteDot"

    // When we used to load images directly from different files, without using a texture atlas,
    // The draw() phase of the main screen would take a really long time because the BatchRenderer would
    // always have to switch between like 170 different textures.
    // So, we now use TexturePacker in the DesktopLauncher class to pack all the different images into single images,
    // and the atlas is what tells us what was packed where.
    var atlas = TextureAtlas("game.atlas")

    // We then shove all the drawables into a hashmap, because the atlas specifically tells us
    //   that the search on it is inefficient
    val textureRegionDrawables = HashMap<String,TextureRegionDrawable>()

    init{
        setTextureRegionDrawables()
    }

    fun setTextureRegionDrawables(){
        textureRegionDrawables.clear()
        for(region in atlas.regions){
            val drawable =TextureRegionDrawable(region)
            textureRegionDrawables[region.name] = drawable
        }
    }

    fun refreshAltas() {
        atlas.dispose() // To avoid OutOfMemory exceptions
        atlas = TextureAtlas("game.atlas")
        setTextureRegionDrawables()
        if (UncivGame.Current.settings.replaceImageWithEmoji) {
            textureRegionDrawables["StatIcons/Culture"]= getfontDrawable("🎵")
            textureRegionDrawables["StatIcons/Food"]= getfontDrawable("🍏")
            textureRegionDrawables["StatIcons/Gold"]= getfontDrawable("💰")
            textureRegionDrawables["StatIcons/Happiness"]= getfontDrawable("😊")
            textureRegionDrawables["StatIcons/Malcontent"]= getfontDrawable("😡")
            textureRegionDrawables["StatIcons/Population"]= getfontDrawable("👨")
            textureRegionDrawables["StatIcons/Production"]= getfontDrawable("🛠️")
            textureRegionDrawables["StatIcons/Science"]= getfontDrawable("💡")
            textureRegionDrawables["StatIcons/Strength"]= getfontDrawable("🛡️")
            textureRegionDrawables["StatIcons/Movement"]= getfontDrawable("👣")
            textureRegionDrawables["StatIcons/RangedStrength"]= getfontDrawable("🏹️")
            textureRegionDrawables["StatIcons/Range"]= getfontDrawable("🎯")
            textureRegionDrawables["ResourceIcons/Bananas"]= getfontDrawable("🍌")
            textureRegionDrawables["ResourceIcons/Cattle"]= getfontDrawable("🐮")
            textureRegionDrawables["ResourceIcons/Deer"]= getfontDrawable("🦌")
            textureRegionDrawables["ResourceIcons/Fish"]= getfontDrawable("🐠")
            textureRegionDrawables["ResourceIcons/Sheep"]= getfontDrawable("🐑")
            textureRegionDrawables["ResourceIcons/Wheat"]= getfontDrawable("🌾")
            textureRegionDrawables["ResourceIcons/Gems"]= getfontDrawable("💎")
            textureRegionDrawables["ResourceIcons/Wine"]= getfontDrawable("🍷")
            textureRegionDrawables["ResourceIcons/Whales"]= getfontDrawable("🐋")
            textureRegionDrawables["TileSets/Default/OasisOverlay"]= getfontDrawable("🌵")
            textureRegionDrawables["TileSets/Default/ForestOverlay"]= getfontDrawable("🌲")
            textureRegionDrawables["TileSets/Default/JungleOverlay"]= getfontDrawable("🌳")
            textureRegionDrawables["TileSets/Default/MountainOverlay"]= getfontDrawable("🏔️️")
            textureRegionDrawables["OtherIcons/Fire"]= getfontDrawable("🔥")
            textureRegionDrawables["OtherIcons/Sleep"]= getfontDrawable("💤")
            textureRegionDrawables["OtherIcons/DisbandUnit"]= getfontDrawable("☠️")// MacOS can't show
            textureRegionDrawables["OtherIcons/Star"]= getfontDrawable("⭐️️")// MacOS can't show
            textureRegionDrawables["OtherIcons/Aircraft"]= getfontDrawable("✈️️️")// MacOS can't show
            textureRegionDrawables["OtherIcons/Stop"]= getfontDrawable("⛔️️️️")// MacOS can't show
        }
    }

    fun getfontDrawable(string: String, size: Int= 100): TextureRegionDrawable {
        val fontPixmap = NativeFont.getListener().getFontPixmap(string, NativeFontPaint(size))
        return TextureRegionDrawable(TextureRegion(Texture(fontPixmap)))
    }

    fun getWhiteDot() =  getImage(whiteDotLocation)
    fun getDot(dotColor: Color) = getWhiteDot().apply { color = dotColor}

    fun getExternalImage(fileName:String): Image {
        return Image(TextureRegion(Texture("ExtraImages/$fileName")))
    }

    fun getImage(fileName: String): Image {
        return Image(getDrawable(fileName))
    }

    private fun getDrawable(fileName: String): TextureRegionDrawable {
        if(textureRegionDrawables.containsKey(fileName)) return textureRegionDrawables[fileName]!!
        else return textureRegionDrawables[whiteDotLocation]!!
    }

    fun getTableBackground(tintColor: Color?=null): Drawable? {
        val drawable = getDrawable("OtherIcons/civTableBackground")
        drawable.minHeight=0f
        drawable.minWidth=0f
        if(tintColor==null) return drawable
        return drawable.tint(tintColor)
    }


    fun imageExists(fileName:String) = textureRegionDrawables.containsKey(fileName)
    fun techIconExists(techName:String) = imageExists("TechIcons/$techName")

    fun getStatIcon(statName: String): Image {
        return getImage("StatIcons/$statName")
                .apply { setSize(20f,20f)}
    }

    fun getUnitIcon(unitName:String,color:Color= Color.BLACK):Image{
        return getImage("UnitIcons/$unitName").apply { this.color=color }
    }

    fun getNationIndicator(nation: Nation, size:Float): IconCircleGroup {
        val civIconName = if(nation.isCityState()) "CityState" else nation.name
        if(nationIconExists(civIconName)){
            val cityStateIcon = getNationIcon(civIconName)
            cityStateIcon.color = nation.getInnerColor()
            return cityStateIcon.surroundWithCircle(size*0.9f).apply { circle.color = nation.getOuterColor() }
                    .surroundWithCircle(size,false).apply { circle.color=nation.getInnerColor() }
        }
        else{
            return getCircle().apply { color = nation.getOuterColor() }
                    .surroundWithCircle(size).apply { circle.color = nation.getInnerColor() }

        }
    }

    fun nationIconExists(nation:String) = imageExists("NationIcons/$nation")
    fun getNationIcon(nation:String) = getImage("NationIcons/$nation")

    val foodCircleColor =  colorFromRGB(129, 199, 132)
    val productionCircleColor = Color.BROWN.cpy().lerp(Color.WHITE,0.5f)!!
    val goldCircleColor = Color.GOLD.cpy().lerp(Color.WHITE,0.5f)!!
    fun getImprovementIcon(improvementName:String, size:Float=20f):Actor{
        if(improvementName.startsWith("Remove"))
            return getImage("OtherIcons/Stop")
        if(improvementName.startsWith("StartingLocation ")){
            val nationName = improvementName.removePrefix("StartingLocation ")
            val nation = GameBasics.Nations[nationName]!!
            return getNationIndicator(nation,size)
        }

        val iconGroup = getImage("ImprovementIcons/$improvementName").surroundWithCircle(size)

        val improvement = GameBasics.TileImprovements[improvementName]!!
        when {
            improvement.food>0 -> iconGroup.circle.color= foodCircleColor
            improvement.production>0 -> iconGroup.circle.color= productionCircleColor
            improvement.gold>0 -> iconGroup.circle.color= goldCircleColor
            improvement.science>0 -> iconGroup.circle.color= Color.BLUE.cpy().lerp(Color.WHITE,0.5f)
            improvement.culture>0 -> iconGroup.circle.color= Color.PURPLE.cpy().lerp(Color.WHITE,0.5f)
        }

        return iconGroup
    }

    fun getConstructionImage(construction: String): Image {
        if(GameBasics.Buildings.containsKey(construction)) return getImage("BuildingIcons/$construction")
        if(GameBasics.Units.containsKey(construction)) return getUnitIcon(construction)
        if(construction=="Nothing") return getImage("OtherIcons/Stop")
        return getStatIcon(construction)
    }

    fun getPromotionIcon(promotionName:String): Actor {
        var level = 0

        when {
            promotionName.endsWith(" I") -> level=1
            promotionName.endsWith(" II") -> level=2
            promotionName.endsWith(" III") -> level=3
        }

        val basePromotionName = if(level==0) promotionName
        else promotionName.substring(0, promotionName.length-level-1)

        if(imageExists("UnitPromotionIcons/$basePromotionName")) {
            val icon = getImage("UnitPromotionIcons/$basePromotionName")
            icon.color = colorFromRGB(255,226,0)
            val circle = icon.surroundWithCircle(30f)
            circle.circle.color = colorFromRGB(0,12,49)
            if(level!=0){
                val starTable = Table().apply { defaults().pad(2f) }
                for(i in 1..level) starTable.add(getImage("OtherIcons/Star")).size(8f)
                starTable.centerX(circle)
                starTable.y=5f
                circle.addActor(starTable)
            }
            return circle
        }
        return getImage("UnitPromotionIcons/" + promotionName.replace(' ', '_') + "_(Civ5)")
    }

    fun getBlue() = Color(0x004085bf)

    fun getCircle() = getImage("OtherIcons/Circle")

    fun getBackground(color:Color): Drawable {
        val drawable = getDrawable("OtherIcons/TableBackground")
        drawable.minHeight=0f
        drawable.minWidth=0f
        return drawable.tint(color)
    }


    fun getResourceImage(resourceName: String, size:Float): Actor {
        val iconGroup = getImage("ResourceIcons/$resourceName").surroundWithCircle(size)
        val resource = GameBasics.TileResources[resourceName]!!
        when {
            resource.food>0 -> iconGroup.circle.color= foodCircleColor
            resource.production>0 -> iconGroup.circle.color= productionCircleColor
            resource.gold>0 -> iconGroup.circle.color= goldCircleColor
        }

        if(resource.resourceType==ResourceType.Luxury){
            val happiness = getStatIcon("Happiness")
            happiness.setSize(size/2,size/2)
            happiness.x = iconGroup.width-happiness.width
            iconGroup.addActor(happiness)
        }
        if(resource.resourceType==ResourceType.Strategic){
            val production = getStatIcon("Production")
            production.setSize(size/2,size/2)
            production.x = iconGroup.width-production.width
            iconGroup.addActor(production)
        }
        return iconGroup
    }

    fun getTechIconGroup(techName: String, circleSize: Float): Group {
        var techIconColor = Color.WHITE
        when (GameBasics.Technologies[techName]!!.era().name) {
            "Ancient" -> techIconColor = colorFromRGB(255, 87, 35)
            "Classical" -> techIconColor = colorFromRGB(233, 31, 99)
            "Medieval" -> techIconColor = colorFromRGB(157, 39, 176)
            "Renaissance" -> techIconColor = colorFromRGB(104, 58, 183)
            "Industrial" -> techIconColor = colorFromRGB(63, 81, 182)
            "Modern" -> techIconColor = colorFromRGB(33, 150, 243)
            "Information" -> techIconColor = colorFromRGB(0, 150, 136)
            "Future" -> techIconColor = colorFromRGB(76,176,81)
        }
        return getImage("TechIcons/$techName").apply { color = techIconColor.lerp(Color.BLACK,0.6f) }
                .surroundWithCircle(circleSize)
    }

    fun getProgressBarVertical(width:Float,height:Float,percentComplete:Float,progressColor:Color,backgroundColor:Color): Table {
        val advancementGroup = Table()
        val completionHeight = height * percentComplete
        advancementGroup.add(getImage(whiteDotLocation).apply { color = backgroundColor })
                .size(width, height - completionHeight).row()
        advancementGroup.add(getImage(whiteDotLocation).apply { color = progressColor }).size(width, completionHeight)
        advancementGroup.pack()
        return advancementGroup
    }

    fun getHealthBar(currentHealth: Float, maxHealth: Float, healthBarSize: Float): Table {
        val healthPercent = currentHealth / maxHealth
        val healthBar = Table()

        val healthPartOfBar = getWhiteDot()
        healthPartOfBar.color = when {
            healthPercent > 2 / 3f -> Color.GREEN
            healthPercent > 1 / 3f -> Color.ORANGE
            else -> Color.RED
        }
        healthBar.add(healthPartOfBar).size(healthBarSize * healthPercent, 5f)

        val emptyPartOfBar = getDot(Color.BLACK)
        healthBar.add(emptyPartOfBar).size(healthBarSize * (1 - healthPercent), 5f)

        healthBar.pad(1f)
        healthBar.pack()
        healthBar.background = getBackground(Color.BLACK)
        return healthBar
    }

    fun getLine(startX:Float,startY:Float,endX:Float,endY:Float, width:Float): Image {
        /** The simplest way to draw a line between 2 points seems to be:
         * A. Get a pixel dot, set its width to the required length (hypotenuse)
         * B. Set its rotational center, and set its rotation
         * C. Center it on the point where you want its center to be
         */

        // A
        val line = getWhiteDot()
        val deltaX = (startX-endX).toDouble()
        val deltaY = (startY-endY).toDouble()
        line.width = Math.sqrt(deltaX*deltaX+deltaY*deltaY).toFloat()
        line.height = width // the width of the line, is the height of the

        // B
        line.setOrigin(Align.center)
        val radiansToDegrees = 180 / Math.PI
        line.rotation = (Math.atan2(deltaY, deltaX) * radiansToDegrees).toFloat()

        // C
        line.x = (startX+endX)/2 - line.width/2
        line.y = (startY+endY)/2 - line.height/2

        return line
    }
}

