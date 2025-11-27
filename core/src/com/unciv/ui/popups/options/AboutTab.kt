package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer

fun aboutTab(): Table {
    // The changelog has no patches, and anchors per release tag omit the dots
    val versionAnchor = Regex("""\.|-patch\d+$""").replace(UncivGame.VERSION.text, "")
    val lines = sequence {
        yield(FormattedLine(extraImage = "banner", imageSize = 240f, centered = true))
        yield(FormattedLine())
        yield(FormattedLine("{Version}: ${UncivGame.VERSION.toNiceString()}", link = "${Constants.uncivRepoURL}blob/master/changelog.md#$versionAnchor"))
        yield(FormattedLine("See online Readme", link = "${Constants.uncivRepoURL}blob/master/README.md#unciv---foss-civ-v-for-androiddesktop"))
        yield(FormattedLine("Visit repository", link = Constants.uncivRepoURL))
        yield(FormattedLine("Visit the wiki", link = Constants.wikiURL))
    }
    return MarkupRenderer.render(lines.asIterable()).pad(20f)
}
