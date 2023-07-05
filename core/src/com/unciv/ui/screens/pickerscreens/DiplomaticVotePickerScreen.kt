package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.images.ImageGetter

class DiplomaticVotePickerScreen(private val votingCiv: Civilization) : PickerScreen() {
    private var chosenCiv: String? = null

    init {
        setDefaultCloseAction()
        rightSideButton.setText("Choose a civ to vote for".tr())

        descriptionLabel.setText("Choose who should become the world leader and win a Diplomatic Victory!".tr())

        val choosableCivs = votingCiv.diplomacyFunctions.getKnownCivsSorted(false)
        for (civ in choosableCivs) {
            addButton(civ.civName, "Vote for [${civ.civName}]", civ.civName,
                ImageGetter.getNationPortrait(
                    civ.nation,
                    PickerPane.pickerOptionIconSize
                )
            )
        }
        addButton("Abstain", "Abstain", null,
            ImageGetter.getImage("OtherIcons/Stop").apply {
                setSize(PickerPane.pickerOptionIconSize, PickerPane.pickerOptionIconSize)
            }
        )

        rightSideButton.onClick(UncivSound.Chimes, ::voteAndClose)
    }

    private fun voteAndClose() {
        votingCiv.diplomaticVoteForCiv(chosenCiv)
        UncivGame.Current.popScreen()
    }

    private fun addButton(caption: String, pickText: String, choice: String?, icon: Actor) {
        val button = PickerPane.getPickerOptionButton(icon, caption)
        button.onClick {
            chosenCiv = choice
            pick(pickText.tr())
        }
        button.onDoubleClick(UncivSound.Chimes) {
            chosenCiv = choice
            voteAndClose()
        }
        topTable.add(button).fillX().pad(10f).row()
    }
}
