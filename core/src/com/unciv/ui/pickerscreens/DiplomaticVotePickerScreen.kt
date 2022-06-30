package com.unciv.ui.pickerscreens

import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.onClick

class DiplomaticVotePickerScreen(private val votingCiv: CivilizationInfo) : PickerScreen() {
    private var chosenCiv: String? = null

    init {
        setDefaultCloseAction()
        rightSideButton.setText("Choose a civ to vote for".tr())

        descriptionLabel.setText("Choose who should become the world leader and win a Diplomatic Victory!".tr())

        val choosableCivs = votingCiv.gameInfo.civilizations.filter { it.isMajorCiv() && it != votingCiv && !it.isDefeated() }
        for (civ in choosableCivs)
        {
            val button = PickerPane.getPickerOptionButton(ImageGetter.getNationIndicator(civ.nation, PickerPane.pickerOptionIconSize), civ.civName)
            button.pack()
            button.onClick {
                chosenCiv = civ.civName
                pick("Vote for [${civ.civName}]".tr())
            }
            topTable.add(button).pad(10f).row()
        }

        rightSideButton.onClick(UncivSound.Chimes) {
            votingCiv.diplomaticVoteForCiv(chosenCiv!!)
            UncivGame.Current.popScreen()
        }

    }
}
