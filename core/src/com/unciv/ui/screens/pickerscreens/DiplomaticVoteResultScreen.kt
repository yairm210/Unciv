package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.civilopediascreen.FormattedLine

class DiplomaticVoteResultScreen(
    private val votesCast: HashMap<String, String>,
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

        rightSideButton.onClick(UncivSound.Click) {
            viewingCiv.addFlag(CivFlags.ShowDiplomaticVotingResults.name, -1)
            UncivGame.Current.popScreen()
        }
        rightSideButton.enable()
        rightSideButton.setText("Continue".tr())
    }

    private fun addVote(civ: Civilization) {
        val civName = civ.civName

        topTable.add(ImageGetter.getNationPortrait(civ.nation, 30f)).pad(10f)
        topTable.add(civName.toLabel()).pad(20f)

        if (civName == civOwningUN && constructionNameUN != null) {
            topTable.add(ImageGetter.getConstructionPortrait(constructionNameUN!!, 30f))
                .pad(10f)
        } else {
            topTable.add()
        }

        fun abstained() = topTable.add("Abstained".toLabel()).row()
        if (civName !in votesCast.keys) return abstained()

        val votedCiv = gameInfo.getCivilization(votesCast[civName]!!)
        if (votedCiv.isDefeated()) return abstained()

        topTable.add("Voted for".toLabel()).pad(20f)
        topTable.add(ImageGetter.getNationPortrait(votedCiv.nation, 30f)).pad(10f)
        topTable.add(votedCiv.civName.toLabel())
        if (civName == civOwningUN)
            topTable.add(ImageGetter.getImage(FormattedLine.starImage).apply { color = Color.GOLD }).size(18f)
        topTable.row()
    }
}
