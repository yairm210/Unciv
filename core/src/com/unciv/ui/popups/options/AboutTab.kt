package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer

fun aboutTab(): Table {
    val versionAnchor = UncivGame.VERSION.text.replace(".", "")
    val lines = sequence {
        yield(FormattedLine(extraImage = "banner", imageSize = 240f, centered = true))
        yield(FormattedLine())
        yield(FormattedLine("{Version}: ${UncivGame.VERSION.toNiceString()}", link = "https://github.com/yairm210/Unciv/blob/master/changelog.md#$versionAnchor"))
        yield(FormattedLine("See online Readme", link = "https://github.com/yairm210/Unciv/blob/master/README.md#unciv---foss-civ-v-for-androiddesktop"))
        yield(FormattedLine("Visit repository", link = "https://github.com/yairm210/Unciv"))
    }
    return MarkupRenderer.render(lines.asIterable()).pad(20f)
}
