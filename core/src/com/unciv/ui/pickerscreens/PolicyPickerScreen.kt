package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.Policy
import com.unciv.models.gamebasics.StringUtils
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter

class PolicyPickerScreen(internal val civInfo: CivilizationInfo) : PickerScreen() {

    private var pickedPolicy: Policy? = null

    init {
        val policies = civInfo.policies
        displayTutorials("PolicyPickerScreen")

        rightSideButton.setText("Adopt policy\r\n(" + policies.storedCulture + "/" + policies.getCultureNeededForNextPolicy() + ")")

        if (policies.freePolicies > 0) {
            rightSideButton.setText("Adopt free policy")
            closeButton.color = Color.GRAY
            closeButton.touchable = Touchable.disabled
        }

        rightSideButton.addClickListener {
            if (policies.freePolicies > 0)
                policies.freePolicies--
            else
                policies.storedCulture -= policies.getCultureNeededForNextPolicy()
            civInfo.policies.adopt(pickedPolicy!!)

            game.screen = PolicyPickerScreen(civInfo)
        }



        topTable.row().pad(30f)

        for (branch in GameBasics.PolicyBranches.values) {
            if (branch.name == "Commerce") topTable.row()
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

            branchGroup.add(getPolicyButton(branch.policies[branch.policies.size - 1], false)) // finisher

            topTable.add(branchGroup)
        }
        topTable.pack()
    }

    private fun pickPolicy(policy: Policy) {
        if (civInfo.policies.isAdopted(policy.name)
                || !civInfo.policies.getAdoptedPolicies().containsAll(policy.requires!!)
                || !civInfo.policies.canAdoptPolicy()) {
            rightSideButton.touchable = Touchable.disabled
            rightSideButton.color = Color.GRAY
        } else {
            rightSideButton.color = Color.WHITE
            rightSideButton.touchable = Touchable.enabled
        }
        pickedPolicy = policy
        var policyText = policy.name + "\r\n" + policy.description + "\r\n"
        if (!policy.name.endsWith("Complete") && policy.requires!!.isNotEmpty())
            policyText += "Requires " + StringUtils.join(", ", policy.requires)
        descriptionLabel.setText(policyText)
    }

    private fun getPolicyButton(policy: Policy, image: Boolean): Button {
        var toReturn = Button(CameraStageBaseScreen.skin)
        if (image) {
            val policyImage = ImageGetter.getImage("PolicyIcons/" + policy.name.replace(" ", "_") + "_(Civ5).png")
            toReturn.add(policyImage).size(30f)
        } else
            toReturn = TextButton(policy.name, CameraStageBaseScreen.skin)

        if (civInfo.policies.isAdopted(policy.name)) { // existing
            toReturn.color = Color.GREEN
        } else if (!civInfo.policies.getAdoptedPolicies().containsAll(policy.requires!!))
        // non-available
        {
            toReturn.color = Color.GRAY
        }
        toReturn.addClickListener { pickPolicy(policy) }
        toReturn.pack()
        return toReturn
    }

}