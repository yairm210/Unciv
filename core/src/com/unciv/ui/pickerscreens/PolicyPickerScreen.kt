package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.TutorialTrigger
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.RecreateOnResize
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.pad
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.math.min


class PolicyPickerScreen(val worldScreen: WorldScreen, civInfo: CivilizationInfo = worldScreen.viewingCiv)
    : PickerScreen(), RecreateOnResize {
    internal val viewingCiv: CivilizationInfo = civInfo
    private var pickedPolicy: Policy? = null

    init {
        val policies = viewingCiv.policies
        displayTutorial(TutorialTrigger.CultureAndPolicies)

        rightSideButton.setText(when {
            policies.allPoliciesAdopted(checkEra = false) ->
                "All policies adopted"
            policies.freePolicies > 0 ->
                "Adopt free policy"
            else ->
                "{Adopt policy}\n(${policies.storedCulture}/${policies.getCultureNeededForNextPolicy()})"
        }.tr())

        setDefaultCloseAction()

        if (policies.freePolicies > 0 && policies.canAdoptPolicy())
            closeButton.disable()

        rightSideButton.onClick(UncivSound.Policy) {
            val policy = pickedPolicy!!

            // Evil people clicking on buttons too fast to confuse the screen - #4977
            if (!policyIsPickable(policy)) return@onClick

            viewingCiv.policies.adopt(policy)

            // If we've moved to another screen in the meantime (great person pick, victory screen) ignore this
            if (game.screen !is PolicyPickerScreen || !policies.canAdoptPolicy()) {
                game.popScreen()
            } else {
                val policyScreen = PolicyPickerScreen(worldScreen)
                policyScreen.scrollPane.scrollPercentX = scrollPane.scrollPercentX
                policyScreen.scrollPane.scrollPercentY = scrollPane.scrollPercentY
                policyScreen.scrollPane.updateVisualScroll()
                game.replaceCurrentScreen(policyScreen)  // update policies
            }
        }

        if (!worldScreen.canChangeState)
            rightSideButton.disable()

        topTable.row()

        val branches = viewingCiv.gameInfo.ruleSet.policyBranches
        var rowChangeCount = Int.MAX_VALUE
        var rowChangeWidth = Float.MAX_VALUE

        // estimate how many branch boxes fit using average size (including pad)
        // TODO If we'd want to use scene2d correctly, this is supposed to happen inside an overridden layout() method
        val numBranchesX = scrollPane.width / 242f
        val numBranchesY = scrollPane.height / 305f
        // plan a nice geometry
        if (scrollPane.width < scrollPane.height) {
            // Portrait - arrange more in the vertical direction
            if (numBranchesX < 2.5f) rowChangeCount = 2
            else rowChangeWidth = scrollPane.width + 10f  // 10f to ignore 1 horizontal padding
        } else {
            // Landscape - arrange in as few rows as looks nice
            if (numBranchesY > 1.5f) {
                val numRows = if (numBranchesY < 2.9f) 2 else (numBranchesY + 0.1f).toInt()
                    rowChangeCount = (branches.size + numRows - 1) / numRows
            }
        }

        // Actually create and distribute the policy branches
        var wrapper = Table()
        var wrapperWidth = 0f   // Either pack() each round or cumulate separately
        for ( (index, branch) in branches.values.withIndex()) {
            val branchGroup = getBranchGroup(branch)
            wrapperWidth += branchGroup.width + 20f  // 20 is the horizontal padding in wrapper.add

            if (index > 0 && index % rowChangeCount == 0 || wrapperWidth > rowChangeWidth) {
                topTable.add(wrapper).pad(5f,10f)
                topTable.addSeparator()
                wrapper = Table()
                wrapperWidth = branchGroup.width
            }

            wrapper.add(branchGroup).pad(10f)
        }
        topTable.add(wrapper).pad(5f,10f)

        // If topTable is larger than available space, scroll in a little - up to top/left
        // total padding, or up to where the axis is centered, whichever is smaller
        splitPane.pack()    // packs topTable but also ensures scrollPane.maxXY is calculated
        if (topTable.height > scrollPane.height) {
            val vScroll = min(15f, scrollPane.maxY / 2)
            scrollPane.scrollY = vScroll
        }
        if (topTable.width > scrollPane.width) {
            val hScroll = min(20f, scrollPane.maxX / 2)
            scrollPane.scrollX = hScroll
        }
        scrollPane.updateVisualScroll()
    }

    private fun policyIsPickable(policy: Policy):Boolean {
        if (!worldScreen.isPlayersTurn
            || worldScreen.viewingCiv.isSpectator() // viewingCiv var points to selectedCiv in case of spectator
            || viewingCiv.isDefeated()
            || viewingCiv.policies.isAdopted(policy.name)
            || policy.policyBranchType == PolicyBranchType.BranchComplete
            || !viewingCiv.policies.isAdoptable(policy)
            || !viewingCiv.policies.canAdoptPolicy()
        )
            return false
        return true
    }

    private fun pickPolicy(policy: Policy) {
        if (!policyIsPickable(policy)) {
            rightSideButton.disable()
        } else {
            rightSideButton.enable()
        }
        if (viewingCiv.gameInfo.gameParameters.godMode && pickedPolicy == policy
                && viewingCiv.policies.isAdoptable(policy)) {
            viewingCiv.policies.adopt(policy)
            game.replaceCurrentScreen(PolicyPickerScreen(worldScreen))
        }
        pickedPolicy = policy

        descriptionLabel.setText(policy.getDescription())
    }

    /**
     * Create a Widget for a complete policy branch including Starter and "complete" buttons.
     * @param branch the policy branch to display
     * @return a [Table], with outer padding _zero_
     */
    private fun getBranchGroup(branch: PolicyBranch): Table {
        val branchGroup = Table()
        branchGroup.row()
        branchGroup.add(getPolicyButton(branch, false))
            .minWidth(160f).padBottom(15f).row()

        var currentRow = 1
        var currentColumn = 1
        val branchTable = Table()
        for (policy in branch.policies) {
            if (policy.policyBranchType == PolicyBranchType.BranchComplete) continue
            if (policy.row > currentRow) {
                branchTable.row().pad(2.5f)
                currentRow++
                currentColumn = 1
            }
            if (policy.column > currentColumn) {
                branchTable.add().colspan(policy.column - currentColumn) // empty space
            }
            branchTable.add(getPolicyButton(policy, true)).colspan(2)
            currentColumn = policy.column + 2
        }

        branchGroup.add(branchTable).height(150f).row()

        // Add the finisher button.
        branchGroup.add(getPolicyButton(branch.policies.last(), false)).padTop(15f)

        // Ensure dimensions are calculated
        branchGroup.pack()
        return branchGroup
    }

    private fun getPolicyButton(policy: Policy, image: Boolean): Button {
        var policyButton = Button(skin)
        if (image) {
            val policyImage = ImageGetter.getImage("PolicyIcons/" + policy.name)
            policyButton.add(policyImage).size(30f)
        } else {
            policyButton = policy.name.toTextButton()
        }

        if (viewingCiv.policies.isAdopted(policy.name)) policyButton.color = Color.GREEN // existing
        else if (!viewingCiv.policies.isAdoptable(policy)) policyButton.color = Color.GRAY // non-available


        policyButton.onClick { pickPolicy(policy) }
        policyButton.pack()
        return policyButton
    }

    override fun recreate(): BaseScreen = PolicyPickerScreen(worldScreen, viewingCiv)
}
