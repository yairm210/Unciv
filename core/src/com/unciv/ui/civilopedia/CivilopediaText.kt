package com.unciv.ui.civilopedia

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.INamed
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
 *  A line can have up to three icons: link, object, star in that order.
 *  Special cases:
 *  - Standalone image from atlas or from ExtraImages
 *  - A separator line ([separator])
 *  - Automatic external links (no [text] but [link] begins with a URL protocol)
 * 
 * @param text          Text to display.
 * @param link          Create link: Line gets a 'Link' icon and is linked to either 
 *                      an Unciv object (format `category/entryname`) or an external URL.
 * @param icon          Display an Unciv object's icon inline but do not link (format `category/entryname`).
 * @param extraImage    Display an Image instead of text. Can be a path as understood by
 *                      [ImageGetter.getImage] or the name of a png or jpg in ExtraImages.
 * @param imageSize     Width of the [extraImage], height is calculated preserving aspect ratio. Defaults to available width.
 * @param size          Text size, defaults to 18f. Use [size] or [header] but not both.
 * @param header        Header level. 1 means double text size and decreases from there.
 * @param indent        Indentation - 0 = text will follow icons with a little padding,
 *                      1 = aligned to a little more than 3 icons, each step above that adds 30f.
 * @param padding       Defines vertical padding between rows, defaults to 5f.
 * @param color         Sets text color, accepts Java names or 6/3-digit web colors (e.g. #FFA040).
 * @param separator     Renders a separator line instead of text. Can be combined only with color and size (the latter being line width, defaulting to 2)
 * @param italic        Renders text in italic font style (not implemented)
 * @param bold          Renders text in bold font style (not implemented)
 * @param strike        Renders text in strikethrough font style (not implemented)
 * @param shadow        Renders text with a dark shadow (not implemented)
 * @param starred       Decorates text with a star icon - if set, it receives the [color] instead of the text.
 * @param centered      Centers the line (and turns off wrap)
 */
class FormattedLine (
    val text: String = "",
    val link: String = "",
    val icon: String = "",
    val extraImage: String = "",
    val imageSize: Float = Float.NaN,
    val size: Int = Int.MIN_VALUE,
    val header: Int = 0,
    val indent: Int = 0,
    val padding: Float = Float.NaN,
    val color: String = "",
    val separator: Boolean = false,
    val italic: Boolean = false,        // Not implemented - would need separate font
    val bold: Boolean = false,          // Not implemented - see 'MSDF'?
    val strike: Boolean = false,        // Not implemented - could do a simple line
    val shadow: Boolean = false,        // Not implemented - see 'MSDF'.
    val starred: Boolean = false,
    val centered: Boolean = false
) {
    // Note: This gets directly deserialized by Json - please keep all attributes meant to be read
    // from json in the primary constructor parameters above. Everything else should be a fun(),
    // have no backing field, be `by lazy` or use @Transient, Thank you.

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
    val align
        get() = if (centered) Align.center else Align.left

    private val iconToDisplay
        get() = if(icon.isNotEmpty()) icon else if(linkType==LinkType.Internal) link else ""
    private val textToDisplay: String by lazy {
        if (text.isEmpty() && linkType == LinkType.External) link else text
    }

    /** Retrieves the parsed [Color] corresponding to the [color] property (String)*/
    val displayColor: Color by lazy { parseColor() }

    /** Returns true if this formatted line will not display anything */
    fun isEmpty(): Boolean = text.isEmpty() && extraImage.isEmpty() && 
            !starred && icon.isEmpty() && link.isEmpty() 

    /** Self-check to potentially support the mod checker
     * @return `null` if no problems found, or multiline String naming problems.
     */
    @Suppress("unused")
    fun unsupportedReason(): String? {
        val reasons = sequence {
            if (text.isNotEmpty() && separator) yield("separator and text are incompatible")
            if (italic) yield("italic is not yet implemented")
            if (bold) yield("bold is not yet implemented")
            if (strike) yield("strike is not yet implemented")
            if (shadow) yield("shadow is not yet implemented")
            if (extraImage.isNotEmpty() && link.isNotEmpty()) yield("extraImage and other options except imageSize are incompatible")
            if (header != 0 && size != Int.MIN_VALUE) yield("use either size or header but not both")
            // ...
        }
        return reasons.joinToString { "\n" }.takeIf { it.isNotEmpty() }
    }

    /** Constants used by [FormattedLine]
     * @property defaultSize    Mirrors default text size as defined elsewhere
     * @property defaultColor   Default color for text _and_ icons
     * @property headerSizes    Array of text sizes to translate the [header] attribute
     * @property minIconSize    Default inline icon size
     * @property indentPad      Padding distance per [indent] level
     * @property starImage      Internal path to the Star image
     * @property linkImage      Internal path to the Link image
     */
    companion object {
        const val defaultSize = 18
        val headerSizes = arrayOf(defaultSize,36,32,27,24,21,15,12,9)    // pretty arbitrary, yes
        val defaultColor: Color = Color.WHITE
        const val linkImage = "OtherIcons/Link"
        const val starImage = "OtherIcons/Star"
        const val minIconSize = 30f
        const val iconPad = 5f
        const val indentPad = 30f
    }

    /** Extension: determines if a [String] looks like a link understood by the OS */
    private fun String.hasProtocol() = startsWith("http://") || startsWith("https://") || startsWith("mailto:")

    /** Extension: determines if a section of a [String] is composed entirely of hex digits
     * @param start starting index
     * @param length length of section - if =0 [isHex]=true but if receiver too short [isHex]=false
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
    
    /**
     * Renders the formatted line as a scene2d [Actor] (currently always a [Table])
     * @param labelWidth Total width to render into, needed to support wrap on Labels.
     * @param noLinkImages Omit 'Link' images - no visual indicator that a line may be linked. 
     */
    fun render(labelWidth: Float, noLinkImages: Boolean = false): Actor {
        if (extraImage.isNotEmpty()) {
            val table = Table(CameraStageBaseScreen.skin)
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
            header in headerSizes.indices -> headerSizes[header]
            size == Int.MIN_VALUE -> defaultSize
            else -> size
        }
        val labelColor = if(starred) defaultColor else displayColor

        val table = Table(CameraStageBaseScreen.skin)
        var iconCount = 0
        val iconSize = max(minIconSize, fontSize * 1.5f)
        if (linkType != LinkType.None && !noLinkImages) {
            table.add( ImageGetter.getImage(linkImage) ).size(iconSize).padRight(iconPad)
            iconCount++
        }
        if (iconToDisplay.isNotEmpty() && !noLinkImages) {
            val parts = iconToDisplay.split('/', limit = 2)
            if (parts.size == 2) {
                val category = CivilopediaCategories.fromLink(parts[0])
                if (category != null) {
                    if (category.getImage != null) {
                        // That Enum custom property is a nullable reference to a lambda which
                        // in turn is allowed to return null. Sorry, but without `!!` the code
                        // won't compile and with we would get the incorrect warning.
                        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") 
                        val image = category.getImage!!(parts[1], iconSize)
                        if (image != null) {
                            table.add(image).size(iconSize).padRight(iconPad)
                            iconCount++
                        }
                    }
                }
            }
        }
        if (starred) {
            val image = ImageGetter.getImage(starImage)
            image.color = displayColor
            table.add(image).size(iconSize).padRight(iconPad)
            iconCount++
        }
        if (textToDisplay.isNotEmpty()) {
            val usedWidth = iconCount * (iconSize + iconPad)
            val padIndent = when {
                centered -> -usedWidth
                indent == 0 && iconCount == 0 -> 0f
                indent == 0 -> iconPad
                else -> (indent-1) * indentPad + 3 * minIconSize + 4 * iconPad - usedWidth
            }
            val label = if (fontSize == defaultSize && labelColor == defaultColor) textToDisplay.toLabel()
            else textToDisplay.toLabel(labelColor,fontSize)
            label.wrap = !centered && labelWidth > 0f
            label.setAlignment(align)
            if (labelWidth == 0f)
                table.add(label)
                    .padLeft(padIndent).align(align)
            else
                table.add(label).width(labelWidth - usedWidth - padIndent)
                    .padLeft(padIndent).align(align)
        }
        return table
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
    private const val emptyLineHeight = 10f
    private const val defaultPadding = 2.5f
    private const val separatorTopPadding = 5f
    private const val separatorBottomPadding = 15f

    /**
     *  Build a Gdx [Table] showing [formatted][FormattedLine] [content][lines].
     *
     *  @param lines            The formatted content to render.
     *  @param labelWidth       Available width needed for wrapping labels and [centered][FormattedLine.centered] attribute.
     *  @param padding          Default cell padding (default 2.5f) to control line spacing
     *  @param noLinkImages     Flag to omit link images (but not linking itself)
     *  @param linkAction       Delegate to call for internal links. Leave null to suppress linking.
     */
    fun render(
        lines: Collection<FormattedLine>,
        labelWidth: Float = 0f,
        padding: Float = defaultPadding,
        noLinkImages: Boolean = false,
        linkAction: ((id: String) -> Unit)? = null
    ): Table {
        val skin = CameraStageBaseScreen.skin
        val table = Table(skin).apply { defaults().pad(padding).align(Align.left) }
        for (line in lines) {
            if (line.isEmpty()) {
                table.add().padTop(emptyLineHeight).row()
                continue
            }
            if (line.separator) {
                //todo: Once the new addSeparator is in, pass line.color and line.size to it
                //table.addSeparator(line.displayColor, 1, if (line.size == Int.MIN_VALUE) 2f else line.size.toFloat())
                //    .pad(separatorTopPadding, 0f, separatorBottomPadding, 0f)
                table.addSeparator().pad(separatorTopPadding, 0f, separatorBottomPadding, 0f)
                continue
            }
            val actor = line.render(labelWidth, noLinkImages)
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

/** Storage class for interface [ICivilopediaText] for use as base class */
open class CivilopediaText : ICivilopediaText {
    override var civilopediaText = listOf<FormattedLine>()
}
/** Storage class for instantiation of the simplest form containing only the lines collection */
class SimpleCivilopediaText(lines: List<FormattedLine>, val isComplete: Boolean = false) : CivilopediaText() {
    init {
        civilopediaText = lines
    }
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = isComplete
    constructor(strings: Sequence<String>, isComplete: Boolean = false) : this(
        strings.map { FormattedLine(it) }.toList(), isComplete)
    constructor(first: Sequence<FormattedLine>, strings: Sequence<String>, isComplete: Boolean = false) : this(
        (first + strings.map { FormattedLine(it) }).toList(), isComplete)
}

/** Addon common to most ruleset game objects managing civilopedia display
 *
 * ### Usage:
 * 1. Let [Ruleset] object implement this (e.g. by inheriting class [CivilopediaText] or adding var [civilopediaText] itself)
 * 2. Add `"civilopediaText": ["",…],` in the json for these objects
 * 3. Optionally override [getCivilopediaTextHeader] to supply a header line
 * 4. Optionally override [getCivilopediaTextLines] to supply automatic stuff like tech prerequisites, uniques, etc.
 * 4. Optionally override [assembleCivilopediaText] to handle assembly of the final set of lines yourself.
 */
interface ICivilopediaText {
    /** List of strings supporting simple [formatting rules][FormattedLine] that [CivilopediaScreen] can render.
     * May later be merged with automatic lines generated by the deriving class
     *  through overridden [getCivilopediaTextHeader] and/or [getCivilopediaTextLines] methods.
     *
     */
    var civilopediaText: List<FormattedLine>

    /** Generate header line from object metadata.
     * Default implementation will pull [INamed.name] and render it in 150% normal font size.
     * @return A [FormattedLine] that will be inserted on top
     */
    fun getCivilopediaTextHeader(): FormattedLine? =
        if (this is INamed) FormattedLine(name, header = 2)
        else null

    /** Generate automatic lines from object metadata.
     *
     * Default implementation is empty - no need to call super in overrides.
     *
     * @param ruleset The current ruleset for the Civilopedia viewer
     * @return A list of [FormattedLine]s that will be inserted before
     *         the first line of [civilopediaText] having a [link][FormattedLine.link]
     */
    fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> = listOf()

    /** Override this and return true to tell the Civilopedia that the legacy description is no longer needed */
    fun replacesCivilopediaDescription() = false
    /** Override this and return true to tell the Civilopedia that this is not empty even if nothing came from json */
    fun hasCivilopediaTextLines() = false
    /** Indicates that neither json nor getCivilopediaTextLines have content */
    fun isCivilopediaTextEmpty() = civilopediaText.isEmpty() && !hasCivilopediaTextLines()

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
    fun assembleCivilopediaText(ruleset: Ruleset): CivilopediaText {
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
                    if (hasCivilopediaTextLines()) {
                        if (outerNotEmpty) yield(FormattedLine())
                        yieldAll(getCivilopediaTextLines(ruleset))
                        yield(FormattedLine())
                    }
                }
                outerNotEmpty = true
                yield(next)
            }
            if (!middleDone) {
                if (outerNotEmpty && hasCivilopediaTextLines()) yield(FormattedLine())
                yieldAll(getCivilopediaTextLines(ruleset))
            }
        }
        return SimpleCivilopediaText(newLines.toList(), isComplete = true)
    }
}
