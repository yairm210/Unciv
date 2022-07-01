package com.unciv.ui.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.toNiceString

fun aboutTab(screen: BaseScreen): Table {
    val versionAnchor = UncivGame.VERSION.text.replace(".", "")
    val lines = sequence {
        yield(FormattedLine(extraImage = "banner", imageSize = 240f, centered = true))
        yield(FormattedLine())
        yield(FormattedLine("{Version}: ${UncivGame.VERSION.toNiceString()}", link = "https://github.com/yairm210/Unciv/blob/master/changelog.md#$versionAnchor"))
        yield(FormattedLine("See online Readme", link = "https://github.com/yairm210/Unciv/blob/master/README.md#unciv---foss-civ-v-for-androiddesktop"))
        yield(FormattedLine("Visit repository", link = "https://github.com/yairm210/Unciv"))
    }
    return MarkupRenderer.render(lines.toList()).pad(20f)
}
