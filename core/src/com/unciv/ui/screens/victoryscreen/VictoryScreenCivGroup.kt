package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.setSize
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
    currentPlayer: Civilization,
    defeatedPlayerStyle: DefeatedPlayerStyle
) : Table() {
    // Note this Table has no skin - works as long as no element tries to get its skin from the parent

    internal enum class DefeatedPlayerStyle {
        REGULAR,
        GREYED_OUT,
    }

    constructor(
        civEntry: VictoryScreen.CivWithStat,
        currentPlayer: Civilization,
        defeatedPlayerStyle: DefeatedPlayerStyle = DefeatedPlayerStyle.GREYED_OUT
    )
            : this(
        civEntry.civ,
        ": ",
        // Don't show a `0` for defeated civs.
        if (civEntry.civ.isDefeated()) "" else civEntry.value.tr(),
        currentPlayer,
        defeatedPlayerStyle
    )

    constructor(
        civ: Civilization,
        additionalInfo: String,
        currentPlayer: Civilization,
        defeatedPlayerStyle: DefeatedPlayerStyle = DefeatedPlayerStyle.GREYED_OUT
    )
    // That tr() is only needed to support additionalInfo containing {} because tr() doesn't support nested ones.
            : this(civ, "\n", additionalInfo.tr(), currentPlayer, defeatedPlayerStyle)

    init {
        val labelText =
            if (currentPlayer.knows(civ) || currentPlayer == civ ||
                    civ.isDefeated() || currentPlayer.isDefeated()) {
                if (additionalInfo.isEmpty()) civ.getDisplayCivName()
                    else "{${civ.getDisplayCivName()}}$separator{$additionalInfo}"
            } else Constants.unknownNationName

        val civInfo = getCivImageAndColors(civ, currentPlayer, defeatedPlayerStyle)
        add(civInfo.first).size(30f)
        val backgroundColor = civInfo.second
        val labelColor = civInfo.third

        background = BaseScreen.skinStrings.getUiBackground("VictoryScreen/CivGroup", BaseScreen.skinStrings.roundedEdgeRectangleShape, backgroundColor)
        val label = labelText.toLabel(labelColor, hideIcons = true)
        label.setAlignment(Align.center)

        add(label).padLeft(10f)
    }

    companion object {
        fun getCivImageAndColors(civ: Civilization, currentPlayer: Civilization, defeatedPlayerStyle: DefeatedPlayerStyle): Triple<Actor, Color, Color> {
            when {
                civ.isDefeated() && defeatedPlayerStyle == DefeatedPlayerStyle.GREYED_OUT -> {
                    val icon = (ImageGetter.getImage("OtherIcons/DisbandUnit"))
                    icon.setSize(30f)
                    return Triple(icon, Color.LIGHT_GRAY, ImageGetter.CHARCOAL)
                }
                currentPlayer.isSpectator()
                    || civ.isDefeated() && defeatedPlayerStyle == DefeatedPlayerStyle.REGULAR
                    || currentPlayer == civ // || game.viewEntireMapForDebug
                    || currentPlayer.knows(civ)
                    || currentPlayer.isDefeated()
                    || currentPlayer.victoryManager.hasWon() -> {
                    return Triple(ImageGetter.getNationPortrait(civ.nation, 30f), civ.nation.getOuterColor(), civ.nation.getInnerColor())
                }
                else ->
                    return Triple((ImageGetter.getRandomNationPortrait(30f)), Color.LIGHT_GRAY, ImageGetter.CHARCOAL)
            }
        }

    }
}
