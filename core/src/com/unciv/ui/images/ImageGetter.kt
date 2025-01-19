package com.unciv.ui.images

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.Batch
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
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.skins.SkinCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.ui.components.NonTransformGroup
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.centerX
import com.unciv.ui.components.extensions.centerY
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.setFontSize
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.surroundWithThinCircle
import com.unciv.ui.components.extensions.toGroup
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.FontRulesetIcons
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.debug
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ImageGetter {
    const val whiteDotLocation = "OtherIcons/whiteDot"
    const val circleLocation = "OtherIcons/Circle"
    val CHARCOAL = Color(0x111111FF)

    // We use texture atlases to minimize texture swapping - see https://yairm210.medium.com/the-libgdx-performance-guide-1d068a84e181
    lateinit var atlas: TextureAtlas
    private val atlases = HashMap<String, TextureAtlas>()
    var ruleset = Ruleset()

    // We then shove all the drawables into a hashmap, because the atlas specifically tells us
    //   that the search on it is inefficient
    private val textureRegionDrawables = HashMap<String, TextureRegionDrawable>()
    private val ninePatchDrawables = HashMap<String, NinePatchDrawable>()

    fun getSpecificAtlas(name: String): TextureAtlas? = atlases[name]

    fun resetAtlases() {
        atlases.values.forEach { it.dispose() }
        atlases.clear()
    }

    fun reloadImages() = setNewRuleset(ruleset)

    /** Required every time the ruleset changes, in order to load mod-specific images */
    fun setNewRuleset(ruleset: Ruleset) {
        ImageGetter.ruleset = ruleset
        textureRegionDrawables.clear()

        // Load base
        loadModAtlases("", Gdx.files.internal(""))

        // These are from the mods
        val visualMods = UncivGame.Current.settings.visualMods + ruleset.mods
        for (mod in visualMods) {
            loadModAtlases(mod, UncivGame.Current.files.getModFolder(mod))
        }

        TileSetCache.assembleTileSetConfigs(ruleset.mods)
        SkinCache.assembleSkinConfigs(ruleset.mods)

        BaseScreen.setSkin()
        FontRulesetIcons.addRulesetImages(ruleset)
    }

    /** Loads all atlas/texture files from a folder, as controlled by an Atlases.json */
    fun loadModAtlases(mod: String, folder: FileHandle) {
        // See #4993 - you can't .list() on a jar file, so the ImagePacker leaves us the list of actual atlases.
        val controlFile = folder.child("Atlases.json")
        val fileNames = (if (controlFile.exists()) json().fromJson(Array<String>::class.java, controlFile)
            else emptyArray()).toMutableSet()
        if (mod.isNotEmpty()) fileNames += "game"  // Backwards compatibility - when packed by 4.9.15+ this is already in the control file
        for (fileName in fileNames) {
            val file = folder.child("$fileName.atlas")
            if (!file.exists()) continue
            val extraAtlas = if (mod.isEmpty()) fileName else if (fileName == "game") mod else "$mod/$fileName"
            var tempAtlas = atlases[extraAtlas]  // fetch if cached
            if (tempAtlas == null) {
                try {
                    debug("Loading %s = %s", extraAtlas, file.path())
                    tempAtlas = TextureAtlas(file)  // load if not
                    atlases[extraAtlas] = tempAtlas  // cache the freshly loaded
                } catch (ex: Exception) {
                    debug("Could not load file $file")
                    continue
                }
            }
            for (region in tempAtlas.regions) {
                if (region.name.startsWith("Skins")) {
                    // TODO: give user a mod warning that the image names has to be [name].9.png
                    //      if this throws an exception
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
    fun getWhiteDotDrawable() = textureRegionDrawables[whiteDotLocation]!!
    fun getDot(dotColor: Color) = getWhiteDot().apply { color = dotColor }

    /** Finds an image file under /ExtraImages/, including Mods (which can override builtin).
     *  Extension can be included or is guessed as png/jpg.
     *  @return `null` if no match found.
     */
    fun findExternalImage(name: String): FileHandle? {
        val folders = try { // For CI mod checker, we can't access "local" files
            // since Gdx files are not set up
            ruleset.mods.asSequence().map { UncivGame.Current.files.getLocalFile("mods/$it/ExtraImages") } +
                    sequenceOf(Gdx.files.internal("ExtraImages"))
        } catch (e: Exception) {
            debug("Error loading mods: $e")
            sequenceOf()
        }
        val extensions = sequenceOf("", ".png", ".jpg")
        return folders.flatMap { folder ->
            extensions.map { folder.child(name + it) }
        }.firstOrNull { it.exists() }
    }

    /** Loads an image on the fly - uncached Texture, not too fast. */
    fun getExternalImage(file: FileHandle): Image {
        // Since these are not packed in an atlas, they have no scaling filter metadata and
        // default to Nearest filter, anisotropic level 1. Use Linear instead, helps
        // loading screen and Tutorial.WorldScreen quite a bit. More anisotropy barely helps.
        val texture = Texture(file)
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        return ImageWithCustomSize(TextureRegion(texture))
    }
    /** Loads an image from (assets)/ExtraImages, from the jar if Unciv runs packaged.
     *  Cannot load ExtraImages from a Mod - use [findExternalImage] and the [getExternalImage](FileHandle) overload instead.
     */
    fun getExternalImage(fileName: String) =
        getExternalImage(Gdx.files.internal("ExtraImages/$fileName"))

    fun getImage(fileName: String?, tintColor: Color? = null): Image = 
        ImageWithCustomSize(getDrawable(fileName)).apply { color = tintColor ?: Color.WHITE }

    fun getDrawable(fileName: String?): TextureRegionDrawable =
        textureRegionDrawables[fileName] ?: textureRegionDrawables[whiteDotLocation]!!

    fun getDrawableOrNull(fileName: String?): TextureRegionDrawable? {
        if (fileName == null)
            return null
        return textureRegionDrawables[fileName]
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
    fun ninePatchImageExists(fileName: String) = ninePatchDrawables.containsKey(fileName)

    fun getStatIcon(statName: String): Image = getImage("StatIcons/$statName")
            .apply { setSize(20f, 20f) }

    fun wonderImageExists(wonderName: String) = imageExists("WonderImages/$wonderName")
    fun getWonderImage(wonderName: String) = getImage("WonderImages/$wonderName")

    fun getNationIcon(nation: String) = getImage("NationIcons/$nation")
    fun getNationPortrait(nation: Nation, size: Float): Portrait = PortraitNation(nation.name, size)

    fun getRandomNationPortrait(size: Float): Portrait = PortraitNation(Constants.random, size)

    fun getUnitIcon(unit: BaseUnit, color: Color = CHARCOAL): Image =
        if (imageExists("UnitIcons/${unit.name}"))
            getImage("UnitIcons/${unit.name}").apply { this.color = color }
        else getImage("UnitTypeIcons/${unit.type}").apply { this.color = color }

    fun getConstructionPortrait(construction: String, size: Float): Group {
        if (ruleset.buildings.containsKey(construction)) {
            return PortraitBuilding(construction, size)
        }
        if (ruleset.units.containsKey(construction)) {
            return PortraitUnit(construction, size)
        }
        if (PerpetualConstruction.perpetualConstructionsMap.containsKey(construction))
            return getImage("OtherIcons/Convert$construction").toGroup(size)
        return getStatIcon(construction).surroundWithCircle(size).surroundWithThinCircle()
    }

    fun getUniquePortrait(uniqueName: String, size: Float): Group = PortraitUnique(uniqueName, size)

    fun getPromotionPortrait(promotionName: String, size: Float = 30f): Group = PortraitPromotion(promotionName, size)

    fun getResourcePortrait(resourceName: String, size: Float, amount: Int = 0): Group =
        PortraitResource(resourceName, size, amount)

    fun getTechIconPortrait(techName: String, circleSize: Float): Group = PortraitTech(techName, circleSize)

    fun getImprovementPortrait(improvementName: String, size: Float = 20f, dim: Boolean = false, isPillaged: Boolean = false): Portrait =
        PortraitImprovement(improvementName, size, dim, isPillaged)

    fun getUnitActionPortrait(actionName: String, size: Float = 20f): Portrait = PortraitUnitAction(actionName, size)

    fun getReligionIcon(iconName: String): Image { return getImage("ReligionIcons/$iconName") }
    fun getReligionPortrait(iconName: String, size: Float): Portrait {
        if (religionIconExists(iconName))
            return PortraitReligion(iconName, size)
        val typeName = ruleset.beliefs[iconName]?.type?.name
        if (typeName != null && religionIconExists(typeName))
            return PortraitReligion(typeName, size)
        return PortraitReligion(iconName, size)
    }

    fun religionIconExists(iconName: String) = imageExists("ReligionIcons/$iconName")

    fun getCircleDrawable() = getDrawable(circleLocation)
    fun getCircle() = getImage(circleLocation)
    fun getCircle(color: Color = Color.WHITE, size: Float? = null) = getCircle().apply {
        setColor(color)
        if (size != null) setSize(size, size)
    }

    fun getTriangle() = getImage("OtherIcons/Triangle")

    fun getRedCross(size: Float, alpha: Float): Actor {
        val redCross = getImage("OtherIcons/Close")
        redCross.setSize(size, size)
        redCross.color = Color.RED.cpy().apply { a = alpha }
        return redCross
    }

    fun getCrossedImage(image: Actor, iconSize: Float) = NonTransformGroup().apply {
            setSize(iconSize, iconSize)
            image.center(this)
            addActor(image)
            val cross = getRedCross(iconSize * 0.7f, 0.7f)
            cross.center(this)
            addActor(cross)
        }

    fun getArrowImage(align: Int = Align.right): Image {
        val image = getImage("OtherIcons/ArrowRight")
        image.setOrigin(Align.center)
        if (align == Align.left) image.rotation = 180f
        if (align == Align.bottom) image.rotation = -90f
        if (align == Align.top) image.rotation = 90f
        return image
    }

    fun getProgressBarVertical(
        width: Float,
        height: Float,
        percentComplete: Float,
        progressColor: Color,
        backgroundColor: Color,
        progressPadding: Float = 0f): ProgressBar {
        return ProgressBar(width, height, true)
                .setBackground(backgroundColor)
                .setProgress(progressColor, percentComplete, padding = progressPadding)
    }

    class ProgressBar(width: Float, height: Float, val vertical: Boolean = true) : NonTransformGroup() {

        var primaryPercentage: Float = 0f
        var secondaryPercentage: Float = 0f

        var label: Label? = null
        var background: Image? = null
        var secondaryProgress: Image? = null
        var primaryProgress: Image? = null

        init {
            setSize(width, height)
        }

        override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)

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
            background?.color = color.cpy()
            background?.setSize(width, height) //clamp between 0 and 1
            background?.toBack()
            background?.center(this)
            if (background != null)
                addActor(background)
            return this
        }

        fun setSemiProgress(color: Color, percentage: Float, padding: Float = 0f): ProgressBar {
            secondaryPercentage = percentage
            secondaryProgress = getWhiteDot()
            secondaryProgress?.color = color.cpy()
            if (vertical)
                secondaryProgress?.setSize(width-padding*2, height *  max(min(percentage, 1f),0f))
            else
                secondaryProgress?.setSize(width *  max(min(percentage, 1f),0f), height-padding*2)
            if (secondaryProgress != null) {
                addActor(secondaryProgress)
                if (vertical)
                    secondaryProgress?.centerX(this)
                else
                    secondaryProgress?.centerY(this)
            }
            primaryProgress?.toFront()
            return this
        }

        fun setProgress(color: Color, percentage: Float, padding: Float = 0f): ProgressBar {
            primaryPercentage = percentage
            primaryProgress = getWhiteDot()
            primaryProgress?.color = color.cpy()
            if (vertical)
                primaryProgress?.setSize(width-padding*2, height *  max(min(percentage, 1f),0f))
            else
                primaryProgress?.setSize(width *  max(min(percentage, 1f),0f), height-padding*2)
            if (primaryProgress != null) {
                addActor(primaryProgress)
                if (vertical)
                    primaryProgress?.centerX(this)
                else
                    primaryProgress?.centerY(this)
            }
            return this
        }
    }

    fun getHealthBar(currentHealth: Float, maxHealth: Float, healthBarSize: Float, height: Float=5f): Table {
        val healthPercent = currentHealth / maxHealth
        val healthBar = Table()

        val healthPartOfBar = getWhiteDot()
        healthPartOfBar.color = when {
            healthPercent > 2 / 3f -> Color.GREEN
            healthPercent > 1 / 3f -> Color.ORANGE
            else -> Color.RED
        }
        healthBar.add(healthPartOfBar).size(healthBarSize * healthPercent, height)

        val emptyPartOfBar = getDot(CHARCOAL)
        healthBar.add(emptyPartOfBar).size(healthBarSize * (1 - healthPercent), height)

        healthBar.pad(1f)
        healthBar.pack()
        healthBar.background = BaseScreen.skinStrings.getUiBackground("General/HealthBar", tintColor = CHARCOAL)
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
    
    fun getAllImageNames() = textureRegionDrawables.keys

    fun getAvailableSkins() = ninePatchDrawables.keys.asSequence().map { it.split("/")[1] }.distinct()

    /** Determines available TileSets from the currently loaded Texture paths.
     *
     *  Note [TileSetCache] will not necessarily load all of them, e.g. if a Mod fails
     *  to provide a config json for a graphic with a Tileset path.
     *
     *  Intersect with [TileSetCache.getAvailableTilesets] for a more reliable answer
     */
    fun getAvailableTilesets() = textureRegionDrawables.keys.asSequence()
        .filter { it.startsWith("TileSets") && !it.contains("/Units/") }
        .map { it.split("/")[1] }.distinct()

    fun getAvailableUnitsets() = textureRegionDrawables.keys.asSequence()
        .filter { it.startsWith("TileSets") && it.contains("/Units/") }
        .map { it.split("/")[1] }
        .distinct()
}
