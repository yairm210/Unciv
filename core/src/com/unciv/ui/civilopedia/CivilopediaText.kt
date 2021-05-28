package com.unciv.ui.civilopedia

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.INamed
import com.unciv.ui.utils.*


/** Represents a text line with optional linking capability.
 *  Special cases:
 *  - Automatic external links (no [text] but [link] begins with a URL protocol)
 * 
 * @param text          Text to display.
 * @param link          Create link: Line gets a 'Link' icon and is linked to either 
 *                      an Unciv object (format `category/entryname`) or an external URL.
 * @param extraImage    Display an Image instead of text. Can be a path as understood by
 *                      [ImageGetter.getImage] or the name of a png or jpg in ExtraImages.
 * @param imageSize     Width of the [extraImage], height is calculated preserving aspect ratio. Defaults to available width.
 * @param header        Header level. 1 means double text size and decreases from there.
 * @param separator     Renders a separator line instead of text.
 */
class FormattedLine (
    val text: String = "",
    val link: String = "",
    val extraImage: String = "",
    val imageSize: Float = Float.NaN,
    val header: Int = 0,
    val separator: Boolean = false,
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

    private val textToDisplay: String by lazy {
        if (text.isEmpty() && linkType == LinkType.External) link else text
    }
    
    /** Returns true if this formatted line will not display anything */
    fun isEmpty(): Boolean = text.isEmpty() && extraImage.isEmpty() && link.isEmpty() && !separator

    /** Constants used by [FormattedLine]
     * @property defaultSize    Mirrors default text size as defined elsewhere
     * @property headerSizes    Array of text sizes to translate the [header] attribute
     */
    companion object {
        const val defaultSize = 18
        val headerSizes = arrayOf(defaultSize,36,32,27,24,21,15,12,9)    // pretty arbitrary, yes
        val defaultColor: Color = Color.WHITE
    }

    /** Extension: determines if a [String] looks like a link understood by the OS */
    private fun String.hasProtocol() = startsWith("http://") || startsWith("https://") || startsWith("mailto:")

    /**
     * Renders the formatted line as a scene2d [Actor] (currently always a [Table])
     * @param labelWidth Total width to render into, needed to support wrap on Labels.
     */
    fun render(labelWidth: Float): Actor {
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
            else -> defaultSize
        }
        val table = Table(CameraStageBaseScreen.skin)
        if (textToDisplay.isNotEmpty()) {
            val label = if (fontSize == defaultSize) textToDisplay.toLabel()
            else textToDisplay.toLabel(defaultColor,fontSize)
            label.wrap = labelWidth > 0f
            if (labelWidth == 0f)
                table.add(label)
            else
                table.add(label).width(labelWidth)
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
     *  @param linkAction       Delegate to call for internal links. Leave null to suppress linking.
     */
    fun render(
        lines: Collection<FormattedLine>,
        labelWidth: Float = 0f,
        padding: Float = defaultPadding,
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
                table.addSeparator().pad(separatorTopPadding, 0f, separatorBottomPadding, 0f)
                continue
            }
            val actor = line.render(labelWidth)
            if (line.linkType == FormattedLine.LinkType.Internal && linkAction != null)
                actor.onClick {
                    linkAction(line.link)
                }
            else if (line.linkType == FormattedLine.LinkType.External)
                actor.onClick {
                    Gdx.net.openURI(line.link)
                }
            if (labelWidth == 0f)
                table.add(actor).row()
            else
                table.add(actor).width(labelWidth).row()
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
