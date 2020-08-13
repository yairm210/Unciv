package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Tutorial
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Policy
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen


class PolicyPickerScreen(val worldScreen: WorldScreen, civInfo: CivilizationInfo = worldScreen.viewingCiv)
    : PickerScreen() {
    internal val viewingCiv: CivilizationInfo = civInfo
    private var pickedPolicy: Policy? = null

    init {
        val policies = viewingCiv.policies
        displayTutorial(Tutorial.CultureAndPolicies)

        rightSideButton.setText("{Adopt policy}\r\n(".tr() + policies.storedCulture + "/" + policies.getCultureNeededForNextPolicy() + ")")

        setDefaultCloseAction()
        if (policies.freePolicies > 0) {
            rightSideButton.setText("Adopt free policy".tr())
            closeButton.disable()
        }
        else onBackButtonClicked { UncivGame.Current.setWorldScreen() }

        rightSideButton.onClick(UncivSound.Policy) {
            viewingCiv.policies.adopt(pickedPolicy!!)

            // If we've moved to another screen in the meantime (great person pick, victory screen) ignore this
            if(game.screen !is PolicyPickerScreen || !policies.canAdoptPolicy()){
                game.setWorldScreen()
                dispose()
            }
            else game.setScreen(PolicyPickerScreen(worldScreen))  // update policies
        }

        if(!UncivGame.Current.worldScreen.canChangeState)
            rightSideButton.disable()

        topTable.row().pad(30f)

        for (branch in viewingCiv.gameInfo.ruleSet.policyBranches.values) {
            if (branch.name == "Commerce") topTable.addSeparator()
            val branchGroup = Table()
            branchGroup.row().pad(20f)
            branchGroup.add(getPolicyButton(branch, false)).row()

            var currentRow = 1
            var currentColumn = 1
            val branchTable = Table()
            for (policy in branch.policies) {
                if (policy.name.endsWith("Complete")) continue
                if (policy.row > currentRow) {
                    branchTable.row()
                    currentRow++
                    currentColumn = 1
                }
                if (policy.column > currentColumn) {
                    branchTable.add().colspan(policy.column - currentColumn) // empty space
                }
                branchTable.add(getPolicyButton(policy, true)).colspan(2)
                currentColumn = policy.column + 2
            }
            branchTable.pack()
            branchGroup.add(branchTable).height(150f).row()

            branchGroup.add(getPolicyButton(branch.policies.last(), false)) // finisher

            topTable.add(branchGroup)
        }
        topTable.pack()
    }

    private fun pickPolicy(policy: Policy) {
        if (!worldScreen.isPlayersTurn
                || viewingCiv.isDefeated()
                || viewingCiv.policies.isAdopted(policy.name)
                || policy.name.endsWith("Complete")
                || !viewingCiv.policies.isAdoptable(policy)
                || !viewingCiv.policies.canAdoptPolicy()) {
            rightSideButton.disable()
        } else {
            rightSideButton.enable()
        }
        if (viewingCiv.gameInfo.gameParameters.godMode && pickedPolicy == policy
                && viewingCiv.policies.isAdoptable(policy)) {
            viewingCiv.policies.adopt(policy)
            game.setScreen(PolicyPickerScreen(worldScreen))
        }
        pickedPolicy = policy
        val policyText = mutableListOf<String>()
        policyText += policy.name
        policyText += policy.uniques

        if (!policy.name.endsWith("Complete")) {
            if (policy.requires!!.isNotEmpty())
                policyText += "{Requires} ".tr() + policy.requires!!.joinToString { it.tr() }
            else
                policyText += ("{Unlocked at} {" + policy.branch.era + "}").tr()
        }
        descriptionLabel.setText(policyText.joinToString("\r\n") { it.tr() })
    }

    private fun getPolicyButton(policy: Policy, image: Boolean): Button {
        var policyButton = Button(skin)
        if (image) {
            val policyImage = ImageGetter.getImage("PolicyIcons/" + policy.name)
            policyButton.add(policyImage).size(30f)
        } else {
            policyButton = policy.name.toTextButton()
        }

        if (viewingCiv.policies.isAdopted(policy.name)) { // existing
            policyButton.color = Color.GREEN
        } else if (!viewingCiv.policies.isAdoptable(policy))
        // non-available
        {
            policyButton.color = Color.GRAY
        }
        policyButton.onClick { pickPolicy(policy) }
        policyButton.pack()
        return policyButton
    }

}