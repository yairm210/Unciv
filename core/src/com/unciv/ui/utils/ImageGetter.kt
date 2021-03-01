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
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.stats.Stats
import kotlin.math.max
import kotlin.math.min

object ImageGetter {
    private const val whiteDotLocation = "OtherIcons/whiteDot"

    // When we used to load images directly from different files, without using a texture atlas,
    // The draw() phase of the main screen would take a really long time because the BatchRenderer would
    // always have to switch between like 170 different textures.
    // So, we now use TexturePacker in the DesktopLauncher class to pack all the different images into single images,
    // and the atlas is what tells us what was packed where.
    var atlas = TextureAtlas("game.atlas")
    var ruleset = Ruleset()

    // We then shove all the drawables into a hashmap, because the atlas specifically tells us
    //   that the search on it is inefficient
    val textureRegionDrawables = HashMap<String, TextureRegionDrawable>()

    init {
        reload()
    }

    fun setNewRuleset(ruleset: Ruleset) {
        this.ruleset = ruleset
        reload()
    }

    /** Required every time the ruleset changes, in order to load mod-specific images */
    fun reload() {
        textureRegionDrawables.clear()
        // These are the drawables from the base game
        for (region in atlas.regions) {
            val drawable = TextureRegionDrawable(region)
            textureRegionDrawables[region.name] = drawable
        }

        for (singleImagesFolder in sequenceOf("BuildingIcons", "FlagIcons", "UnitIcons")) {
            val tempAtlas = TextureAtlas("$singleImagesFolder.atlas")
            for (region in tempAtlas.regions) {
                val drawable = TextureRegionDrawable(region)
                textureRegionDrawables["$singleImagesFolder/" + region.name] = drawable
            }
        }

        for (folder in Gdx.files.internal("SingleImages").list())
            for (image in folder.list()) {
                val texture = Texture(image)
                // Since these aren't part of the packed texture we need to set this manually for each one
                // Unfortunately since it's not power-of-2
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                textureRegionDrawables[folder.name() + "/" + image.nameWithoutExtension()] = TextureRegionDrawable(texture)
            }

        // These are from the mods
        for (mod in ruleset.mods) {
            val modAtlasFile = Gdx.files.local("mods/$mod/game.atlas")
            if (!modAtlasFile.exists()) continue
            val modAtlas = TextureAtlas(modAtlasFile)
            for (region in modAtlas.regions) {
                val drawable = TextureRegionDrawable(region)
                textureRegionDrawables[region.name] = drawable
            }
        }
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
        if (textureRegionDrawables.containsKey(fileName)) return textureRegionDrawables[fileName]!!
        else return textureRegionDrawables[whiteDotLocation]!!
    }

    fun getRoundedEdgeTableBackground(tintColor: Color? = null): NinePatchDrawable {
        val drawable = NinePatchDrawable(NinePatch(getDrawable("OtherIcons/buttonBackground").region, 25, 25, 0, 0)).apply {
            setPadding(5f, 15f, 5f, 15f)
        }
        if (tintColor == null) return drawable
        return drawable.tint(tintColor)
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
        if (nationIconExists(civIconName)) {
            val cityStateIcon = getNationIcon(civIconName)
            cityStateIcon.color = nation.getInnerColor()
            return cityStateIcon.surroundWithCircle(size * 0.9f).apply { circle.color = nation.getOuterColor() }
                    .surroundWithCircle(size, false).apply { circle.color = nation.getInnerColor() }
        } else {
            return getCircle().apply { color = nation.getOuterColor() }
                    .surroundWithCircle(size).apply { circle.color = nation.getInnerColor() }

        }
    }

    fun nationIconExists(nation: String) = imageExists("NationIcons/$nation")
    fun getNationIcon(nation: String) = getImage("NationIcons/$nation")

    val foodCircleColor = colorFromRGB(129, 199, 132)
    val productionCircleColor = Color.BROWN.cpy().lerp(Color.WHITE, 0.5f)!!
    val goldCircleColor = Color.GOLD.cpy().lerp(Color.WHITE, 0.5f)!!
    val cultureCircleColor = Color.PURPLE.cpy().lerp(Color.WHITE, 0.5f)!!
    val scienceCircleColor = Color.BLUE.cpy().lerp(Color.WHITE, 0.5f)!!
    fun getColorFromStats(stats: Stats) = when {
        stats.food > 0 -> foodCircleColor
        stats.production > 0 -> productionCircleColor
        stats.gold > 0 -> goldCircleColor
        stats.culture > 0 -> cultureCircleColor
        stats.science > 0 -> scienceCircleColor
        else -> Color.WHITE
    }


    fun getImprovementIcon(improvementName: String, size: Float = 20f): Actor {
        if (improvementName.startsWith("Remove") || improvementName == Constants.cancelImprovementOrder)
            return getImage("OtherIcons/Stop")
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

    fun getPromotionIcon(promotionName: String): Actor {
        var level = 0

        when {
            promotionName.endsWith(" I") -> level = 1
            promotionName.endsWith(" II") -> level = 2
            promotionName.endsWith(" III") -> level = 3
        }

        val basePromotionName = if (level == 0) promotionName
        else promotionName.substring(0, promotionName.length - level - 1)

        if (imageExists("UnitPromotionIcons/$basePromotionName")) {
            val icon = getImage("UnitPromotionIcons/$basePromotionName")
            icon.color = colorFromRGB(255, 226, 0)
            val circle = icon.surroundWithCircle(30f)
            circle.circle.color = colorFromRGB(0, 12, 49)
            if (level != 0) {
                val starTable = Table().apply { defaults().pad(2f) }
                for (i in 1..level) starTable.add(getImage("OtherIcons/Star")).size(8f)
                starTable.centerX(circle)
                starTable.y = 5f
                circle.addActor(starTable)
            }
            return circle
        }
        return getImage("UnitPromotionIcons/" + promotionName.replace(' ', '_') + "_(Civ5)")
    }

    fun getBlue() = Color(0x004085bf)

    fun getCircle() = getImage("OtherIcons/Circle")
    fun getTriangle() = getImage("OtherIcons/Triangle")

    fun getBackground(color: Color): Drawable {
        val drawable = getDrawable("OtherIcons/TableBackground")
        drawable.minHeight = 0f
        drawable.minWidth = 0f
        return drawable.tint(color)
    }


    fun getResourceImage(resourceName: String, size: Float): Actor {
        val iconGroup = getImage("ResourceIcons/$resourceName").surroundWithCircle(size)
        val resource = ruleset.tileResources[resourceName]
        if (resource == null) return iconGroup // This is the result of a bad modding setup, just give em an empty circle. Their problem.
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

    fun getTechIconGroup(techName: String, circleSize: Float): Group {
        val techIconColor = when (ruleset.technologies[techName]!!.era()) {
            Constants.ancientEra -> colorFromRGB(255, 87, 35)
            Constants.classicalEra -> colorFromRGB(233, 31, 99)
            Constants.medievalEra -> colorFromRGB(157, 39, 176)
            Constants.renaissanceEra -> colorFromRGB(104, 58, 183)
            Constants.industrialEra -> colorFromRGB(63, 81, 182)
            Constants.modernEra -> colorFromRGB(33, 150, 243)
            Constants.informationEra -> colorFromRGB(0, 150, 136)
            Constants.futureEra -> colorFromRGB(76, 176, 81)
            else -> Color.WHITE.cpy()
        }
        return getImage("TechIcons/$techName").apply { color = techIconColor.lerp(Color.BLACK, 0.6f) }
                .surroundWithCircle(circleSize)
    }

    fun getProgressBarVertical(width: Float, height: Float, percentComplete: Float, progressColor: Color, backgroundColor: Color): Table {
        val advancementGroup = Table()
        var completionHeight = height * percentComplete
        if (completionHeight > height)
            completionHeight = height
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
        line.width = Math.sqrt(deltaX * deltaX + deltaY * deltaY).toFloat()
        line.height = width // the width of the line, is the height of the

        // B
        line.setOrigin(Align.center)
        val radiansToDegrees = 180 / Math.PI
        line.rotation = (Math.atan2(deltaY, deltaX) * radiansToDegrees).toFloat()

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
}