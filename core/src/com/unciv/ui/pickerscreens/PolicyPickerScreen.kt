package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
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
    private data class PickedPolicyAndButton (val policy: Policy, val button: Button)
    private var pickedPolicy: PickedPolicyAndButton? = null
    private var highlightedButton: Button? = null
    private val virtualBranchBoxSizeX = 6
    private val virtualBranchBoxSizeY = 8

    init {
        arrowKeyStraightLineBias = 2

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
            viewingCiv.policies.adopt(pickedPolicy!!.policy)

            // If we've moved to another screen in the meantime (great person pick, victory screen) ignore this
            if(game.screen !is PolicyPickerScreen || !policies.canAdoptPolicy()){
                game.setWorldScreen()
                dispose()
            }
            else game.setScreen(PolicyPickerScreen(worldScreen))  // update policies
        }
        if(!UncivGame.Current.worldScreen.isPlayersTurn)
            rightSideButton.disable()
        

        topTable.row().pad(30f)
        var branchRow = 0
        var branchCol = 0
        for (branch in viewingCiv.gameInfo.ruleSet.policyBranches.values) {
            if (branch.name == "Commerce") {
                topTable.addSeparator()
                branchRow = 1
                branchCol = 0
            }
            val branchGroup = Table()
            branchGroup.row().pad(20f)
            branchGroup.add(getPolicyButton(branch, false,
                    branchCol * virtualBranchBoxSizeX + virtualBranchBoxSizeX/2, branchRow * virtualBranchBoxSizeY)).row()

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
                branchTable.add(getPolicyButton(policy, true,
                        branchCol * virtualBranchBoxSizeX + policy.column, branchRow * virtualBranchBoxSizeY + policy.row + 1)).colspan(2)
                currentColumn = policy.column + 2
            }
            branchTable.pack()
            branchGroup.add(branchTable).height(150f).row()

            branchGroup.add(getPolicyButton(branch.policies.last(), false,
                    branchCol * virtualBranchBoxSizeX + virtualBranchBoxSizeX/2, branchRow * virtualBranchBoxSizeY + 6)) // finisher

            topTable.add(branchGroup)
            branchCol += 1
        }
        topTable.pack()
    }

    private fun pickPolicy(policyAndButton: PickedPolicyAndButton) {
        val policy = policyAndButton.policy
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
        pickedPolicy = policyAndButton
        var policyText = policy.name.tr() + "\r\n" + policy.description.tr() + "\r\n"
        if (!policy.name.endsWith("Complete")){
            if(policy.requires!!.isNotEmpty())
                policyText += "{Requires} ".tr() + policy.requires!!.joinToString { it.tr() }
            else
                policyText += ("{Unlocked at} {"+policy.branch.era.toString()+" era}").tr()
        }
        descriptionLabel.setText(policyText)
        policyAndButton.button.highlight()
    }

    private fun getPolicyButton(policy: Policy, image: Boolean, x: Int, y: Int): Button {
        var policyButton = Button(skin)
        if (image) {
            val policyImage = ImageGetter.getImage("PolicyIcons/" + policy.name)
            policyButton.add(policyImage).size(30f)
        } else {
            policyButton = TextButton(policy.name.tr(), skin)
        }

        if (viewingCiv.policies.isAdopted(policy.name)) { // existing
            policyButton.color = Color.GREEN
        } else if (!viewingCiv.policies.isAdoptable(policy))
        // non-available
        {
            policyButton.color = Color.GRAY
        }
        val action =  { pickPolicy(PickedPolicyAndButton(policy,policyButton)) }
        policyButton.onClick (action)
        registerKeyHandler (policy.name.tr(), action, x, y)
        policyButton.pack()
        return policyButton
    }

    private fun Button.highlight (highlight: Boolean = true) {
        if (highlight) {
            highlightedButton?.highlight(false)
            highlightedButton = null
        }
        val newColor = if (highlight) Color.GOLDENROD else Color.WHITE
        (this.children.firstOrNull { it is Image } as Image?)?.color = newColor
        (this.children.firstOrNull { it is Label } as Label?)?.color = newColor
        if (highlight)
            highlightedButton = this
    }
}