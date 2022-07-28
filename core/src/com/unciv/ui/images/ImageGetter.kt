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
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
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
    private val textureRegionDrawables = HashMap<String, TextureRegionDrawable>()

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

    fun getWhiteDot() = getImage(whiteDotLocation)
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
    fun unitIconExists(unitName: String) = imageExists("UnitIcons/$unitName")

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


    fun getImprovementIcon(improvementName: String, size: Float = 20f): Group {
        if (improvementName.startsWith(Constants.remove) || improvementName == Constants.cancelImprovementOrder)
            return getImage("OtherIcons/Stop").surroundWithCircle(size)

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

        val circle = imageAttempter.getImage()
            .apply { color = colorFromRGB(255, 226, 0) }
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
        return circle
    }

    fun religionIconExists(iconName: String) = imageExists("ReligionIcons/$iconName")
    fun getReligionImage(iconName: String): Image {
        return getImage("ReligionIcons/$iconName")
    }
    fun getCircledReligionIcon(iconName: String, size: Float): IconCircleGroup {
        return getReligionImage(iconName).surroundWithCircle(size, color = Color.BLACK )
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
        return iconGroup
    }

    fun getTechIconGroup(techName: String, circleSize: Float) = getTechIcon(techName).surroundWithCircle(circleSize)

    fun getTechIcon(techName: String): Image {
        val techIconColor = ruleset.eras[ruleset.technologies[techName]?.era()]?.getColor()
            ?: return getWhiteDot()
        return getImage("TechIcons/$techName").apply { color = techIconColor.darken(0.6f) }
    }

    fun getProgressBarVertical(width: Float, height: Float, percentComplete: Float, progressColor: Color, backgroundColor: Color): Group {
        return VerticalProgressBar(width, height)
                .addColor(backgroundColor, 1f)
                .addColor(progressColor, percentComplete)
    }

    class VerticalProgressBar(width: Float, height: Float):Group() {
        init {
            setSize(width, height)
            isTransform = false
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
