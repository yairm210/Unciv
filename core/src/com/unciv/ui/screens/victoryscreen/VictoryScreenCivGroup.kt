package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/** Element displaying one Civilization as seen by another Civilization on a rounded-edge background.
 *  @param civ Civilization to show, with nation icon (depending on alive / known) and name, optionally followed by …
 *  @param separator … a separator (untranslated, only if additionalInfo isn't empty) and …
 *  @param additionalInfo … some additional info, auto-translated.
 *  @param currentPlayer Viewing Civilization
 */
internal class VictoryScreenCivGroup(
    civ: Civilization,
    separator: String,
    additionalInfo: String,
    currentPlayer: Civilization
) : Table() {
    // Note this Table has no skin - works as long as no element tries to get its skin from the parent

    constructor(civEntry: VictoryScreen.CivWithStat, currentPlayer: Civilization)
            : this(civEntry.civ, ": ", civEntry.value.toString(), currentPlayer)
    constructor(civ: Civilization, additionalInfo: String, currentPlayer: Civilization)
            // That tr() is only needed to support additionalInfo containing {} because tr() doesn't support nested ones.
            : this(civ, "\n", additionalInfo.tr(), currentPlayer)

    init {
        var labelText = if (additionalInfo.isEmpty()) civ.civName
            else "{${civ.civName}}$separator{$additionalInfo}"
        val labelColor: Color
        val backgroundColor: Color

        when {
            civ.isDefeated() -> {
                add(ImageGetter.getImage("OtherIcons/DisbandUnit")).size(30f)
                backgroundColor = Color.LIGHT_GRAY
                labelColor = Color.BLACK
            }
            currentPlayer == civ // || game.viewEntireMapForDebug
                    || currentPlayer.knows(civ)
                    || currentPlayer.isDefeated()
                    || currentPlayer.victoryManager.hasWon() -> {
                add(ImageGetter.getNationPortrait(civ.nation, 30f))
                backgroundColor = civ.nation.getOuterColor()
                labelColor = civ.nation.getInnerColor()
            }
            else -> {
                add(ImageGetter.getRandomNationPortrait(30f))
                backgroundColor = Color.DARK_GRAY
                labelColor = Color.WHITE
                labelText = Constants.unknownNationName
            }
        }

        background = BaseScreen.skinStrings.getUiBackground("VictoryScreen/CivGroup", BaseScreen.skinStrings.roundedEdgeRectangleShape, backgroundColor)
        val label = labelText.toLabel(labelColor)
        label.setAlignment(Align.center)

        add(label).padLeft(10f)
    }
}
