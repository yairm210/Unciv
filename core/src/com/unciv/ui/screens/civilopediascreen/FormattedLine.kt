package com.unciv.ui.screens.civilopediascreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Colors
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.ui.components.extensions.getReadonlyPixmap
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Log
import kotlin.math.max

/* Ideas:
 *    - Now we're using a Table container and inside one Table per line. Rendering order, in view of
 *      texture swaps, is per Group, as this goes by ZIndex and that is implemented as actual index
 *      into the parent's children array. So, we're SOL to get the number of texture switches down
 *      with this structure, as many lines will require at least 2 texture switches.
 *      We *could* instead try go for one big table with 4 columns (3 images, plus rest)
 *      and use colspan - then group all images separate from labels via ZIndex. To-Do later.
 *    - Do bold using Distance field fonts wrapped in something like [maltaisn/msdf-gdx](https://github.com/maltaisn/msdf-gdx)
 *    - Do strikethrough by stacking a line on top (as rectangle with background like the separator but thinner)
 */


/** Represents a decorated text line with optional linking capability.
 *  A line can have [text] with optional [size], [color], [indent] or as [header];
 *  and up to three icons: [link], [object][icon], [star][starred] in that order.
 *  Special cases:
 *  - Standalone [image][extraImage] from atlas or from ExtraImages
 *  - A separator line ([separator])
 *  - Automatic external links ([link] begins with a URL protocol)
 */
class FormattedLine (
    /** Text to display. */
    val text: String = "",
    /** Create link: Line gets a 'Link' icon and is linked to either
     *  an Unciv object (format `category/entryname`) or an external URL. */
    val link: String = "",
    /** Display an Unciv object's icon inline but do not link (format `category/entryname`). */
    val icon: String = "",
    /** Display an Image instead of text, [sized][imageSize]. Can be a path as understood by
     *  [ImageGetter.getImage] or the name of a png or jpg in ExtraImages. */
    val extraImage: String = "",
    /** Width of the [extraImage], height is calculated preserving aspect ratio. Defaults to available width. */
    val imageSize: Float = Float.NaN,
    /** Text size, defaults to [Constants.defaultFontSize]. Use [size] or [header] but not both. */
    val size: Int = Int.MIN_VALUE,
    /** Header level. 1 means double text size and decreases from there. */
    val header: Int = 0,
    /** Indentation: 0 = text will follow icons with a little padding,
     *  1 = aligned to a little more than 3 icons, each step above that adds 30f. */
    val indent: Int = 0,
    /** Defines vertical padding between rows, defaults to 5f. */
    val padding: Float = Float.NaN,
    /** Sets text color, accepts 6/3-digit web colors (e.g. #FFA040) or names as defined by Gdx [Colors]. */
    val color: String = "",
    /** Renders a separator line instead of text. Can be combined only with [color] and [size] (line width, default 2) */
    val separator: Boolean = false,
    /** Decorates text with a star icon - if set, it receives the [color] instead of the text. */
    val starred: Boolean = false,
    /** Centers the line (and turns off wrap) */
    val centered: Boolean = false,
    /** Paint a red X over the [icon] or [link] image */
    val iconCrossed: Boolean = false
) {
    // Note: This gets directly deserialized by Json - please keep all attributes meant to be read
    // from json in the primary constructor parameters above. Everything else should be a fun(),
    // have no backing field, be `by lazy` or use @Transient, Thank you.

    /** Looks for linkable ruleset objects in [Unique] parameters and returns a linked [FormattedLine] if successful, a plain one otherwise */
    constructor(unique: Unique, indent: Int = 0) : this(unique.getDisplayText(), getUniqueLink(unique), indent = indent)

    /** Link types that can be used for [FormattedLine.link] */
    enum class LinkType {
        None,
        /** Link points to a Civilopedia entry in the form `category/item` **/
        Internal,
        /** Link opens as URL in external App - begins with `https://`, `http://` or `mailto:` **/
        External
    }

    /** The type of the [link]'s destination */
    val linkType: LinkType by lazy {
        when {
            link.hasProtocol() -> LinkType.External
            link.isNotEmpty() -> LinkType.Internal
            else -> LinkType.None
        }
    }

    /** Translates [centered] into [libGdx][Gdx] [Align] value */
    val align: Int by lazy {if (centered) Align.center else Align.left}

    private val iconToDisplay: String by lazy {
        if (icon.isNotEmpty()) icon else if (linkType == LinkType.Internal) link else ""
    }
    private val textToDisplay: String by lazy {
        if (text.isEmpty() && linkType == LinkType.External) link else text
    }

    /** Retrieves the parsed [Color] corresponding to the [color] property (String)*/
    val displayColor: Color by lazy { parseColor() ?: defaultColor }

    /** Returns true if this formatted line will not display anything */
    fun isEmpty(): Boolean = text.isEmpty() && extraImage.isEmpty() &&
            !starred && icon.isEmpty() && link.isEmpty() && !separator

    /** Self-check to support the RulesetValidator
     * @return 0 or more Strings naming problems - all occurrences get the same severity upstream
     */
    fun unsupportedReasons(validator: RulesetValidator) = sequence {
        if (hasNormalContent() && separator)
            yield("separator and other options are incompatible")
        if (link.isNotEmpty() && !(isValidInternalLink(link) || link.hasProtocol()))
            yield("link is invalid - use internal category/name format or a https:// URL")
        if (icon.isNotEmpty() && !isValidInternalLink(link))
            yield("icon is invalid - use internal category/name format")
        if (header != 0 && size != Int.MIN_VALUE)
            yield("use either size or header but not both")
        if (header !in headerSizes.indices)
            yield("header should be in the range 1..${headerSizes.size - 1}") // Not mentioning 0 is valid too - same as omitting it
        if (size != Int.MIN_VALUE && size !in 1..100)  // arbitrary
            yield("size is out of sensible range")
        if (indent !in 0..100)  // arbitrary
            yield("indent is out of sensible range")
        if (color.isNotEmpty())
            if (parseColor() == null)
                yield("unknown color \"$color\"")
            else if (text.isEmpty() && textToDisplay.isEmpty() && !starred && !separator)
                yield("color set but nothing to apply it to")
        if (iconCrossed && link.isEmpty() && icon.isEmpty())
            yield("iconCrossed set without icon or link")
        if (extraImage.isNotEmpty()) checkExtraImage(validator)
        if (!imageSize.isNaN() && extraImage.isEmpty())
            yield("imageSize is only valid for an extraImage")
    }

    private suspend fun SequenceScope<String>.checkExtraImage(validator: RulesetValidator) {
        if (hasNormalContent() || separator)
            // not checking centered or padding - these may be implementable
            yield("extraImage and other options except imageSize are incompatible")
        // check image exists - but for textures from atlases we can't rely on ImageGetter having cached the appropriate combo???
        if (ImageGetter.imageExists(extraImage)) return
        if (ImageGetter.findExternalImage(extraImage) != null) return
        if (validator.uncachedImageExists(extraImage)) return
        yield("extraImage not found as either atlas texture or in ExtraImages folder")
    }

    /** Not one of the exceptions - empty, separator or extraImage. For validation, independent of [isEmpty] which looks for **visible** content */
    private fun hasNormalContent() =
        text.isNotEmpty() || link.isNotEmpty() || icon.isNotEmpty() || color.isNotEmpty() || size != Int.MIN_VALUE || header != 0 || starred
    private fun isValidInternalLink(link: String) = link.matches(Regex("""^[^/]+/[^/]+$"""))

    /** Constants used by [FormattedLine] */
    companion object {
        /** Array of text sizes to translate the [header] attribute */
        val headerSizes = arrayOf(Constants.defaultFontSize,36,32,27,24,21,15,12,9)    // pretty arbitrary, yes
        /** Default color for [text] _and_ icons */
        val defaultColor: Color = Color.WHITE
        /** Internal path to the [Link][link] image */
        const val linkImage = "OtherIcons/Link"
        /** Internal path to the [Star][starred] image */
        const val starImage = "OtherIcons/Star"
        /** Default inline icon size */
        const val minIconSize = 30f
        /** Padding added to the right of each icon */
        const val iconPad = 5f
        /** Padding distance per [indent] level */
        const val indentPad = 30f
        /** Where indent==1 will be, measured as icon count */
        const val indentOneAtNumIcons = 2

        private var rulesetCachedInNameMap: Ruleset? = null
        // Cache to quickly match Categories to names. Takes a few ms to build on a slower desktop and will use just a few 10k bytes.
        private var allObjectNamesCategoryMap: HashMap<String, CivilopediaCategories>? = null

        // Helper for constructor(Unique)
        private fun getUniqueLink(unique: Unique): String {
            val ruleSet = getCurrentRuleset()
            if (allObjectNamesCategoryMap == null || rulesetCachedInNameMap !== ruleSet)
                allObjectNamesCategoryMap = initNamesCategoryMap(ruleSet)
            for (parameter in unique.params + unique.modifiers.flatMap { it.params }) {
                val category = allObjectNamesCategoryMap!![parameter] ?: continue
                return category.name + "/" + parameter
            }
            return ""
        }
        private fun getCurrentRuleset() = when {
            !UncivGame.isCurrentInitialized() -> Ruleset()
            UncivGame.Current.gameInfo == null -> RulesetCache[BaseRuleset.Civ_V_Vanilla.fullName]!!
            else -> UncivGame.Current.gameInfo!!.ruleset
        }
        private fun initNamesCategoryMap(ruleSet: Ruleset): HashMap<String, CivilopediaCategories> {
            //val startTime = System.nanoTime()
            // These are because the IDEA compiler DOES NOT like them being directly in the yield
            //  This is some kinf o compiler bug, looks like
            fun wonderBuildings() = ruleSet.buildings.filter { it.value.isAnyWonder() }
            fun nonWonderBuildings() = ruleSet.buildings.filter { !it.value.isAnyWonder() }
            
            // order these with the categories that should take precedence in case of name conflicts (e.g. Railroad) _last_
            val allObjectMapsSequence = sequence {
                yield(CivilopediaCategories.Belief to ruleSet.beliefs)
                yield(CivilopediaCategories.Difficulty to ruleSet.difficulties)
                yield(CivilopediaCategories.Promotion to ruleSet.unitPromotions)
                yield(CivilopediaCategories.Policy to ruleSet.policies)
                yield(CivilopediaCategories.Terrain to ruleSet.terrains)
                yield(CivilopediaCategories.Improvement to ruleSet.tileImprovements)
                yield(CivilopediaCategories.Resource to ruleSet.tileResources)
                yield(CivilopediaCategories.Nation to ruleSet.nations)
                yield(CivilopediaCategories.UnitType to ruleSet.unitTypes)
                yield(CivilopediaCategories.Unit to ruleSet.units)
                yield(CivilopediaCategories.Technology to ruleSet.technologies)
                yield(CivilopediaCategories.Building to nonWonderBuildings())
                yield(CivilopediaCategories.Wonder to wonderBuildings())
            }
            val result = HashMap<String, CivilopediaCategories>()
            allObjectMapsSequence
                .flatMap { pair -> pair.second.keys.asSequence().map { key -> pair.first to key } }
                .forEach {
                    result[it.second] = it.first
                }
            result["Maya Long Count calendar cycle"] = CivilopediaCategories.Tutorial

            //println("allObjectNamesCategoryMap took ${System.nanoTime()-startTime}ns to initialize")
            rulesetCachedInNameMap = ruleSet
            return result
        }
    }

    /** Extension: determines if a [String] looks like a link understood by the OS */
    private fun String.hasProtocol() = startsWith("http://") || startsWith("https://") || startsWith("mailto:")

    /** Extension: determines if a section of a [String] is composed entirely of hex digits
     * @param start starting index
     * @param length length of section (if == 0 [isHex] returns `true`, if receiver too short [isHex] returns `false`)
     */
    private fun String.isHex(start: Int, length: Int) =
        when {
            length == 0 -> false
            start + length > this.length -> false
            substring(start, start + length).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> true
            else -> false
        }

    /** Parse a json-supplied color string to [Color], defaults to [defaultColor]. */
    private fun parseColor(): Color? {
        if (color.isEmpty()) return null
        if (color[0] == '#' && color.isHex(1,3)) {
            if (color.isHex(1,6)) return Color.valueOf(color)
            val hex6 = String(charArrayOf(color[1], color[1], color[2], color[2], color[3], color[3]))
            return Color.valueOf(hex6)
        }
        return Colors.get(color.uppercase())
    }

    /** Used only as parameter to [FormattedLine.render] and [MarkupRenderer.render] */
    enum class IconDisplay { All, NoLink, None }

    /**
     * Renders the formatted line as a scene2d [Actor] (currently always a [Table])
     * @param labelWidth Total width to render into, needed to support wrap on Labels.
     * @param iconDisplay Flag to omit link or all images.
     */
    fun render(labelWidth: Float, iconDisplay: IconDisplay = IconDisplay.All): Actor {
        if (extraImage.isNotEmpty()) return renderExtraImage(labelWidth)

        val fontSize = when {
            header in 1 until headerSizes.size -> headerSizes[header]
            size == Int.MIN_VALUE -> Constants.defaultFontSize
            else -> size
        }
        val labelColor = if (starred) defaultColor else displayColor

        val table = Table(BaseScreen.skin)
        var iconCount = 0
        val iconSize = max(minIconSize, fontSize * 1.5f)
        if (linkType != LinkType.None && iconDisplay == IconDisplay.All) {
            table.add(ImageGetter.getImage(linkImage)).size(iconSize).padRight(iconPad)
            iconCount++
        }
        if (iconDisplay != IconDisplay.None)
            iconCount += renderIcon(table, iconToDisplay, iconSize)
        if (starred) {
            val image = ImageGetter.getImage(starImage)
            image.color = displayColor
            table.add(image).size(iconSize).padRight(iconPad)
            iconCount++
        }
        if (textToDisplay.isNotEmpty()) {
            val usedWidth = iconCount * (iconSize + iconPad)
            val indentWidth = when {
                centered -> -usedWidth
                indent == 0 && iconCount == 0 -> 0f
                indent == 0 -> iconPad
                iconCount == 0 -> indent * indentPad - usedWidth
                else -> (indent-1) * indentPad +
                        indentOneAtNumIcons * (minIconSize + iconPad) + iconPad - usedWidth
            }
            val label = if ('«' in textToDisplay)
                ColorMarkupLabel(textToDisplay, fontSize, hideIcons = iconCount != 0)
            else
                textToDisplay.toLabel(labelColor, fontSize, hideIcons = iconCount != 0)
            label.wrap = !centered && labelWidth > 0f
            label.setAlignment(align)
            if (labelWidth == 0f)
                table.add(label)
                    .padLeft(indentWidth.coerceAtLeast(0f))
                    .padRight((-indentWidth).coerceAtLeast(0f))
                    .align(align)
            else
                table.add(label)
                    .width(labelWidth - usedWidth - indentWidth)
                    .padLeft(indentWidth)
                    .align(align)
        }
        return table
    }

    private fun renderExtraImage(labelWidth: Float): Table {
        val table = Table(BaseScreen.skin)
        fun getExtraImage(): Image? {
            if (ImageGetter.imageExists(extraImage))
                return if (centered) ImageGetter.getDrawable(extraImage).cropToContent()
                    else ImageGetter.getImage(extraImage)
            val externalImage = ImageGetter.findExternalImage(extraImage)
                ?: return null
            return ImageGetter.getExternalImage(externalImage)
        }
        try {
            val image = getExtraImage() ?: return table
            // limit larger cordinate to a given max size
            val maxSize = if (imageSize.isNaN()) labelWidth else imageSize
            val (width, height) = if (image.width > image.height)
                maxSize to maxSize * image.height / image.width
            else maxSize * image.width / image.height to maxSize
            table.add(image).size(width, height)
        } catch (exception: Exception) {
            Log.error("Exception while rendering civilopedia text", exception)
        }
        return table
    }

    /** Place a RuleSet object icon.
     * @return 1 if successful for easy counting
     */
    private fun renderIcon(table: Table, iconToDisplay: String, iconSize: Float): Int {
        // prerequisites: iconToDisplay has form "category/name", category can be mapped to
        // a `CivilopediaCategories`, and that knows how to get an Image.
        if (iconToDisplay.isEmpty()) return 0
        val parts = iconToDisplay.split('/', limit = 2)
        if (parts.size != 2) return 0
        val category = CivilopediaCategories.fromLink(parts[0]) ?: return 0
        val image = category.getImage?.invoke(parts[1], iconSize) ?: return 0

        if (iconCrossed) {
            table.add(ImageGetter.getCrossedImage(image, iconSize)).size(iconSize).padRight(iconPad)
        } else {
            table.add(image).size(iconSize).padRight(iconPad)
        }
        return 1
    }

    // Debug visualization only
    override fun toString(): String {
        return when {
            isEmpty() -> "(empty)"
            separator -> "(separator)"
            extraImage.isNotEmpty() -> "(extraImage='$extraImage')"
            header > 0 -> "(header=$header)'$text'"
            linkType == LinkType.None -> "'$text'"
            else -> "'$text'->$link"
        }
    }

    // region Helpers to crop an image to content
    private fun TextureRegionDrawable.cropToContent(): Image {
        val rect = getContentSize()
        val newRegion = TextureRegion(region.texture, rect.x, rect.y, rect.width, rect.height)
        return Image(TextureRegionDrawable(newRegion))
    }

    private fun TextureRegionDrawable.getContentSize(): IntRectangle {
        val pixMap = region.texture.textureData.getReadonlyPixmap()
        val result = IntRectangle(region.regionX, region.regionY, region.regionWidth, region.regionHeight) // Not Gdx: integers!
        val original = result.copy()

        while (result.height > 0 && pixMap.isRowEmpty(result, result.height - 1)) {
            result.height -= 1
        }
        while (result.height > 0 && pixMap.isRowEmpty(result, 0)) {
            result.y += 1
            result.height -= 1
        }
        while (result.width > 0 && pixMap.isColumnEmpty(result, result.width - 1)) {
            result.width -= 1
        }
        while (result.width > 0 && pixMap.isColumnEmpty(result, 0)) {
            result.x += 1
            result.width -= 1
        }

        result.grow((original.width / 40).coerceAtLeast(1), (original.height / 40).coerceAtLeast(1))
        return result.intersection(original)
    }

    private fun Pixmap.isRowEmpty(bounds: IntRectangle, relativeY: Int): Boolean {
        val y = bounds.y + relativeY
        return (bounds.x until bounds.x + bounds.width).all {
            getPixel(it, y) and 255 == 0
        }
    }

    private fun Pixmap.isColumnEmpty(bounds: IntRectangle, relativeX: Int): Boolean {
        val x = bounds.x + relativeX
        return (bounds.y until bounds.y + bounds.height).all {
            getPixel(x, it) and 255 == 0
        }
    }
    // endregion

    // region Integer Rectangle class
    /** Partial rewrite of java.awt.Rectangle which is not available on Android. */
    private data class IntRectangle(
        var x: Int,
        var y: Int,
        var width: Int,
        var height: Int
    ) {
        // Note: Gdx *has* an Integer equivalent of Vector2: GridPoint2 - but not of Rectangle (all in com.badlogic.gdx.math)

        /** Grow both left and right edges horizontally by [h] and correspondingly top, bottom by [v]
         *
         *  Unlike java.awt.Rectangle this will not check for integer overflow or negative size.
         */
        fun grow(h: Int, v: Int) {
            x -= h
            width += h + h
            y -= v
            height += y + y
        }

        /** Returns a new IntRectangle that represents the intersection of the two rectangles: `this` and [r].
         *  If the two rectangles do not intersect, the result will be an empty rectangle.
         *
         *  Unlike java.awt.Rectangle this will not check for integer overflow or negative size.
         */
        fun intersection(r: IntRectangle): IntRectangle {
            val tx1 = x.coerceAtLeast(r.x)
            val ty1 = y.coerceAtLeast(r.y)
            val tx2 = (x + width).coerceAtMost(r.x + r.width)
            val ty2 = (y + height).coerceAtMost(r.y + r.height)
            return IntRectangle(tx1, ty1, tx2 - tx1, ty2 - ty1)
        }
    }
    // endregion
}
