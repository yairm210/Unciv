package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.WrappableLabel
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick

class PantheonPickerScreen(choosingCiv: CivilizationInfo, gameInfo: GameInfo) : PickerScreen() {
    private var selectedPantheon: Belief? = null
    private var selectedButton: Button? = null

    init {
        closeButton.isVisible = true
        setDefaultCloseAction()

        rightSideButton.setText("Choose a pantheon".tr())

        topTable.defaults().pad(10f).fillX()

        for (belief in gameInfo.ruleSet.beliefs.values) {
            if (belief.type != BeliefType.Pantheon) continue
            val beliefButton = getBeliefButton(belief)
            if (choosingCiv.religionManager.isPickablePantheonBelief(belief)) {
                beliefButton.onClick {
                    selectedButton?.enable()
                    selectedButton = beliefButton
                    beliefButton.disable()
                    selectedPantheon = belief
                    pick("Follow [${belief.name}]".tr())
                }
            } else {
                beliefButton.touchable = Touchable.disabled
                beliefButton.color = Color(0x7F0000ff)
            }
            topTable.add(beliefButton).row()
        }

        rightSideButton.onClick(UncivSound.Choir) {
            choosingCiv.religionManager.choosePantheonBelief(selectedPantheon!!)
            UncivGame.Current.setWorldScreen()
            dispose()
        }
    }

    private fun getBeliefButton(belief: Belief): Button {
        val labelWidth = stage.width * 0.9f - 52f
        return Button(skin).apply {
            val nameLabel = WrappableLabel(belief.name, labelWidth, fontSize = Constants.headingFontSize)
            add(nameLabel.apply { wrap = true }).row()
            val effectLabel = WrappableLabel(belief.uniques.joinToString("\n") { it.tr() }, labelWidth)
            add(effectLabel.apply { wrap = true })
        }
    }
}
