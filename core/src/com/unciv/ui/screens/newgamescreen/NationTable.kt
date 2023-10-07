package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.nation.Nation
import com.unciv.ui.components.widgets.WrappableLabel
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine.IconDisplay
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer

// The ruleset also acts as a secondary parameter to determine if this is the right or self side of the player picker
class NationTable(val nation: Nation, width: Float, minHeight: Float, ruleset: Ruleset? = null)
    : Table(BaseScreen.skin) {
    val innerTable = Table()

    init {
        val innerColor = nation.getInnerColor()
        val outerColor = nation.getOuterColor()
        val textBackgroundColor = Color(0x002042ff)
        val borderWidth = 5f
        val totalPadding = 10f + 4 * borderWidth // pad*2 + innerTable.pad*2 + borderTable.pad*2
        val internalWidth = width - totalPadding

        val titleTable = Table()
        titleTable.background = BaseScreen.skinStrings.getUiBackground(
            "NewGameScreen/NationTable/Title", tintColor = outerColor
        )
        val nationIndicator = ImageGetter.getNationPortrait(nation, 50f)  // Works for Random too
        titleTable.add(nationIndicator).pad(10f).padLeft(0f)  // left 0 for centering _with_ label

        val titleText = if (ruleset == null || nation.name == Constants.random || nation.name == Constants.spectator)
            nation.name else nation.getLeaderDisplayName()
        val leaderDisplayNameMaxWidth = internalWidth - 70f // for the nation indicator with padding
        val leaderDisplayLabel = WrappableLabel(titleText, leaderDisplayNameMaxWidth, innerColor, Constants.headingFontSize, hideIcons = true)
        if (leaderDisplayLabel.prefWidth > leaderDisplayNameMaxWidth - 2f) {
            leaderDisplayLabel.wrap = true
            titleTable.add(leaderDisplayLabel).width(leaderDisplayNameMaxWidth)
        } else {
            titleTable.add(leaderDisplayLabel).align(Align.center).pad(10f,0f)
        }

        innerTable.add(titleTable).growX().fillY().row()

        if (ruleset != null) {
            titleTable.padBottom(borderWidth) // visual centering including upper border
            innerTable.background = BaseScreen.skinStrings.getUiBackground(
                "NewGameScreen/NationTable/RightInnerTable",
                tintColor = textBackgroundColor
            )
            val lines = nation.getCivilopediaTextLines(ruleset)
                .filter { it.header != 3 }
            innerTable.add(MarkupRenderer.render(lines, internalWidth, iconDisplay = IconDisplay.NoLink)).pad(10f)
            val borderTable = Table()
            borderTable.background = BaseScreen.skinStrings.getUiBackground(
                "NewGameScreen/NationTable/BorderTable",
                tintColor = outerColor
            )
            borderTable.add(innerTable).pad(borderWidth).grow()
            add(borderTable).pad(borderWidth).width(width).minHeight(minHeight - totalPadding)
        } else {
            add(innerTable).width(width).minHeight(minHeight - totalPadding)
        }

        touchable = Touchable.enabled
        background = BaseScreen.skinStrings.getUiBackground(
            "NewGameScreen/NationTable/Background",
            tintColor = innerColor
        )
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
