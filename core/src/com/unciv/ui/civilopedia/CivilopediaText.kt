package com.unciv.ui.civilopedia

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.stats.INamed
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.*
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
 *  - Automatic external links (no [text] but [link] begins with a URL protocol)
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
    /** Sets text color, accepts Java names or 6/3-digit web colors (e.g. #FFA040). */
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
    constructor(unique: Unique, indent: Int = 0) : this(unique.text, getUniqueLink(unique), indent = indent)

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
    val displayColor: Color by lazy { parseColor() }

    /** Returns true if this formatted line will not display anything */
    fun isEmpty(): Boolean = text.isEmpty() && extraImage.isEmpty() && 
            !starred && icon.isEmpty() && link.isEmpty() && !separator

    /** Self-check to potentially support the mod checker
     * @return `null` if no problems found, or multiline String naming problems.
     */
    @Suppress("unused")
    fun unsupportedReason(): String? {
        val reasons = sequence {
            if (text.isNotEmpty() && separator) yield("separator and text are incompatible")
            if (extraImage.isNotEmpty() && link.isNotEmpty()) yield("extraImage and other options except imageSize are incompatible")
            if (header != 0 && size != Int.MIN_VALUE) yield("use either size or header but not both")
            // ...
        }
        return reasons.joinToString { "\n" }.takeIf { it.isNotEmpty() }
    }

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
            for (parameter in unique.params + unique.conditionals.flatMap { it.params }) {
                val category = allObjectNamesCategoryMap!![parameter] ?: continue
                return category.name + "/" + parameter
            }
            return ""
        }
        private fun getCurrentRuleset() = when {
            !UncivGame.isCurrentInitialized() -> Ruleset()
            !UncivGame.Current.isGameInfoInitialized() -> RulesetCache[BaseRuleset.Civ_V_Vanilla.fullName]!!
            else -> UncivGame.Current.gameInfo.ruleSet
        }
        private fun initNamesCategoryMap(ruleSet: Ruleset): HashMap<String, CivilopediaCategories> {
            //val startTime = System.nanoTime()
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
                yield(CivilopediaCategories.Unit to ruleSet.units)
                yield(CivilopediaCategories.Technology to ruleSet.technologies)
                yield(CivilopediaCategories.Building to ruleSet.buildings.filter { !it.value.isAnyWonder() })
                yield(CivilopediaCategories.Wonder to ruleSet.buildings.filter { it.value.isAnyWonder() })
            }
            val result = HashMap<String,CivilopediaCategories>()
            allObjectMapsSequence.filter { !it.first.hide }
                .flatMap { pair -> pair.second.keys.asSequence().map { key -> pair.first to key } }
                .forEach { 
                    result[it.second] = it.first
                    //println("  ${it.second} is a ${it.first}")
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
    private fun parseColor(): Color {
        if (color.isEmpty()) return defaultColor
        if (color[0] == '#' && color.isHex(1,3)) {
            if (color.isHex(1,6)) return Color.valueOf(color)
            val hex6 = String(charArrayOf(color[1], color[1], color[2], color[2], color[3], color[3]))
            return Color.valueOf(hex6)
        }
        return defaultColor
    }

    /** Used only as parameter to [FormattedLine.render] and [MarkupRenderer.render] */
    enum class IconDisplay { All, NoLink, None }

    /**
     * Renders the formatted line as a scene2d [Actor] (currently always a [Table])
     * @param labelWidth Total width to render into, needed to support wrap on Labels.
     * @param iconDisplay Flag to omit link or all images. 
     */
    fun render(labelWidth: Float, iconDisplay: IconDisplay = IconDisplay.All): Actor {
        if (extraImage.isNotEmpty()) {
            val table = Table(BaseScreen.skin)
            try {
                val image = when {
                    ImageGetter.imageExists(extraImage) ->
                        ImageGetter.getImage(extraImage)
                    Gdx.files.internal("ExtraImages/$extraImage.png").exists() ->
                        ImageGetter.getExternalImage("$extraImage.png")
                    Gdx.files.internal("ExtraImages/$extraImage.jpg").exists() ->
                        ImageGetter.getExternalImage("$extraImage.jpg")
                    else -> return table
                }
                val width = if (imageSize.isNaN()) labelWidth else imageSize
                val height = width * image.height / image.width
                table.add(image).size(width, height)
            } catch (exception: Exception) {
                println ("${exception.message}: ${exception.cause?.message}")
            }
            return table
        }

        val fontSize = when {
            header in 1 until headerSizes.size -> headerSizes[header]
            size == Int.MIN_VALUE -> Constants.defaultFontSize
            else -> size
        }
        val labelColor = if(starred) defaultColor else displayColor

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
            val label = if (fontSize == Constants.defaultFontSize && labelColor == defaultColor) textToDisplay.toLabel()
            else textToDisplay.toLabel(labelColor,fontSize)
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
}

/** Makes [renderer][render] available outside [ICivilopediaText] */
object MarkupRenderer {
    /** Height of empty line (`FormattedLine()`) - about half a normal text line, independent of font size */
    private const val emptyLineHeight = 10f
    /** Default cell padding of non-empty lines */
    private const val defaultPadding = 2.5f
    /** Padding above a [separator][FormattedLine.separator] line */
    private const val separatorTopPadding = 10f
    /** Padding below a [separator][FormattedLine.separator] line */
    private const val separatorBottomPadding = 10f

    /**
     *  Build a Gdx [Table] showing [formatted][FormattedLine] [content][lines].
     *
     *  @param labelWidth       Available width needed for wrapping labels and [centered][FormattedLine.centered] attribute.
     *  @param padding          Default cell padding (default 2.5f) to control line spacing
     *  @param iconDisplay      Flag to omit link or all images (but not linking itself if linkAction is supplied)
     *  @param linkAction       Delegate to call for internal links. Leave null to suppress linking.
     */
    fun render(
        lines: Collection<FormattedLine>,
        labelWidth: Float = 0f,
        padding: Float = defaultPadding,
        iconDisplay: FormattedLine.IconDisplay = FormattedLine.IconDisplay.All,
        linkAction: ((id: String) -> Unit)? = null
    ): Table {
        val skin = BaseScreen.skin
        val table = Table(skin).apply { defaults().pad(padding).align(Align.left) }
        for (line in lines) {
            if (line.isEmpty()) {
                table.add().padTop(emptyLineHeight).row()
                continue
            }
            if (line.separator) {
                table.addSeparator(line.displayColor, 1, if (line.size == Int.MIN_VALUE) 2f else line.size.toFloat())
                    .pad(separatorTopPadding, 0f, separatorBottomPadding, 0f)
                continue
            }
            val actor = line.render(labelWidth, iconDisplay)
            if (line.linkType == FormattedLine.LinkType.Internal && linkAction != null)
                actor.onClick {
                    linkAction(line.link)
                }
            else if (line.linkType == FormattedLine.LinkType.External)
                actor.onClick {
                    Gdx.net.openURI(line.link)
                }
            if (labelWidth == 0f)
                table.add(actor).align(line.align).row()
            else
                table.add(actor).width(labelWidth).align(line.align).row()
        }
        return table.apply { pack() }
    }
}

/** Storage class for instantiation of the simplest form containing only the lines collection */
open class SimpleCivilopediaText(
    override var civilopediaText: List<FormattedLine>
) : ICivilopediaText {
    constructor(strings: Sequence<String>) : this(
        strings.map { FormattedLine(it) }.toList())
    constructor(first: Sequence<FormattedLine>, strings: Sequence<String>) : this(
        (first + strings.map { FormattedLine(it) }).toList())

    override fun makeLink() = ""
}

/** Addon common to most ruleset game objects managing civilopedia display
 *
 * ### Usage:
 * 1. Let [Ruleset] object implement this (by inheriting and implementing class [ICivilopediaText])
 * 2. Add `"civilopediaText": ["",…],` in the json for these objects
 * 3. Optionally override [getCivilopediaTextHeader] to supply a different header line
 * 4. Optionally override [getCivilopediaTextLines] to supply automatic stuff like tech prerequisites, uniques, etc.
 * 4. Optionally override [assembleCivilopediaText] to handle assembly of the final set of lines yourself.
 */
interface ICivilopediaText {
    /** List of strings supporting simple [formatting rules][FormattedLine] that [CivilopediaScreen] can render.
     *  May later be merged with automatic lines generated by the deriving class
     *  through overridden [getCivilopediaTextHeader] and/or [getCivilopediaTextLines] methods.
     */
    var civilopediaText: List<FormattedLine>

    /** Generate header line from object metadata.
     * Default implementation will take [INamed.name] and render it in 150% normal font size with an icon from [makeLink].
     * @return A [FormattedLine] that will be inserted on top
     */
    fun getCivilopediaTextHeader(): FormattedLine? =
        if (this is INamed) FormattedLine(name, icon = makeLink(), header = 2)
        else null

    /** Generate automatic lines from object metadata.
     *
     * Please do not rely on a UncivGame.Current.gameInfo being initialized, this should be able to run from the main menu.
     * (And the info displayed should be about the **ruleset**, not the player situation)
     * 
     * Default implementation is empty - no need to call super in overrides.
     *
     * @param ruleset The current ruleset for the Civilopedia viewer
     * @return A list of [FormattedLine]s that will be inserted before
     *         the first line of [civilopediaText] having a [link][FormattedLine.link]
     */
    fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> = listOf()

    /** Build a Gdx [Table] showing our [formatted][FormattedLine] [content][civilopediaText]. */
    fun renderCivilopediaText (labelWidth: Float, linkAction: ((id: String)->Unit)? = null): Table {
        return MarkupRenderer.render(civilopediaText, labelWidth, linkAction = linkAction)
    }

    /** Assemble json-supplied lines with automatically generated ones.
     *
     * The default implementation will insert [getCivilopediaTextLines] before the first [linked][FormattedLine.link] [civilopediaText] line and [getCivilopediaTextHeader] on top.
     *
     * @param ruleset The current ruleset for the Civilopedia viewer
     * @return A new CivilopediaText instance containing original [civilopediaText] lines merged with those from [getCivilopediaTextHeader] and [getCivilopediaTextLines] calls.
     */
    fun assembleCivilopediaText(ruleset: Ruleset): ICivilopediaText {
        val outerLines = civilopediaText.iterator()
        val newLines = sequence {
            var middleDone = false
            var outerNotEmpty = false
            val header = getCivilopediaTextHeader()
            if (header != null) {
                yield(header)
                yield(FormattedLine(separator = true))
            }
            while (outerLines.hasNext()) {
                val next = outerLines.next()
                if (!middleDone && !next.isEmpty() && next.linkType != FormattedLine.LinkType.None) {
                    middleDone = true
                    if (outerNotEmpty) yield(FormattedLine())
                    yieldAll(getCivilopediaTextLines(ruleset))
                    yield(FormattedLine())
                }
                outerNotEmpty = true
                yield(next)
            }
            if (!middleDone) {
                if (outerNotEmpty) yield(FormattedLine())
                yieldAll(getCivilopediaTextLines(ruleset))
            }
        }
        return SimpleCivilopediaText(newLines.toList())
    }

    /** Create the correct string for a Civilopedia link */
    fun makeLink(): String

    /** Overrides alphabetical sorting in Civilopedia
     *  @param ruleset The current ruleset in case the function needs to do lookups
     */
    fun getSortGroup(ruleset: Ruleset): Int = 0

    /** Overrides Icon used for Civilopedia entry list (where you select the instance)
     *  This will still be passed to the category-specific image getter.
     */
    fun getIconName() = if (this is INamed) name else ""
}
