package com.unciv.ui.images

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.city.PerpetualConstruction
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.skins.SkinCache
import com.unciv.models.stats.Stats
import com.unciv.models.tilesets.TileSetCache
import com.unciv.ui.utils.*
import com.unciv.ui.utils.extensions.*
import com.unciv.utils.debug
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ImageGetter {
    private const val whiteDotLocation = "OtherIcons/whiteDot"

    // We use texture atlases to minimize texture swapping - see https://yairm210.medium.com/the-libgdx-performance-guide-1d068a84e181
    lateinit var atlas: TextureAtlas
    private val atlases = HashMap<String, TextureAtlas>()
    var ruleset = Ruleset()

    // We then shove all the drawables into a hashmap, because the atlas specifically tells us
    //   that the search on it is inefficient
    private val textureRegionDrawables = HashMap<String, TextureRegionDrawable>()
    private val ninePatchDrawables = HashMap<String, NinePatchDrawable>()

    fun resetAtlases() {
        atlases.values.forEach { it.dispose() }
        atlases.clear()
        atlas = TextureAtlas("game.atlas")
        atlases["game"] = atlas
    }

    /** Required every time the ruleset changes, in order to load mod-specific images */
    fun setNewRuleset(ruleset: Ruleset) {
        ImageGetter.ruleset = ruleset
        textureRegionDrawables.clear()
        // These are the drawables from the base game
        for (region in atlas.regions) {
            val drawable = TextureRegionDrawable(region)
            textureRegionDrawables[region.name] = drawable
        }

        // Load base (except game.atlas which is already loaded)
        loadModAtlases("", Gdx.files.internal(""))

        // These are from the mods
        val visualMods = UncivGame.Current.settings.visualMods + ruleset.mods
        for (mod in visualMods) {
            loadModAtlases(mod, Gdx.files.local("mods/$mod"))
        }

        TileSetCache.assembleTileSetConfigs(ruleset.mods)
        SkinCache.assembleSkinConfigs(ruleset.mods)
    }

    /** Loads all atlas/texture files from a folder, as controlled by an Atlases.json */
    fun loadModAtlases(mod: String, folder: FileHandle) {
        // See #4993 - you can't .list() on a jar file, so the ImagePacker leaves us the list of actual atlases.
        val controlFile = folder.child("Atlases.json")
        val fileNames = (if (controlFile.exists()) json().fromJson(Array<String>::class.java, controlFile)
            else emptyArray()).toMutableList()
        if (mod.isNotEmpty()) fileNames += "game"
        for (fileName in fileNames) {
            val file = folder.child("$fileName.atlas")
            if (!file.exists()) continue
            val extraAtlas = if (mod.isEmpty()) fileName else if (fileName == "game") mod else "$mod/$fileName"
            var tempAtlas = atlases[extraAtlas]  // fetch if cached
            if (tempAtlas == null) {
                debug("Loading %s = %s", extraAtlas, file.path())
                tempAtlas = TextureAtlas(file)  // load if not
                atlases[extraAtlas] = tempAtlas  // cache the freshly loaded
            }
            for (region in tempAtlas.regions) {
                if (region.name.startsWith("Skins")) {
                    val ninePatch = tempAtlas.createPatch(region.name)
                    ninePatchDrawables[region.name] = NinePatchDrawable(ninePatch)
                } else {
                    val drawable = TextureRegionDrawable(region)
                    textureRegionDrawables[region.name] = drawable
                }
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
     *      "TileSets/FantasyHex/Units/Warrior-NUMBER" are retrieved. NUMBER must start from 1 and
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

        var number = 1
        while (imageExists("$baseFileName-$number")) {
            layerNames.add("$baseFileName-$number")
            ++number
        }

        for (i in layerNames.indices) {
            val image = getImage(layerNames[i])
            if (i < colors.size && colors[i] != null)
                image.color = colors[i]
            layerList.add(image)
        }

        return layerList
    }

    fun getWhiteDot() = getImage(whiteDotLocation).apply { setSize(1f) }
    fun getDot(dotColor: Color) = getWhiteDot().apply { color = dotColor }

    fun getExternalImage(fileName: String): Image {
        // Since these are not packed in an atlas, they have no scaling filter metadata and
        // default to Nearest filter, anisotropic level 1. Use Linear instead, helps
        // loading screen and Tutorial.WorldScreen quite a bit. More anisotropy barely helps.
        val texture = Texture("ExtraImages/$fileName")
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        return ImageWithCustomSize(TextureRegion(texture))
    }

    fun getImage(fileName: String?): Image {
        return ImageWithCustomSize(getDrawable(fileName))
    }

    fun getDrawable(fileName: String?): TextureRegionDrawable {
        return textureRegionDrawables[fileName] ?: textureRegionDrawables[whiteDotLocation]!!
    }

    fun getNinePatch(fileName: String?, tintColor: Color? = null): NinePatchDrawable {
        val drawable = ninePatchDrawables[fileName] ?: NinePatchDrawable(NinePatch(textureRegionDrawables[whiteDotLocation]!!.region))

        if (fileName == null || ninePatchDrawables[fileName] == null) {
            drawable.minHeight = 0f
            drawable.minWidth = 0f
        }
        if (tintColor == null)
            return drawable
        return drawable.tint(tintColor)
    }

    fun imageExists(fileName: String) = textureRegionDrawables.containsKey(fileName)
    fun techIconExists(techName: String) = imageExists("TechIcons/$techName")
    fun unitIconExists(unitName: String) = imageExists("UnitIcons/$unitName")
    fun ninePatchImageExists(fileName: String) = ninePatchDrawables.containsKey(fileName)

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

    fun getRandomNationIndicator(size: Float): IconCircleGroup {
        return "?"
            .toLabel(Color.WHITE, (size * 5f/8f).toInt())
            .apply { this.setAlignment(Align.center) }
            .surroundWithCircle(size * 0.9f).apply { circle.color = Color.BLACK }
            .surroundWithCircle(size, false).apply { circle.color = Color.WHITE }
    }

    private fun nationIconExists(nation: String) = imageExists("NationIcons/$nation")
    fun getNationIcon(nation: String) = getImage("NationIcons/$nation")

    fun wonderImageExists(wonderName: String) = imageExists("WonderImages/$wonderName")
    fun getWonderImage(wonderName: String) = getImage("WonderImages/$wonderName")

    private fun getColorFromStats(stats: Stats): Color? {
        if (stats.asSequence().none { it.value > 0 }) return Color.WHITE
        val highestStat = stats.asSequence().maxByOrNull { it.value }!!
        return highestStat.key.color
    }


    fun getImprovementIcon(improvementName: String, size: Float = 20f, withCircle: Boolean = true): Group {
        if (improvementName == Constants.cancelImprovementOrder)
            return getImage("OtherIcons/Stop").surroundWithCircle(size)

        val icon = getImage("ImprovementIcons/$improvementName")

        if (!withCircle) return icon.toGroup(size)

        val group = icon.surroundWithCircle(size)
        val improvement = ruleset.tileImprovements[improvementName]
        if (improvement != null)
            group.circle.color = getColorFromStats(improvement)
        return group.surroundWithThinCircle()
    }

    fun getPortraitImage(construction: String, size: Float): Group {
        if (ruleset.buildings.containsKey(construction)) {
            val buildingPortraitLocation = "BuildingPortraits/$construction"
            return if (imageExists(buildingPortraitLocation)) {
                getImage(buildingPortraitLocation).toGroup(size)
            } else {
                val image = if (imageExists("BuildingIcons/$construction")) getImage("BuildingIcons/$construction")
                    else getImage("BuildingIcons/Fallback")
                image.surroundWithCircle(size).surroundWithThinCircle()
            }
        }
        if (ruleset.units.containsKey(construction)) {
            val unitPortraitLocation = "UnitPortraits/$construction"
            return if (imageExists(unitPortraitLocation)) {
                getImage(unitPortraitLocation).toGroup(size)
            } else
                getUnitIcon(construction).surroundWithCircle(size).surroundWithThinCircle()
        }
        if (PerpetualConstruction.perpetualConstructionsMap.containsKey(construction))
            return getImage("OtherIcons/Convert$construction").toGroup(size)
        return getStatIcon(construction).surroundWithCircle(size).surroundWithThinCircle()
    }

    fun getPromotionIcon(promotionName: String, size: Float = 30f): Actor {
        val nameWithoutBrackets = promotionName.replace("[", "").replace("]", "")

        var level = when {
            nameWithoutBrackets.endsWith(" I") -> 1
            nameWithoutBrackets.endsWith(" II") -> 2
            nameWithoutBrackets.endsWith(" III") -> 3
            else -> 0
        }

        val basePromotionName = nameWithoutBrackets.dropLast(if (level == 0) 0 else level + 1)

        val imageAttempter = ImageAttempter(Unit)
            .tryImage { "UnitPromotionIcons/$nameWithoutBrackets" }
            .tryImage { "UnitPromotionIcons/$basePromotionName" }
            .tryImage { "UnitIcons/${basePromotionName.removeSuffix(" ability")}" }

        if (imageAttempter.getPathOrNull() != null && imageAttempter.getPath()!!.endsWith(nameWithoutBrackets))
            level = 0

        val promotionColor = colorFromRGB(255, 226, 0)
        val circle = imageAttempter.getImage()
            .apply { color = promotionColor }
            .surroundWithCircle(size)
            .apply { circle.color = colorFromRGB(0, 12, 49) }

        if (level != 0) {
            val padding = if (level == 3) 0.5f else 2f
            val starTable = Table().apply { defaults().pad(padding) }
            for (i in 1..level) starTable.add(getImage("OtherIcons/Star")).size(size / 4f)
            starTable.centerX(circle)
            starTable.y = size / 6f
            circle.addActor(starTable)
        }
        return circle.surroundWithThinCircle(promotionColor)
    }

    fun religionIconExists(iconName: String) = imageExists("ReligionIcons/$iconName")
    fun getReligionImage(iconName: String): Image {
        return getImage("ReligionIcons/$iconName")
    }
    fun getCircledReligionIcon(iconName: String, size: Float): IconCircleGroup {
        return getReligionImage(iconName).surroundWithCircle(size, color = Color.BLACK )
    }

    @Deprecated("Use skin defined base color instead", ReplaceWith("BaseScreen.skinStrings.skinConfig.baseColor", "com.unciv.ui.utils.BaseScreen"))
    fun getBlue() = Color(0x004085bf)

    fun getCircle() = getImage("OtherIcons/Circle")
    fun getTriangle() = getImage("OtherIcons/Triangle")

    fun getRedCross(size: Float, alpha: Float): Actor {
        val redCross = getImage("OtherIcons/Close")
        redCross.setSize(size, size)
        redCross.color = Color.RED.cpy().apply { a = alpha }
        return redCross
    }

    fun getCrossedImage(image: Actor, iconSize: Float) = Group().apply {
            isTransform = false
            setSize(iconSize, iconSize)
            image.center(this)
            addActor(image)
            val cross = getRedCross(iconSize * 0.7f, 0.7f)
            cross.center(this)
            addActor(cross)
        }

    fun getArrowImage(align:Int = Align.right): Image {
        val image = getImage("OtherIcons/ArrowRight")
        image.setOrigin(Align.center)
        if (align == Align.left) image.rotation = 180f
        if (align == Align.bottom) image.rotation = -90f
        if (align == Align.top) image.rotation = 90f
        return image
    }

    fun getResourceImage(resourceName: String, size: Float): IconCircleGroup {
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
        return iconGroup.surroundWithThinCircle()
    }

    fun getTechIconGroup(techName: String, circleSize: Float): IconCircleGroup {
        val techIconColor = ruleset.eras[ruleset.technologies[techName]?.era()]?.getColor()?.darken(0.6f) ?: Color.BLACK
        val image =
                if (imageExists("TechIcons/$techName")) getImage("TechIcons/$techName")
                else getImage("TechIcons/Fallback")
        return image.apply { color = techIconColor }
            .surroundWithCircle(circleSize)
            .surroundWithThinCircle(techIconColor)
    }

    fun getProgressBarHorizontal(width: Float, height: Float, percentComplete: Float, progressColor: Color, backgroundColor: Color): Group {
        return ProgressBar(width, height, false)
            .setBackground(backgroundColor)
            .setProgress(progressColor, percentComplete)
    }

    fun getProgressBarVertical(width: Float, height: Float, percentComplete: Float, progressColor: Color, backgroundColor: Color): Group {
        return ProgressBar(width, height, true)
                .setBackground(backgroundColor)
                .setProgress(progressColor, percentComplete)
    }

    class ProgressBar(width: Float, height: Float, val vertical: Boolean = true):Group() {

        var primaryPercentage: Float = 0f
        var secondaryPercentage: Float = 0f

        var label: Label? = null
        var background: Image? = null
        var secondaryProgress: Image? = null
        var primaryProgress: Image? = null

        init {
            setSize(width, height)
            isTransform = false
        }

        fun setLabel(color: Color, text: String, fontSize: Int = Constants.defaultFontSize) : ProgressBar {
            label = text.toLabel()
            label?.setAlignment(Align.center)
            label?.setFontColor(color)
            label?.setFontSize(fontSize)
            label?.toFront()
            label?.center(this)
            if (label != null)
                addActor(label)
            return this
        }

        fun setBackground(color: Color): ProgressBar {
            background = getWhiteDot()
            background?.color = color
            background?.setSize(width, height) //clamp between 0 and 1
            background?.toBack()
            background?.center(this)
            if (background != null)
                addActor(background)
            return this
        }

        fun setSemiProgress(color: Color, percentage: Float): ProgressBar {
            secondaryPercentage = percentage
            secondaryProgress = getWhiteDot()
            secondaryProgress?.color = color
            if (vertical)
                secondaryProgress?.setSize(width, height *  max(min(percentage, 1f),0f))
            else
                secondaryProgress?.setSize(width *  max(min(percentage, 1f),0f), height)
            if (secondaryProgress != null)
                addActor(secondaryProgress)
            return this
        }

        fun setProgress(color: Color, percentage: Float): ProgressBar {
            primaryPercentage = percentage
            primaryProgress = getWhiteDot()
            primaryProgress?.color = color
            if (vertical)
                primaryProgress?.setSize(width, height *  max(min(percentage, 1f),0f))
            else
                primaryProgress?.setSize(width *  max(min(percentage, 1f),0f), height)
            if (primaryProgress != null)
                addActor(primaryProgress)
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
        healthBar.background = BaseScreen.skinStrings.getUiBackground("General/HealthBar", tintColor = Color.BLACK)
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

    fun getAvailableSkins() = ninePatchDrawables.keys.asSequence().map { it.split("/")[1] }.distinct()

    fun getAvailableTilesets() = textureRegionDrawables.keys.asSequence().filter { it.startsWith("TileSets") && !it.contains("/Units/") }
            .map { it.split("/")[1] }.distinct()

    fun getAvailableUnitsets() = textureRegionDrawables.keys.asSequence().filter { it.contains("/Units/") }
        .map { it.split("/")[1] }.distinct()
}
