package com.unciv.ui.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

class CivGroup(civ: Civilization, afterCivNameText: String, currentPlayer: Civilization) : Table() {

    init {
        var labelText = "{${civ.civName.tr()}}{${afterCivNameText.tr()}}"
        var labelColor = Color.WHITE
        val backgroundColor: Color

        if (civ.isDefeated()) {
            add(ImageGetter.getImage("OtherIcons/DisbandUnit")).size(30f)
            backgroundColor = Color.LIGHT_GRAY
            labelColor = Color.BLACK
        } else if (currentPlayer == civ || currentPlayer.knows(civ) || currentPlayer.isDefeated() || currentPlayer.victoryManager.hasWon()) {
            add(ImageGetter.getNationPortrait(civ.nation, 30f))
            backgroundColor = civ.nation.getOuterColor()
            labelColor = civ.nation.getInnerColor()
        } else {
            add(ImageGetter.getRandomNationPortrait(30f))
            backgroundColor = Color.DARK_GRAY
            labelText = Constants.unknownNationName
        }

        background = BaseScreen.skinStrings.getUiBackground(
            "VictoryScreen/CivGroup",
            BaseScreen.skinStrings.roundedEdgeRectangleShape,
            backgroundColor
        )

        val label = labelText.toLabel(labelColor)
        label.setAlignment(Align.center)
        add(label).padLeft(10f)

        pack()
    }
}
