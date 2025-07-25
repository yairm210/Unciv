package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.models.TutorialTrigger
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toGroup
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.components.widgets.BorderedTable
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private enum class PolicyColors(
    val default: Color
) {
    // The getUIColor comments are picked up by UiElementDocsWriter, the actual call is not.
    ButtonBGPickable(colorFromRGB(32,46,64)),             // getUIColor("PolicyScreen/Colors/ButtonBGPickable", colorFromRGB(32,46,64))
    ButtonBGPickableSelected(colorFromRGB(37,87,82)),     // getUIColor("PolicyScreen/Colors/ButtonBGPickableSelected", colorFromRGB(37,87,82))
    ButtonBGNotPickable(colorFromRGB(20,20,20)),          // getUIColor("PolicyScreen/Colors/ButtonBGNotPickable", colorFromRGB(20,20,20))
    ButtonBGNotPickableSelected(colorFromRGB(20,20,20)),  // getUIColor("PolicyScreen/Colors/ButtonBGNotPickableSelected", colorFromRGB(20,20,20))
    ButtonBGAdopted(colorFromRGB(1,17,19)),               // getUIColor("PolicyScreen/Colors/ButtonBGAdopted", colorFromRGB(1,17,19))
    ButtonBGAdoptedSelected(colorFromRGB(1,17,19)),       // getUIColor("PolicyScreen/Colors/ButtonBGAdoptedSelected", colorFromRGB(1,17,19))

    ButtonIconPickable(Color.WHITE),                              // getUIColor("PolicyScreen/Colors/ButtonIconPickable", Color.WHITE)
    ButtonIconPickableSelected(Color.WHITE),                      // getUIColor("PolicyScreen/Colors/ButtonIconPickableSelected", Color.WHITE)
    ButtonIconNotPickable(Color.valueOf("ffffff33")),         // getUIColor("PolicyScreen/Colors/ButtonIconNotPickable", Color(0xffffff33))
    ButtonIconNotPickableSelected(Color.valueOf("ffffff33")), // getUIColor("PolicyScreen/Colors/ButtonIconNotPickableSelected", Color(0xffffff33))
    ButtonIconAdopted(Color.GOLD),                                // getUIColor("PolicyScreen/Colors/ButtonIconAdopted", Color.GOLD)
    ButtonIconAdoptedSelected(Color.GOLD),                        // getUIColor("PolicyScreen/Colors/ButtonIconAdoptedSelected", Color.GOLD)

    BranchBGCompleted(colorFromRGB(255,205,0)),           // getUIColor("PolicyScreen/Colors/BranchBGCompleted", colorFromRGB(255,205,0))
    BranchBGNotAdopted(colorFromRGB(5,45,65)),            // getUIColor("PolicyScreen/Colors/BranchBGNotAdopted", colorFromRGB(5,45,65))
    BranchBGAdopted(colorFromRGB(50,45,5)),               // getUIColor("PolicyScreen/Colors/BranchBGAdopted", colorFromRGB(50,45,5))

    BranchHeaderBG(colorFromRGB(47,90,92)),               // getUIColor("PolicyScreen/Colors/BranchHeaderBG", colorFromRGB(47,90,92))

    BranchLabelAdopted(colorFromRGB(150,70,40)),          // getUIColor("PolicyScreen/Colors/BranchLabelAdopted", colorFromRGB(150,70,40))
    BranchLabelPickable(Color.WHITE),                              // getUIColor("PolicyScreen/Colors/BranchLabelPickable", Color.WHITE)
    BranchLabelNotPickable(Color.valueOf("ffffff7f")),        // getUIColor("PolicyScreen/Colors/BranchLabelNotPickable", Color(0xffffff7f))

    ;
    val color get() = BaseScreen.skinStrings.getUIColor("PolicyScreen/Colors/$name", default)
}

private fun Policy.isPickable(viewingCiv: Civilization, canChangeState: Boolean) =
    viewingCiv.isCurrentPlayer()
        && canChangeState
        && !viewingCiv.isDefeated()
        && !viewingCiv.policies.isAdopted(this.name)
        && policyBranchType != PolicyBranchType.BranchComplete
        && viewingCiv.policies.isAdoptable(this)
        && viewingCiv.policies.canAdoptPolicy()

private class PolicyButton(viewingCiv: Civilization, canChangeState: Boolean, val policy: Policy, size: Float = 30f) : BorderedTable(
    path = "PolicyScreen/PolicyButton",
    defaultBgBorder = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
    defaultBgShape = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
) {

    val icon = ImageGetter.getPolicyIcon(policy.name)

    private val isPickable = policy.isPickable(viewingCiv, canChangeState)
    private val isAdopted = viewingCiv.policies.isAdopted(policy.name)

    var isSelected = false
        set(value) {
            field = value
            updateState()
        }

    init {
        borderSize = 2f
        icon.setSize(size*0.7f, size*0.7f)

        addActor(icon)

        updateState()
        pack()
        width = size
        height = size

        icon.toFront()
        icon.center(this)
    }

    fun onClick(function: () -> Unit): PolicyButton {
        (this as Actor).onClick {
            function()
            updateState()
        }
        return this
    }

    private fun updateState() {
        val colors = when {
            isSelected && isPickable ->
                PolicyColors.ButtonBGPickableSelected to PolicyColors.ButtonIconPickableSelected
            isPickable ->
                PolicyColors.ButtonBGPickable to PolicyColors.ButtonIconPickable
            isSelected && isAdopted ->
                PolicyColors.ButtonBGAdoptedSelected to PolicyColors.ButtonIconAdoptedSelected
            isAdopted ->
                PolicyColors.ButtonBGAdopted to PolicyColors.ButtonIconAdopted
            isSelected ->
                PolicyColors.ButtonBGNotPickableSelected to PolicyColors.ButtonIconNotPickableSelected
            else ->
                PolicyColors.ButtonBGNotPickable to PolicyColors.ButtonIconNotPickable
        }
        bgColor = colors.first.color
        icon.color = colors.second.color
    }
}


class PolicyPickerScreen(
    val viewingCiv: Civilization,
    val canChangeState: Boolean,
    select: String? = null
) : PickerScreen(), RecreateOnResize {

    object Sizes {
        const val paddingVertical = 10f
        const val paddingHorizontal = 20f
        const val paddingBetweenHor = 10f
        const val paddingBetweenVer = 20f
        const val iconSize = 50f
    }

    private val policyNameToButton = HashMap<String, PolicyButton>()
    private var selectedPolicyButton: PolicyButton? = null

    init {
        val branchToGroup = HashMap<String, BranchGroup>()

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

        rightSideButton.onClick(UncivSound.Policy) {
            confirmAction()
        }

        if (!canChangeState)
            rightSideButton.disable()

        topTable.row()

        val branches = viewingCiv.gameInfo.ruleset.policyBranches
        val branchesPerRow: Int

        // estimate how many branch boxes fit using average size (including pad)
        // TODO If we'd want to use scene2d correctly, this is supposed to happen inside an overridden layout() method
        val numBranchesY = scrollPane.height / 305f
            // Landscape - arrange in as few rows as looks nice
        branchesPerRow = if (numBranchesY > 1.5f) {
            val numRows = if (numBranchesY < 2.9f) 2 else (numBranchesY + 0.1f).toInt()
            (branches.size + numRows - 1) / numRows
        } else branches.size


        // Actually create and distribute the policy branches
        val numberOfRows = ceil(branches.size / branchesPerRow.toFloat()).toInt()

        val positionToTable = HashMap<String,Table>()
        val allPoliciesTable = Table()
        for (rowNum in 0 until numberOfRows){
            val row = Table()
            for (columnNum in 0 until branchesPerRow){
                val branchTable = Table()
                row.add(branchTable).grow()
                positionToTable["$rowNum-$columnNum"] = branchTable
            }
            allPoliciesTable.add(row).pad(5f,10f)
            if (rowNum != numberOfRows-1) allPoliciesTable.addSeparator().pad(0f, 10f)
        }

        for ((index, branch) in branches.values.withIndex()){
            val branchGroup = BranchGroup(branch)
            branchToGroup[branch.name] = branchGroup

            val rowNumber = index / branchesPerRow
            val isRowLeftToRight = rowNumber % 2 == 0
            val numberInRow =  index % branchesPerRow // RTL rows
            val rowPosition = if (isRowLeftToRight) numberInRow else branchesPerRow-1-numberInRow
            val policyTable = positionToTable["$rowNumber-$rowPosition"]!!
            policyTable.add(branchGroup).grow()
        }
        topTable.add(allPoliciesTable)


        // If topTable is larger than available space, scroll in a little - up to top/left
        // total padding, or up to where the axis is centered, whichever is smaller
        splitPane.pack()    // packs topTable but also ensures scrollPane.maxXY is calculated
        if (topTable.height > scrollPane.height) {
            val vScroll = min(0f, scrollPane.maxY / 2)
            scrollPane.scrollY = vScroll
        }
        if (topTable.width > scrollPane.width) {
            val hScroll = min(20f, scrollPane.maxX / 2)
            scrollPane.scrollX = hScroll
        }
        scrollPane.updateVisualScroll()

        when (select) {
            in branches -> branchToGroup[select]?.toggle()
            in policyNameToButton -> pickPolicy(policyNameToButton[select]!!)
        }
    }

    override fun getCivilopediaRuleset() = viewingCiv.gameInfo.ruleset

    private fun pickPolicy(button: PolicyButton) {

        val policy = button.policy

        rightSideButton.isVisible = !viewingCiv.policies.isAdopted(policy.name)
        if (!policy.isPickable(viewingCiv, canChangeState)) {
            rightSideButton.disable()
        } else {
            rightSideButton.enable()
        }

        selectedPolicyButton?.isSelected = false
        selectedPolicyButton = button
        selectedPolicyButton?.isSelected = true

        descriptionLabel.setText(policy.getDescription())
        descriptionLabel.clearListeners()
        descriptionLabel.onActivation {
            openCivilopedia(policy.makeLink())
        }
        descriptionLabel.keyShortcuts.add(KeyboardBinding.Civilopedia)
    }

    /**
     * Create a Widget for a complete policy branch including Starter and "complete" buttons.
     * @param branch the policy branch to display
     * @return a [Table], with outer padding _zero_
     */
    private inner class BranchGroup(branch: PolicyBranch) : BorderedTable(path = "PolicyScreen/PolicyBranchBackground") {
        private val header = getBranchHeader(branch)
        private val group = Group()
        private val groupCell: Cell<Group>
        private val topBtn = getTopButton(branch)
        private val topBtnCell: Cell<Table>
        private val labelTable = Table()

        init {
            // Calculate preferred size
            val maxCol = max(5, branch.policies.maxOf { it.column })
            val maxRow = branch.policies.maxOf { it.row }

            val prefWidth = Sizes.paddingHorizontal * 2 + Sizes.iconSize * maxCol - (Sizes.iconSize - Sizes.paddingBetweenHor) * (maxCol - 1) / 2
            val prefHeight = Sizes.paddingVertical * 2 + Sizes.iconSize * maxRow + Sizes.paddingBetweenVer * (maxRow - 1)

            // Main table
            bgColor = if (viewingCiv.policies.isAdopted(branch.name))
                PolicyColors.BranchBGAdopted.color else PolicyColors.BranchBGNotAdopted.color

            // Header
            add(header).growX().row()

            // Description
            val onAdoption = branch.getDescription()
            val onCompletion = branch.policies.last().getDescription()
            var text = ""
            if (viewingCiv.gameInfo.ruleset.eras[branch.era]!!.eraNumber > viewingCiv.getEraNumber())
                text += "{Unlocked at} {${branch.era}}" + "\n\n"
            text += "{On adoption}:" + "\n\n" + onAdoption + "\n\n" +
                "{On completion}:" + "\n\n" + onCompletion

            val label = text.toLabel(fontSize = 13)
            label.setFillParent(false)
            label.setAlignment(Align.topLeft)
            label.wrap = true
            labelTable.add(label).pad(7f, 20f, 10f, 20f).grow().row()

            if (branch.uniqueMap.getUniques(UniqueType.OnlyAvailable).any()) {
                var warning = UniqueType.OnlyAvailable.text.tr() + ":\n"
                for (unique in branch.uniqueMap.getUniques(UniqueType.OnlyAvailable))
                    for (conditional in unique.modifiers) {
                        warning += "• " + conditional.text.tr() + "\n"
                    }
                val warningLabel = ColorMarkupLabel(warning, Color.RED, fontSize = 13)
                warningLabel.setAlignment(Align.topLeft)
                warningLabel.wrap = true
                labelTable.add(warningLabel).pad(0f, 20f, 17f, 20f).grow()
            }

            // Top button
            topBtnCell = add(topBtn).growX().pad(10f, 10f, 0f, 10f)
            row()

            // Main grid
            group.width = prefWidth
            group.height = prefHeight

            // Calculate grid points coordinates
            val startX = Sizes.paddingHorizontal
            val endX = prefWidth - Sizes.paddingHorizontal - Sizes.iconSize
            val deltaX = (endX - startX) / (maxCol - 1)

            val startY = prefHeight - Sizes.paddingVertical - Sizes.iconSize
            val endY = Sizes.paddingVertical
            val deltaY = (startY - endY) / (maxRow - 1)

            val coords = Array(maxRow + 1) { Array(maxCol + 1) { Pair(0f, 0f) } }

            var row = 1
            var col: Int

            var posX: Float
            var posY = startY

            while (row <= maxRow) {
                col = 1
                posX = startX
                while (col <= maxCol) {
                    coords[row][col] = Pair(posX, posY)

                    col += 1
                    posX += deltaX
                }

                row += 1
                posY -= deltaY
            }

            // Create policy buttons at calculated coordinates
            for (policy in branch.policies) {
                if (policy.policyBranchType == PolicyBranchType.BranchComplete)
                    continue

                val button = getPolicyButton(policy)
                group.addActor(button)

                val policyX = coords[policy.row][policy.column].first
                val policyY = coords[policy.row][policy.column].second

                button.x = policyX
                button.y = policyY

                policyNameToButton[policy.name] = button
            }

            // Draw connecting lines
            drawLines(branch)

            groupCell = add(group).minWidth(prefWidth).expandY().top()
            row()

            // Setup header clicks
            header.onClick(::toggle)

            // Ensure dimensions are calculated
            pack()
        }

        fun toggle() {
            val newActor = if (groupCell.actor == group) labelTable else group
            val rotate = if (groupCell.actor == group) -90f else 90f

            if (groupCell.actor == group)
                topBtnCell.clearActor()
            else
                topBtnCell.setActor(topBtn)

            groupCell.clearActor()
            groupCell.setActor(newActor)

            //todo resolve kludge by making BranchHeader a proper class
            ((header.cells[0].actor as Table).cells[0] as Cell<Actor>)
                .clearActor()
                .setActor(
                    ImageGetter.getImage("OtherIcons/BackArrow").apply { rotation = rotate }.toGroup(10f)
                )
        }
    }


    private fun drawLines(branch: PolicyBranch) {

        for (policy in branch.policies) {

            if (policy.policyBranchType == PolicyBranchType.BranchComplete)
                continue

            if (policy.requires == null)
                continue

            val policyButton = policyNameToButton[policy.name]
            val group = policyButton!!.parent

            for (prereqName in policy.requires!!) {

                if (prereqName == branch.name)
                    continue

                val prereqButton = policyNameToButton[prereqName]
                if (prereqButton != null) {
                    drawLine(
                        group,
                        // Top center
                        policyButton.x + policyButton.width / 2,
                        policyButton.y + policyButton.height,
                        // Bottom center
                        prereqButton.x + prereqButton.width / 2,
                        prereqButton.y
                    )
                }
            }

        }

    }

    private fun drawLine(group: Group, policyX: Float, policyY: Float, prereqX: Float, prereqY:Float) {

        val lineColor = Color.WHITE.cpy()
        val lineSize = 2f

        if (policyX != prereqX) {

            val r = 3f

            val deltaX = policyX - prereqX     // can be > 0 or < 0
            val deltaY = prereqY - policyY     // always > 0

            val bendingY = Sizes.paddingBetweenVer / 2

            // Top line
            val line = ImageGetter.getWhiteDot().apply {
                width = lineSize
                height = deltaY - bendingY - r
                x = prereqX - width / 2
                y = prereqY - height
            }
            // Bottom line
            val line1 = ImageGetter.getWhiteDot().apply {
                width = lineSize
                height = bendingY - r
                x = policyX - width / 2
                y = policyY
            }
            // Middle line
            val line2 = ImageGetter.getWhiteDot().apply {
                width = abs(deltaX) - 2*r
                height = lineSize
                x = policyX + (if (deltaX > 0f) -width - r else r)
                y = policyY + bendingY - lineSize/2
            }

            val line3: Image?  // Top -> Middle
            val line4: Image?  // Bottom -> Middle

            if (deltaX < 0) {
                line3 = ImageGetter.getLine(line2.x + line2.width - lineSize/2, line2.y + lineSize/2,
                    line.x + lineSize/2, line.y + lineSize/2, lineSize)
                line4 = ImageGetter.getLine(line2.x, line2.y + lineSize/2,
                    line1.x + lineSize/2, line1.y + line1.height, lineSize)
            } else {
                line3 = ImageGetter.getLine(line2.x, line2.y + line2.height/2,
                    line.x + lineSize/2, line.y, lineSize)
                line4 = ImageGetter.getLine(line2.x + line2.width - lineSize/2, line2.y + lineSize/2,
                    line1.x + lineSize/2, line1.y + line1.height - lineSize/2, lineSize)
            }

            line.color = lineColor
            line1.color = lineColor
            line2.color = lineColor
            line3.color = lineColor
            line4.color = lineColor

            group.addActor(line)
            group.addActor(line1)
            group.addActor(line2)
            group.addActor(line3)
            group.addActor(line4)
        } else {

            val line = ImageGetter.getWhiteDot().apply {
                width = lineSize
                height = prereqY - policyY
                x = policyX - width / 2
                y = policyY
            }
            line.color = lineColor
            group.addActor(line)
        }

    }

    private fun getBranchHeader(branch: PolicyBranch): Table {
        val header = BorderedTable(path = "PolicyScreen/PolicyBranchHeader")
        header.bgColor = PolicyColors.BranchHeaderBG.color
        header.borderSize = 5f
        header.pad(10f)

        val table = Table()

        val iconPath = "PolicyBranchIcons/" + branch.name
        val icon = if (ImageGetter.imageExists(iconPath)) ImageGetter.getImage(iconPath).apply {
            setOrigin(Align.center)
            setOrigin(25f, 25f)
            align = Align.center
        }.toGroup(15f) else null
        val expandIcon = ImageGetter.getImage("OtherIcons/BackArrow").apply { rotation = 90f }.toGroup(10f)
        table.add(expandIcon).minWidth(15f).expandX().left()
        table.add(
            branch.name.tr(hideIcons = true).uppercase().toLabel(fontSize = 14, alignment = Align.center)
        ).center()
        table.add(icon).expandX().left().padLeft(5f)

        header.touchable = Touchable.enabled

        header.add(table).minWidth(150f).growX()
        header.pack()

        return header
    }

    private fun getTopButton(branch: PolicyBranch): Table {

        val text: String
        val isPickable = branch.isPickable(viewingCiv, canChangeState)
        var isAdoptedBranch = false
        var percentage = 0f

        val lockIcon = ImageGetter.getImage("OtherIcons/LockSmall")
            .apply { color = Color.WHITE }.toGroup(15f)


        if (viewingCiv.policies.isAdopted(branch.name)) {
            val amountToDo = branch.policies.count()-1
            val amountDone =
                if (viewingCiv.policies.isAdopted(branch.policies.last().name))
                    amountToDo
                else
                    branch.policies.count { viewingCiv.policies.isAdopted(it.name) }
            percentage = amountDone / amountToDo.toFloat()
            text = "{Completed} ($amountDone/$amountToDo)"
            lockIcon.isVisible = false
            isAdoptedBranch = true
        } else if (viewingCiv.gameInfo.ruleset.eras[branch.era]!!.eraNumber > viewingCiv.getEraNumber()) {
            text = branch.era
        } else {
            text = "Adopt"
        }

        val label = text.toLabel(fontSize = 14)
        label.setAlignment(Align.center)

        label.color = when {
            isAdoptedBranch -> PolicyColors.BranchLabelAdopted
            isPickable -> PolicyColors.BranchLabelPickable
            else -> PolicyColors.BranchLabelNotPickable
        }.color
        lockIcon.isVisible = !isPickable && !isAdoptedBranch

        val table = object : BorderedTable(
            path="PolicyScreen/PolicyBranchAdoptButton",
            defaultBgShape = skinStrings.roundedEdgeRectangleSmallShape,
            defaultBgBorder = skinStrings.roundedEdgeRectangleSmallShape) {

            var progress: Image? = null

            init {
                if (isAdoptedBranch && percentage > 0) {
                    progress = Image(
                        skinStrings.getUiBackground("",
                            skinStrings.roundedEdgeRectangleSmallShape,
                            tintColor = PolicyColors.BranchBGCompleted.color
                        )
                    )
                    progress!!.setSize(this.width * percentage, this.height)
                    this.addActor(progress)
                    progress!!.toBack()
                }
            }

            override fun sizeChanged() {
                super.sizeChanged()
                progress?.setSize(this.width * percentage, this.height)
            }

        }
        table.bgColor = when {
            isPickable -> PolicyColors.ButtonBGPickable
            else -> PolicyColors.ButtonBGNotPickable
        }.color
        table.borderSize = 3f

        table.add(label).minHeight(30f).minWidth(150f).growX()
        table.addActor(lockIcon)
        table.pack()
        lockIcon.setPosition(table.width, table.height / 2 - lockIcon.height/2)

        table.onClick {
            if (branch.isPickable(viewingCiv, canChangeState))
                ConfirmPopup(
                    this,
                    "Are you sure you want to adopt [${branch.name}]?",
                    "Adopt", true, action = {
                        viewingCiv.policies.adopt(branch, false)
                        game.replaceCurrentScreen(recreate())
                    }
                ).open(force = true)
        }

        return table
    }

    private fun getPolicyButton(policy: Policy): PolicyButton {
        val button = PolicyButton(viewingCiv, canChangeState, policy, size = Sizes.iconSize)
        button.onClick { pickPolicy(button = button) }
        if (policy.isPickable(viewingCiv, canChangeState))
            button.onDoubleClick(UncivSound.Policy) { confirmAction() }
        return button
    }

    private fun confirmAction() {
        val policy = selectedPolicyButton!!.policy

        // Evil people clicking on buttons too fast to confuse the screen - #4977
        if (!policy.isPickable(viewingCiv, canChangeState)) return

        viewingCiv.policies.adopt(policy)

        // If we've moved to another screen in the meantime (great person pick, victory screen) ignore this
        // update policies
        if (game.screen !is PolicyPickerScreen) game.popScreen()
        else game.replaceCurrentScreen(recreate())
    }

    override fun recreate(): BaseScreen {
        val newScreen = PolicyPickerScreen(viewingCiv, canChangeState, selectedPolicyButton?.policy?.name)
        newScreen.scrollPane.scrollPercentX = scrollPane.scrollPercentX
        newScreen.scrollPane.scrollPercentY = scrollPane.scrollPercentY
        newScreen.scrollPane.updateVisualScroll()
        return newScreen
    }
}
