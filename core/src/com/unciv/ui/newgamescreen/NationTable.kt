package com.unciv.ui.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.civilopedia.FormattedLine.IconDisplay
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.WrappableLabel
import com.unciv.ui.utils.extensions.pad

// The ruleset also acts as a secondary parameter to determine if this is the right or self side of the player picker
class NationTable(val nation: Nation, width: Float, minHeight: Float, ruleset: Ruleset? = null)
    : Table(BaseScreen.skin) {
    val innerTable = Table()

    init {
        val innerColor = nation.getInnerColor()
        val outerColor = nation.getOuterColor()
        val textBackgroundColor = Color(0x002042ff) // getBlue().lerp(Black,0.5).apply { a = 1 }
        val borderWidth = 5f
        val totalPadding = 10f + 4 * borderWidth // pad*2 + innerTable.pad*2 + borderTable.pad*2
        val internalWidth = width - totalPadding

        val titleTable = Table()
        titleTable.background = ImageGetter.getBackground(outerColor)
        val nationIndicator: Actor =
            if (nation.name == Constants.random) ImageGetter.getRandomNationIndicator(50f)
            else ImageGetter.getNationIndicator(nation, 50f)
        titleTable.add(nationIndicator).pad(10f).padLeft(0f)  // left 0 for centering _with_ label

        val titleText = if (ruleset == null || nation.name == Constants.random || nation.name == Constants.spectator)
            nation.name else nation.getLeaderDisplayName()
        val leaderDisplayNameMaxWidth = internalWidth - 70f // for the nation indicator with padding
        val leaderDisplayLabel = WrappableLabel(titleText, leaderDisplayNameMaxWidth, innerColor, Constants.headingFontSize)
        if (leaderDisplayLabel.prefWidth > leaderDisplayNameMaxWidth - 2f) {
            leaderDisplayLabel.wrap = true
            titleTable.add(leaderDisplayLabel).width(leaderDisplayNameMaxWidth)
        } else {
            titleTable.add(leaderDisplayLabel).align(Align.center).pad(10f,0f)
        }

        innerTable.add(titleTable).growX().fillY().row()

        if (ruleset != null) {
            titleTable.padBottom(borderWidth) // visual centering including upper border
            innerTable.background = ImageGetter.getBackground(textBackgroundColor)
            val lines = nation.getCivilopediaTextLines(ruleset)
                .filter { it.header != 3 }
            innerTable.add(MarkupRenderer.render(lines, internalWidth, iconDisplay = IconDisplay.NoLink)).pad(10f)
            val borderTable = Table()
            borderTable.background = ImageGetter.getBackground(outerColor)
            borderTable.add(innerTable).pad(borderWidth).grow()
            add(borderTable).pad(borderWidth).width(width).minHeight(minHeight - totalPadding)
        } else {
            innerTable.background = ImageGetter.getBackground(outerColor)
            add(innerTable).width(width).minHeight(minHeight - totalPadding)
        }

        touchable = Touchable.enabled
        background = ImageGetter.getBackground(innerColor)
    }
}

/*
Layout if ruleset != null:

       *Widgets*
         Text colour                     Background Colour
           Align                           Padding

+----- *NationTable* ----------------------------------------+
|                                        getInnerColor       |
| +---- *borderTable* -------------------------------------+ |
| |                                      getOuterColor     | |
| | +--- *innerTable* -----------------------------------+ | |
| | | +-- *titleTable* --------------------------------+ | | |
| | | |   getInnerColor                  getOuterColor | | | |
| | | |     *nationIndicator*   *leaderDisplayLabel*   | | | |
| | | |   center or left/wrap            0: all sides  | | | |
| | | +------------------------------------------------+ | | |
| | |     White                          Dark-blue       | | |
| | |   MarkupRenderer.render(getCivilopediaTextLines)   | | |
| | |     left/wrap                      10: all sides   | | |
| | |                                                    | | |
| | +----------------------------------------------------+ | |
| +--------------------------------------------------------+ |
+------------------------------------------------------------+

*/
