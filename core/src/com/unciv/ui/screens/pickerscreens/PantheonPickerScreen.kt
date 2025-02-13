package com.unciv.ui.screens.pickerscreens

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr

class PantheonPickerScreen(
    choosingCiv: Civilization
) : ReligionPickerScreenCommon(choosingCiv) {
    private var selectedPantheon: Belief? = null
    private val selection = Selection()

    init {
        topTable.defaults().pad(10f).fillX()

        for (belief in ruleset.beliefs.values) {
            if (belief.type != BeliefType.Pantheon) continue
            val beliefButton = getBeliefButton(belief, withTypeLabel = false)
            if (choosingCiv.religionManager.getReligionWithBelief(belief) == null
                && belief.getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals)
                    .none { !it.conditionalsApply(choosingCiv.state) }) {
                beliefButton.onClickSelect(selection, belief) {
                    selectedPantheon = belief
                    pick("Follow [${belief.name}]".tr())
                }
            } else {
                beliefButton.disable(redDisableColor)
            }
            topTable.add(beliefButton).row()
        }

        setOKAction("Choose a pantheon") {
            chooseBeliefs(listOf(selectedPantheon!!), useFreeBeliefs = usingFreeBeliefs())
        }
    }
}
