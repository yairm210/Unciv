package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.ImageGetter

class DiplomaticVoteResultScreen(
    private val votesCast: HashMap<String, String?>,
    viewingCiv: Civilization
) : PickerScreen() {
    val gameInfo = viewingCiv.gameInfo
    private val constructionNameUN: String?
    private val civOwningUN: String?

    init {
        closeButton.remove()

        val findUN = viewingCiv.victoryManager.getUNBuildingAndOwnerNames()
        constructionNameUN = findUN.first
        civOwningUN = findUN.second

        val orderedCivs = gameInfo.getCivsSorted(civToSortFirst = viewingCiv)
        for (civ in orderedCivs) addVote(civ)

        val result = viewingCiv.victoryManager.getDiplomaticVictoryVoteBreakdown()
        descriptionLabel.setAlignment(Align.center)
        descriptionLabel.setText(result.tr())

        rightSideButton.onActivation(UncivSound.Click) {
            viewingCiv.addFlag(CivFlags.ShowDiplomaticVotingResults.name, -1)
            game.popScreen()
        }
        rightSideButton.keyShortcuts.add(KeyCharAndCode.BACK)
        rightSideButton.keyShortcuts.add(KeyCharAndCode.SPACE)
        rightSideButton.enable()
        rightSideButton.setText("Continue".tr())
        bottomTable.cells[0].minWidth(rightSideButton.prefWidth + 20f)  // center descriptionLabel
    }

    private fun addVote(civ: Civilization) {
        val civName = civ.civName

        topTable.add(ImageGetter.getNationPortrait(civ.nation, 30f)).pad(10f)
        topTable.add(civName.toLabel(hideIcons = true)).pad(20f)

        if (civName == civOwningUN && constructionNameUN != null) {
            topTable.add(ImageGetter.getConstructionPortrait(constructionNameUN, 30f))
                .pad(10f)
            topTable.add("[2] votes".toLabel())
        } else {
            topTable.add("[1] vote".toLabel()).colspan(2)
        }

        fun abstained() = topTable.add("Abstained".toLabel()).colspan(3).row()
        val votedCivName = votesCast[civName]
            ?: return abstained()

        val votedCiv = gameInfo.getCivilization(votedCivName)
        if (votedCiv.isDefeated()) return abstained()

        topTable.add("Voted for".toLabel()).pad(20f).padRight(0f)
        topTable.add(ImageGetter.getNationPortrait(votedCiv.nation, 30f)).pad(10f)
        topTable.add(votedCiv.civName.toLabel(hideIcons = true))
        topTable.row()
    }
}
