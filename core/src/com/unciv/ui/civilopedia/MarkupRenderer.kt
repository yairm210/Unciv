package com.unciv.ui.civilopedia

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.onClick


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
