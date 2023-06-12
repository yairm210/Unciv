package com.unciv.ui.screens.pickerscreens

import com.unciv.UncivGame
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toLabel

class DiplomaticVoteResultScreen(val votesCast: HashMap<String, String>, val viewingCiv: Civilization) : PickerScreen() {
    val gameInfo = viewingCiv.gameInfo

    init {
        closeButton.remove()

        addVote(viewingCiv.civName)

        for (civ in gameInfo.civilizations.filter { it.isMajorCiv() && it != viewingCiv })
            addVote(civ.civName)
        for (civ in gameInfo.civilizations.filter { it.isCityState() })
            addVote(civ.civName)

        rightSideButton.onClick(UncivSound.Click) {
            viewingCiv.addFlag(CivFlags.ShowDiplomaticVotingResults.name, -1)
            UncivGame.Current.popScreen()
        }
        rightSideButton.enable()
        rightSideButton.setText("Continue".tr())
    }

    private fun addVote(civName: String) {
        val civ = gameInfo.civilizations.firstOrNull { it.civName == civName }
        if (civ == null || civ.isDefeated()) return

        topTable.add(ImageGetter.getNationPortrait(civ.nation, 30f)).pad(10f)
        topTable.add(civName.toLabel()).pad(20f)
        if (civName !in votesCast.keys) {
            topTable.add("Abstained".toLabel()).row()
            return
        }

        val votedCiv = gameInfo.civilizations.firstOrNull { it.civName == votesCast[civName] }!!
        if (votedCiv.isDefeated()) {
            topTable.add("Abstained".toLabel()).row()
            return
        }

        topTable.add("Voted for".toLabel()).pad(20f)
        topTable.add(ImageGetter.getNationPortrait(votedCiv.nation, 30f)).pad(10f)
        topTable.add(votedCiv.civName.toLabel()).row()
    }
}
