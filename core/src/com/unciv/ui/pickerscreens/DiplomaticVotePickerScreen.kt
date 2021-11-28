package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class DiplomaticVotePickerScreen(private val votingCiv: CivilizationInfo) : PickerScreen() {
    private var chosenCiv: String? = null

    init {
        setDefaultCloseAction()
        rightSideButton.setText("Choose a civ to vote for".tr())

        descriptionLabel.setText("Choose who should become the world leader and win a diplomatic victory!".tr())

        val choosableCivs = votingCiv.gameInfo.civilizations.filter { it.isMajorCiv() && it != votingCiv && !it.isDefeated() }
        for (civ in choosableCivs)
        {
            val button = Button(skin)

            button.add(ImageGetter.getNationIndicator(civ.nation, 30f)).pad(10f)
            button.add(civ.civName.toLabel()).pad(10f)
            button.pack()
            button.onClick {
                chosenCiv = civ.civName
                pick("Vote for [${civ.civName}]".tr())
            }
            topTable.add(button).pad(10f).row()
        }

        rightSideButton.onClick(UncivSound.Chimes) {
            votingCiv.diplomaticVoteForCiv(chosenCiv!!)
            UncivGame.Current.setWorldScreen()
        }

    }
}
