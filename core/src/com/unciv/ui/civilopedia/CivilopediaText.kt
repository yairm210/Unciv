package com.unciv.ui.civilopedia

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.utils.*


/** Represents a text line with optional linking capability.
 *  Special cases:
 *  - Automatic external links (no [text] but [link] begins with a URL protocol)
 * 
 * @param text          Text to display.
 * @param link          Create link: Line gets a 'Link' icon and is linked to either 
 *                      an Unciv object (format `category/entryname`) or an external URL.
 */
class FormattedLine (
    val text: String = "",
    val link: String = "",
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
    fun isEmpty(): Boolean = text.isEmpty() && link.isEmpty() 

    /** Extension: determines if a [String] looks like a link understood by the OS */
    private fun String.hasProtocol() = startsWith("http://") || startsWith("https://") || startsWith("mailto:")

    /**
     * Renders the formatted line as a scene2d [Actor] (currently always a [Table])
     * @param skin  The [Skin] to use for contained actors.
     * @param labelWidth Total width to render into, needed to support wrap on Labels.
     */
    fun render(skin: Skin, labelWidth: Float): Actor {
        val table = Table(skin)
        if (textToDisplay.isNotEmpty()) {
            val label = textToDisplay.toLabel()
            label.wrap = labelWidth > 0f
            if (labelWidth == 0f)
                table.add(label)
            else
                table.add(label).width(labelWidth)
        }
        return table
    }
}

/** Makes [renderer][render] available outside [ICivilopediaText] */
object MarkupRenderer {
    private const val emptyLineHeight = 10f
    private const val defaultPadding = 2.5f

    /**
     *  Build a Gdx [Table] showing [formatted][FormattedLine] [content][lines].
     *
     *  @param lines
     *  @param skin
     *  @param labelWidth       Available width needed for wrapping labels and [centered][FormattedLine.centered] attribute.
     *  @param linkAction       Delegate to call for internal links. Leave null to suppress linking.
     */
    fun render(
        lines: Collection<FormattedLine>,
        skin: Skin? = null,
        labelWidth: Float = 0f,
        linkAction: ((id: String) -> Unit)? = null
    ): Table {
        val skinToUse = skin ?: CameraStageBaseScreen.skin
        val table = Table(skinToUse).apply { defaults().pad(defaultPadding).align(Align.left) }
        for (line in lines) {
            if (line.isEmpty()) {
                table.add().padTop(emptyLineHeight).row()
                continue
            }
            val actor = line.render(skinToUse, labelWidth)
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
