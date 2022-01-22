package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Belief
import com.unciv.models.translations.tr
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class PantheonPickerScreen(choosingCiv: CivilizationInfo, gameInfo: GameInfo) : PickerScreen() {
    private var chosenPantheon: Belief? = null
    
    init {
        closeButton.isVisible = true
        setDefaultCloseAction()
        
        rightSideButton.setText("Choose a pantheon".tr())
        
        topTable.apply { defaults().pad(10f) }
        for (belief in gameInfo.ruleSet.beliefs.values) {
            if (!choosingCiv.religionManager.isPickablePantheonBelief(belief)) continue
            val beliefTable = Table(skin).apply { touchable = Touchable.enabled; 
                background =
                    // Ideally I want to this to be the darker blue we use for pressed buttons, but I suck at UI so I'll leave it like this.
                    if (belief == chosenPantheon) ImageGetter.getBackground(ImageGetter.getBlue()) 
                    else ImageGetter.getBackground(ImageGetter.getBlue()) 
            }
            beliefTable.pad(10f)
            beliefTable.add(belief.name.toLabel(fontSize = Constants.headingFontSize)).row()
            beliefTable.add(belief.uniques.joinToString().toLabel())
            beliefTable.onClick { 
                chosenPantheon = belief
                pick("Follow [${chosenPantheon!!.name}]".tr())
            }
            topTable.add(beliefTable).fillX().row()
        }

        rightSideButton.onClick(UncivSound.Choir) {
            choosingCiv.religionManager.choosePantheonBelief(chosenPantheon!!)
            UncivGame.Current.setWorldScreen()
        }
    }
}
