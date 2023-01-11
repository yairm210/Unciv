package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.TutorialTrigger
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.BorderedTable
import com.unciv.ui.utils.RecreateOnResize
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.pad
import com.unciv.ui.utils.extensions.toGroup
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.worldscreen.WorldScreen
import java.lang.Integer.max
import kotlin.math.abs
import kotlin.math.min

object PolicyColors {

    val policyPickable = colorFromRGB(47,67,92).darken(0.3f)
    val policyNotPickable =  colorFromRGB(20, 20, 20)
    val policySelected = colorFromRGB(37,87,82)

    val branchCompleted = colorFromRGB(255, 205, 0)
    val branchNotAdopted = colorFromRGB(10,90,130).darken(0.5f)
    val branchAdopted = colorFromRGB(100, 90, 10).darken(0.5f)
}

fun Policy.isAdopted() : Boolean {
    val worldScreen = UncivGame.Current.worldScreen ?: return false
    val viewingCiv = worldScreen.viewingCiv
    return viewingCiv.policies.isAdopted(this.name)
}

fun Policy.isPickable() : Boolean {
    val worldScreen = UncivGame.Current.worldScreen ?: return false
    val viewingCiv = worldScreen.viewingCiv
    if (!worldScreen.isPlayersTurn
            || worldScreen.viewingCiv.isSpectator() // viewingCiv var points to selectedCiv in case of spectator
            || viewingCiv.isDefeated()
            || viewingCiv.policies.isAdopted(this.name)
            || this.policyBranchType == PolicyBranchType.BranchComplete
            || !viewingCiv.policies.isAdoptable(this)
            || !viewingCiv.policies.canAdoptPolicy()
    )
        return false
    return true
}

class PolicyButton(val policy: Policy, size: Float = 30f) : BorderedTable(
    path = "PolicyScreen/PolicyButton",
    defaultBorder = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
    defaultInner = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
    borderSize = 2f
) {

    val icon = ImageGetter.getImage("PolicyIcons/" + policy.name)
    val highlight = ImageGetter.getImage("UnitFlagIcons/UnitFlagSelection")

    var isSelected = false
        set(value) {
            field = value
            updateState()
        }

    init {

        icon.setSize(size*0.7f, size*0.7f)
        highlight.setSize(size*1.38f, size*1.38f)

        addActor(icon)
        addActor(highlight)

        updateState()
        pack()
        width = size
        height = size

        icon.toFront()
        icon.center(this)
        highlight.toBack()
        highlight.isVisible = false
        highlight.center(this)
    }

    fun onClick(function: () -> Unit): PolicyButton {
        (this as Actor).onClick(function = {
            function()
            updateState()
        })
        return this
    }

    fun updateState() {

        val worldScreen = UncivGame.Current.worldScreen ?: return
        val viewingCiv = worldScreen.viewingCiv

        val isPickable = policy.isPickable()
        val isAdopted = viewingCiv.policies.isAdopted(policy.name)


        highlight.isVisible = isSelected

        when {

            isSelected && isPickable -> {
                setBackgroundColor(PolicyColors.policySelected)
            }

            isPickable -> {
                setBackgroundColor(PolicyColors.policyPickable)
            }

            isAdopted -> {
                icon.color = Color.GOLD.cpy()
                setBackgroundColor(colorFromRGB(10,90,100).darken(0.8f))
            }

            else -> {
                icon.color.a = 0.2f
                setBackgroundColor(PolicyColors.policyNotPickable)
            }

        }

    }

}


class PolicyPickerScreen(val worldScreen: WorldScreen, civInfo: CivilizationInfo = worldScreen.viewingCiv)
    : PickerScreen(), RecreateOnResize {

    object Sizes {
        val paddingVertical = 10f
        val paddingHorizontal = 20f
        val paddingBetweenHor = 10f
        val paddingBetweenVer = 20f
        val iconSize = 50f
    }

    internal val viewingCiv: CivilizationInfo = civInfo

    private var policyNameToButton = HashMap<String, BorderedTable>()
    private var selectedPolicyButton: PolicyButton? = null

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
            val policy = selectedPolicyButton!!.policy

            // Evil people clicking on buttons too fast to confuse the screen - #4977
            if (!policy.isPickable()) return@onClick

            viewingCiv.policies.adopt(policy)

            // If we've moved to another screen in the meantime (great person pick, victory screen) ignore this
            if (game.screen !is PolicyPickerScreen) {
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

        // estimate how many branch boxes fit using average size (including pad)
        // TODO If we'd want to use scene2d correctly, this is supposed to happen inside an overridden layout() method
        val numBranchesY = scrollPane.height / 305f
            // Landscape - arrange in as few rows as looks nice
        if (numBranchesY > 1.5f) {
            val numRows = if (numBranchesY < 2.9f) 2 else (numBranchesY + 0.1f).toInt()
            rowChangeCount = (branches.size + numRows - 1) / numRows
        } else {
            rowChangeCount = branches.size
        }


        // Actually create and distribute the policy branches
        var wrapper = Table()
        val size = ((branches.size / rowChangeCount) + if (branches.size % rowChangeCount == 0) 0 else 1)*rowChangeCount
        for (i in 0 until size) {

            val change = (i % rowChangeCount == 0)
            val rightToLeft = ((i / rowChangeCount) % 2)
            val r = (i % rowChangeCount) + (i / rowChangeCount)*(rowChangeCount-rightToLeft) + rightToLeft*(rowChangeCount-2*(i % rowChangeCount))

            if (i > 0 && change) {
                topTable.add(wrapper).pad(5f,10f)
                topTable.addSeparator().pad(0f, 10f)
                wrapper = Table()
            }

            if (r >= branches.size) {
                wrapper.add().expand()
            } else {
                val branch = branches.values.elementAt(r)
                val branchGroup = getBranchGroup(branch)
                wrapper.add(branchGroup).growY().growX()
            }
        }
        topTable.add(wrapper).pad(5f,10f)

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
    }

    private fun pickPolicy(button: PolicyButton) {

        val policy = button.policy

        rightSideButton.isVisible = !viewingCiv.policies.isAdopted(policy.name)
        if (!policy.isPickable()) {
            rightSideButton.disable()
        } else {
            rightSideButton.enable()
        }
        if (viewingCiv.gameInfo.gameParameters.godMode && selectedPolicyButton?.policy == policy
                && viewingCiv.policies.isAdoptable(policy)) {
            viewingCiv.policies.adopt(policy)
            game.replaceCurrentScreen(PolicyPickerScreen(worldScreen))
        }

        selectedPolicyButton?.isSelected = false
        selectedPolicyButton = button
        selectedPolicyButton?.isSelected = true

        descriptionLabel.setText(policy.getDescription())
    }

    /**
     * Create a Widget for a complete policy branch including Starter and "complete" buttons.
     * @param branch the policy branch to display
     * @return a [Table], with outer padding _zero_
     */
    private fun getBranchGroup(branch: PolicyBranch): Group {

        // Calculate preferred size
        val maxCol = max(5, branch.policies.maxOf { it.column })
        val maxRow = branch.policies.maxOf { it.row }

        val prefWidth = Sizes.paddingHorizontal*2 + Sizes.iconSize*maxCol - (Sizes.iconSize-Sizes.paddingBetweenHor)*(maxCol-1)/2
        val prefHeight = Sizes.paddingVertical*2 + Sizes.iconSize*maxRow + Sizes.paddingBetweenVer*(maxRow - 1)

        // Main table
        val colorBg = if (branch.isAdopted()) PolicyColors.branchAdopted else PolicyColors.branchNotAdopted
        val branchGroup = BorderedTable(
            path="PolicyScree/PolicyBranchBackground",
            innerColor = colorBg)

        // Header
        val header = getBranchHeader(branch)
        branchGroup.add(header).growX().row()

        // Description
        val onAdoption = branch.getDescription()
        val onCompletion = branch.policies.last().getDescription()
        var text = ""
        if (viewingCiv.gameInfo.ruleSet.eras[branch.era]!!.eraNumber > viewingCiv.getEraNumber())
            text += "{Unlocked at} {${branch.era}}" + "\n\n"
        text += "{On adoption}:" + "\n\n" + onAdoption + "\n\n" +
                "{On completion}:" + "\n\n" + onCompletion

        val labelTable = Table()

        val label = text.toLabel(fontSize = 13)
        label.setFillParent(false)
        label.setAlignment(Align.topLeft)
        label.wrap = true
        labelTable.add(label).pad(7f,20f, 10f, 20f).grow().row()

        val conditionals = LinkedHashMap<UniqueType, ArrayList<String>>()

        branch.uniqueMap[UniqueType.OnlyAvailableWhen.text]?.forEach {
            it.conditionals.forEach {
                if (it.type != null) {
                    if (conditionals[it.type] == null)
                        conditionals[it.type] = ArrayList()
                    conditionals[it.type]!!.add(it.params.toString().tr())
                }
            }
        }

        if (conditionals.isNotEmpty()) {
            var warning = UniqueType.OnlyAvailableWhen.text.tr() + ":\n"
            for ((k, v) in conditionals) {
                warning += "• " + k.text.fillPlaceholders(v.joinToString()).tr() + "\n"
            }
            val warningLabel = warning.toLabel(Color.RED.cpy(), 13)
            warningLabel.setFillParent(false)
            warningLabel.setAlignment(Align.topLeft)
            warningLabel.wrap = true
            labelTable.add(warningLabel).pad(0f, 20f, 17f, 20f).grow()
        }

        // Top button
        val topBtn = getTopButton(branch)
        val topBtnCell = branchGroup.add(topBtn).growX().pad(10f, 10f, 0f, 10f)
        topBtnCell.row()

        // Main grid

        val group = Group()
        group.width = prefWidth
        group.height = prefHeight

        // Calculate grid points coordinates
        val startX = Sizes.paddingHorizontal
        val endX = prefWidth - Sizes.paddingHorizontal - Sizes.iconSize
        val deltaX = (endX - startX)/(maxCol - 1)

        val startY = prefHeight - Sizes.paddingVertical - Sizes.iconSize
        val endY = Sizes.paddingVertical
        val deltaY = (startY - endY)/(maxRow - 1)

        val coords = Array(maxRow+1) { Array(maxCol+1) {Pair(0f,0f)}}

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

            val button = getPolicyButton(policy, size = Sizes.iconSize)
            group.addActor(button)

            val policyX = coords[policy.row][policy.column].first
            val policyY = coords[policy.row][policy.column].second

            button.x = policyX
            button.y = policyY

            policyNameToButton[policy.name] = button
        }

        // Draw connecting lines
        drawLines(branch)

        val groupCell = branchGroup.add(group).minWidth(prefWidth).expandY().top()
        branchGroup.row()

        // Setup header clicks
        header.onClick {

            val newActor = if (groupCell.actor == group) labelTable else group
            val rotate = if (groupCell.actor == group) -90f else 90f

            if (groupCell.actor == group)
                topBtnCell.clearActor()
            else
                topBtnCell.setActor(topBtn)

            groupCell.clearActor()
            groupCell.setActor(newActor)

            ((header.cells[0].actor as Table).cells[0] as Cell<Actor>).clearActor()
            ((header.cells[0].actor as Table).cells[0] as Cell<Actor>).setActor(ImageGetter.
            getImage("OtherIcons/BackArrow").apply { rotation = rotate}.toGroup(10f))
        }

        // Ensure dimensions are calculated
        branchGroup.pack()
        return branchGroup
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
        val header = BorderedTable(
            path="PolicyScreen/PolicyBranchHeader",
            innerColor = colorFromRGB(47,90,92), borderSize = 5f)
        header.pad(5f)

        val table = Table()

        val iconPath = "PolicyBranchIcons/" + branch.name
        val icon = if (ImageGetter.imageExists(iconPath)) ImageGetter.getImage(iconPath).apply {
            setOrigin(Align.center)
            setOrigin(25f, 25f)
            align = Align.center
        }.toGroup(15f) else null
        val expandIcon = ImageGetter.getImage("OtherIcons/BackArrow").apply { rotation = 90f }.toGroup(10f)
        table.add(expandIcon).minWidth(15f).expandX().left()
        table.add(branch.name.tr().uppercase().toLabel(fontSize = 14).apply { setAlignment(Align.center) }).center()
        table.add(icon).expandX().left().padLeft(5f)

        header.add(table).minWidth(150f).growX()
        header.pack()

        return header
    }

    private fun getTopButton(branch: PolicyBranch): Table {

        val policy: Policy
        val text: String

        val lockIcon = ImageGetter.getImage("OtherIcons/LockSmall")
            .apply { color = Color.WHITE.cpy() }.toGroup(15f)
        lockIcon.isVisible = false
        if (viewingCiv.policies.isAdopted(branch.name)) {
            policy = branch.policies.last()
            text = "Completed"
        } else if (viewingCiv.gameInfo.ruleSet.eras[branch.era]!!.eraNumber > viewingCiv.getEraNumber()) {
            policy = branch
            text = branch.era
        } else {
            policy = branch
            text = "Adopt"
        }

        val label = text.toLabel(fontSize = 14)
        label.setAlignment(Align.center)

        val color = when {
            policy.isPickable() -> PolicyColors.policyPickable
            viewingCiv.policies.isAdopted(policy.name) -> {
                label.color = colorFromRGB(150, 70, 40)
                PolicyColors.branchCompleted
            }
            else -> {
                lockIcon.isVisible = true
                label.color.a = 0.5f
                PolicyColors.policyNotPickable}
        }

        val table = BorderedTable(
            path="PolicyScreen/PolicyBranchAdoptButton",
            defaultInner = skinStrings.roundedEdgeRectangleSmallShape,
            defaultBorder = skinStrings.roundedEdgeRectangleSmallShape,
            innerColor = color, borderSize = 2f)

        table.add(label).minHeight(30f).minWidth(150f).growX()
        table.addActor(lockIcon)

        table.onClick(function = {})
        table.onClick {
            if (policy.isPickable())
                ConfirmPopup(
                    this,
                    "Are you sure you want to adopt [${branch.name}]?",
                    "Adopt", true, action = {
                        viewingCiv.policies.adopt(policy, false)

                        val policyScreen = PolicyPickerScreen(worldScreen)
                        policyScreen.scrollPane.scrollPercentX = scrollPane.scrollPercentX
                        policyScreen.scrollPane.scrollPercentY = scrollPane.scrollPercentY
                        policyScreen.scrollPane.updateVisualScroll()
                        game.replaceCurrentScreen(policyScreen)
                    }
                ).open(force = true)
        }
        table.pack()

        lockIcon.setPosition(table.width, table.height / 2 - lockIcon.height/2)

        return table
    }

    private fun getPolicyButton(policy: Policy, size: Float = 30f): BorderedTable {
        val button = PolicyButton(policy, size = size)
        button.onClick { pickPolicy(button = button) }
        return button
    }

    override fun recreate(): BaseScreen = PolicyPickerScreen(worldScreen, viewingCiv)
}
