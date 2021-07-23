package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.ruleset.Era
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.stats.Stats
import com.unciv.models.tilesets.TileSetCache
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ImageGetter {
    private const val whiteDotLocation = "OtherIcons/whiteDot"

    // When we used to load images directly from different files, without using a texture atlas,
    // The draw() phase of the main screen would take a really long time because the BatchRenderer would
    // always have to switch between like 170 different textures.
    // So, we now use TexturePacker in the DesktopLauncher class to pack all the different images into single images,
    // and the atlas is what tells us what was packed where.
    lateinit var atlas: TextureAtlas
    private val atlases = HashMap<String, TextureAtlas>()
    var ruleset = Ruleset()

    // We then shove all the drawables into a hashmap, because the atlas specifically tells us
    //   that the search on it is inefficient
    internal val textureRegionDrawables = HashMap<String, TextureRegionDrawable>()

    fun resetAtlases() {
        atlases.values.forEach { it.dispose() }
        atlases.clear()
        atlas = TextureAtlas("game.atlas")
        atlases["game"] = atlas
    }

    /** Required every time the ruleset changes, in order to load mod-specific images */
    fun setNewRuleset(ruleset: Ruleset) {
        this.ruleset = ruleset
        textureRegionDrawables.clear()
        // These are the drawables from the base game
        for (region in atlas.regions) {
            val drawable = TextureRegionDrawable(region)
            textureRegionDrawables[region.name] = drawable
        }

        for (singleImagesFolder in sequenceOf("BuildingIcons", "FlagIcons", "UnitIcons")) {
            if (!atlases.containsKey(singleImagesFolder)) atlases[singleImagesFolder] = TextureAtlas("$singleImagesFolder.atlas")
            val tempAtlas = atlases[singleImagesFolder]!!
            for (region in tempAtlas.regions) {
                val drawable = TextureRegionDrawable(region)
                textureRegionDrawables["$singleImagesFolder/" + region.name] = drawable
            }
        }

        if (!atlases.containsKey("Skin")) atlases["Skin"] = TextureAtlas("Skin.atlas")
        for (region in atlases["Skin"]!!.regions) {
            val drawable = TextureRegionDrawable(region)
            textureRegionDrawables["Skin/" + region.name] = drawable
        }

        // These are from the mods
        for (mod in UncivGame.Current.settings.visualMods + ruleset.mods) {
            val modAtlasFile = Gdx.files.local("mods/$mod/game.atlas")
            if (!modAtlasFile.exists()) continue

            if (!atlases.containsKey(mod)) atlases[mod] = TextureAtlas(modAtlasFile)
            val modAtlas = atlases[mod]!!

            for (region in modAtlas.regions) {
                val drawable = TextureRegionDrawable(region)
                textureRegionDrawables[region.name] = drawable
            }
        }

        TileSetCache.assembleTileSetConfigs(ruleset.mods)
    }


    /**
     * Colors a multilayer image and returns it as a list of layers (Image).
     *
     * @param   baseFileName    The filename of the base image.
     *                              For example: TileSets/FantasyHex/Units/Warrior
     *
     * @param   colors          The list of colors, one per layer. No coloring is applied to layers
     *                              whose color is null.
     *
     * @return  The list of layers colored. The layers are sorted by NUMBER (see example below) order
     *              and colors are applied, one per layer, in the same order. If a color is null, no
     *              coloring is performed on such layer (it stays as it is). If there are less colors
     *              than layers, the last layers are not colored. Defaults to an empty list if there
     *              is no layer corresponding to baseFileName.
     *
     * Example:
     *      getLayeredImageColored("TileSets/FantasyHex/Units/Warrior", null, Color.GOLD, Color.RED)
     *
     *      All images in the atlas that match the pattern "TileSets/FantasyHex/Units/Warrior" or
     *      "TileSets/FantasyHex/Units/Warrior-NUMBER" are retrieved. NUMBERs must start from 1 and
     *      be incremented by 1 per layer. If the n-th NUMBER is missing, the (n-1)-th layer is the
     *      last one retrieved:
     *      Given the layer names:
     *          - TileSets/FantasyHex/Units/Warrior
     *          - TileSets/FantasyHex/Units/Warrior-1
     *          - TileSets/FantasyHex/Units/Warrior-2
     *          - TileSets/FantasyHex/Units/Warrior-4
     *      Only the base layer and layers 1 and 2 are retrieved.
     *      The method returns then a list in which first layer has unmodified colors, the second is
     *      colored in GOLD and the third in RED.
     */
    fun getLayeredImageColored(baseFileName: String, vararg colors: Color?): ArrayList<Image> {
        if (!imageExists(baseFileName))
            return arrayListOf()

        val layerNames = mutableListOf(baseFileName)
        val layerList = arrayListOf<Image>()

        var i = 1
        while (imageExists("$baseFileName-$i")) {
            layerNames.add("$baseFileName-$i")
            ++i
        }

        for (i in layerNames.indices) {
            val image = getImage(layerNames[i])
            if (i < colors.size && colors[i] != null)
                image.color = colors[i]
            layerList.add(image)
        }

        return layerList
    }

    fun getWhiteDot() = getImage(whiteDotLocation)
    fun getDot(dotColor: Color) = getWhiteDot().apply { color = dotColor }

    fun getExternalImage(fileName: String): Image {
        return Image(TextureRegion(Texture("ExtraImages/$fileName")))
    }

    fun getImage(fileName: String): Image {
        return Image(getDrawable(fileName))
    }

    fun getDrawable(fileName: String): TextureRegionDrawable {
        return if (textureRegionDrawables.containsKey(fileName)) textureRegionDrawables[fileName]!!
        else textureRegionDrawables[whiteDotLocation]!!
    }

    fun getRoundedEdgeRectangle(tintColor: Color? = null): NinePatchDrawable {
        val region = getDrawable("Skin/roundedEdgeRectangle").region
        val drawable = NinePatchDrawable(NinePatch(region, 25, 25, 0, 0))
        drawable.setPadding(5f, 15f, 5f, 15f)

        if (tintColor == null) return drawable
        return drawable.tint(tintColor)
    }

    fun getRectangleWithOutline(): NinePatchDrawable {
        val region = getDrawable("Skin/rectangleWithOutline").region
        return NinePatchDrawable(NinePatch(region, 1, 1, 1, 1))
    }

    fun getSelectBox(): NinePatchDrawable {
        val region = getDrawable("Skin/select-box").region
        return NinePatchDrawable(NinePatch(region, 10, 25, 5, 5))
    }

    fun getSelectBoxPressed(): NinePatchDrawable {
        val region = getDrawable("Skin/select-box-pressed").region
        return NinePatchDrawable(NinePatch(region, 10, 25, 5, 5))
    }

    fun getCheckBox(): Drawable {
        return getDrawable("Skin/checkbox")
    }

    fun getCheckBoxPressed(): Drawable {
        return getDrawable("Skin/checkbox-pressed")
    }

    fun imageExists(fileName: String) = textureRegionDrawables.containsKey(fileName)
    fun techIconExists(techName: String) = imageExists("TechIcons/$techName")

    fun getStatIcon(statName: String): Image {
        return getImage("StatIcons/$statName")
                .apply { setSize(20f, 20f) }
    }

    fun getUnitIcon(unitName: String, color: Color = Color.BLACK): Image {
        return getImage("UnitIcons/$unitName").apply { this.color = color }
    }



    fun getNationIndicator(nation: Nation, size: Float): IconCircleGroup {
        val civIconName = if (nation.isCityState()) "CityState" else nation.name
        return if (nationIconExists(civIconName)) {
            val cityStateIcon = getNationIcon(civIconName)
            cityStateIcon.color = nation.getInnerColor()
            cityStateIcon.surroundWithCircle(size * 0.9f).apply { circle.color = nation.getOuterColor() }
                    .surroundWithCircle(size, false).apply { circle.color = nation.getInnerColor() }
        } else getCircle().apply { color = nation.getOuterColor() }
                .surroundWithCircle(size).apply { circle.color = nation.getInnerColor() }
    }

    private fun nationIconExists(nation: String) = imageExists("NationIcons/$nation")
    fun getNationIcon(nation: String) = getImage("NationIcons/$nation")
    
    fun wonderImageExists(wonderName: String) = imageExists("WonderImages/$wonderName")
    fun getWonderImage(wonderName: String) = getImage("WonderImages/$wonderName")

    val foodCircleColor = colorFromRGB(129, 199, 132)
    private val productionCircleColor = Color.BROWN.cpy().lerp(Color.WHITE, 0.5f)
    private val goldCircleColor = Color.GOLD.cpy().lerp(Color.WHITE, 0.5f)
    private val cultureCircleColor = Color.PURPLE.cpy().lerp(Color.WHITE, 0.5f)
    private val scienceCircleColor = Color.BLUE.cpy().lerp(Color.WHITE, 0.5f)
    private fun getColorFromStats(stats: Stats) = when {
        stats.food > 0 -> foodCircleColor
        stats.production > 0 -> productionCircleColor
        stats.gold > 0 -> goldCircleColor
        stats.culture > 0 -> cultureCircleColor
        stats.science > 0 -> scienceCircleColor
        else -> Color.WHITE
    }


    fun getImprovementIcon(improvementName: String, size: Float = 20f): Actor {
        if (improvementName.startsWith("Remove") || improvementName == Constants.cancelImprovementOrder)
            return Table().apply { add(getImage("OtherIcons/Stop")).size(size) }
        if (improvementName.startsWith("StartingLocation ")) {
            val nationName = improvementName.removePrefix("StartingLocation ")
            val nation = ruleset.nations[nationName]!!
            return getNationIndicator(nation, size)
        }

        val iconGroup = getImage("ImprovementIcons/$improvementName").surroundWithCircle(size)

        val improvement = ruleset.tileImprovements[improvementName]
        if (improvement != null)
            iconGroup.circle.color = getColorFromStats(improvement)

        return iconGroup
    }

    fun getConstructionImage(construction: String): Image {
        if (ruleset.buildings.containsKey(construction)) return getImage("BuildingIcons/$construction")
        if (ruleset.units.containsKey(construction)) return getUnitIcon(construction)
        if (construction == "Nothing") return getImage("OtherIcons/Sleep")
        return getStatIcon(construction)
    }

    fun getPromotionIcon(promotionName: String, size: Float = 30f): Actor {
        val level = when {
            promotionName.endsWith(" I") -> 1
            promotionName.endsWith(" II") -> 2
            promotionName.endsWith(" III") -> 3
            else -> 0
        }

        val basePromotionName = if (level == 0) promotionName
        else promotionName.substring(0, promotionName.length - level - 1)

        val circle = getImage("UnitPromotionIcons/$basePromotionName")
                .apply { color = colorFromRGB(255, 226, 0) }
                .surroundWithCircle(size)
                .apply { circle.color = colorFromRGB(0, 12, 49) }
        if (level != 0) {
            val starTable = Table().apply { defaults().pad(2f) }
            for (i in 1..level) starTable.add(getImage("OtherIcons/Star")).size(size / 3f)
            starTable.centerX(circle)
            starTable.y = size / 6f
            circle.addActor(starTable)
        }
        return circle
    }
    
    fun getReligionIcon(iconName: String): Image {
        return getImage("ReligionIcons/$iconName")
    }

    fun getBlue() = Color(0x004085bf)

    fun getCircle() = getImage("OtherIcons/Circle")
    fun getTriangle() = getImage("OtherIcons/Triangle")

    fun getBackground(color: Color): Drawable {
        val drawable = getDrawable("")
        drawable.minHeight = 0f
        drawable.minWidth = 0f
        return drawable.tint(color)
    }


    fun getResourceImage(resourceName: String, size: Float): Actor {
        val iconGroup = getImage("ResourceIcons/$resourceName").surroundWithCircle(size)
        val resource = ruleset.tileResources[resourceName]
                ?: return iconGroup // This is the result of a bad modding setup, just give em an empty circle. Their problem.
        iconGroup.circle.color = getColorFromStats(resource)

        if (resource.resourceType == ResourceType.Luxury) {
            val happiness = getStatIcon("Happiness")
            happiness.setSize(size / 2, size / 2)
            happiness.x = iconGroup.width - happiness.width
            iconGroup.addActor(happiness)
        }
        if (resource.resourceType == ResourceType.Strategic) {
            val production = getStatIcon("Production")
            production.setSize(size / 2, size / 2)
            production.x = iconGroup.width - production.width
            iconGroup.addActor(production)
        }
        return iconGroup
    }

    fun getTechIconGroup(techName: String, circleSize: Float) = getTechIcon(techName).surroundWithCircle(circleSize)

    fun getTechIcon(techName: String): Image {
        val era = ruleset.technologies[techName]!!.era()
        val techIconColor = 
            if (era !in ruleset.eras) Era().getColor()
            else ruleset.eras[ruleset.technologies[techName]!!.era()]!!.getColor()
        return getImage("TechIcons/$techName").apply { color = techIconColor.lerp(Color.BLACK, 0.6f) }
    }

    fun getProgressBarVertical(width: Float, height: Float, percentComplete: Float, progressColor: Color, backgroundColor: Color): Group {
        return VerticalProgressBar(width, height)
                .addColor(backgroundColor, 1f)
                .addColor(progressColor, percentComplete)
    }

    class VerticalProgressBar(width: Float, height: Float):Group() {
        init {
            setSize(width, height)
        }

        fun addColor(color: Color, percentage: Float): VerticalProgressBar {
            val bar = getWhiteDot()
            bar.color = color
            bar.setSize(width, height *  max(min(percentage, 1f),0f)) //clamp between 0 and 1
            addActor(bar)
            return this
        }
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

    fun getLine(startX: Float, startY: Float, endX: Float, endY: Float, width: Float): Image {
        /** The simplest way to draw a line between 2 points seems to be:
         * A. Get a pixel dot, set its width to the required length (hypotenuse)
         * B. Set its rotational center, and set its rotation
         * C. Center it on the point where you want its center to be
         */

        // A
        val line = getWhiteDot()
        val deltaX = (startX - endX).toDouble()
        val deltaY = (startY - endY).toDouble()
        line.width = sqrt(deltaX * deltaX + deltaY * deltaY).toFloat()
        line.height = width // the width of the line, is the height of the

        // B
        line.setOrigin(Align.center)
        val radiansToDegrees = 180 / Math.PI
        line.rotation = (atan2(deltaY, deltaX) * radiansToDegrees).toFloat()

        // C
        line.x = (startX + endX) / 2 - line.width / 2
        line.y = (startY + endY) / 2 - line.height / 2

        return line
    }

    fun getSpecialistIcon(color: Color): Image {
        val specialist = getImage("StatIcons/Specialist")
        specialist.color = color
        return specialist
    }

    fun getAvailableTilesets() = textureRegionDrawables.keys.asSequence().filter { it.startsWith("TileSets") }
            .map { it.split("/")[1] }.distinct()
}