package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Counter
import com.unciv.models.Religion
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class ReligiousBeliefsPickerScreen (
    private val choosingCiv: CivilizationInfo,
    private val gameInfo: GameInfo,
    newBeliefsToChoose: Counter<BeliefType>,
    private val pickIconAndName: Boolean
): PickerScreen(disableScroll = true) {
    // Roughly follows the layout of the original (although I am not very good at UI designing, so please improve this)

    private val topReligionIcons = Table() // Top of the layout, contains icons for religions
    private val leftChosenBeliefs = Table() // Left middle part, contains buttons to select the types of beliefs to choose
    private val leftScrollPane = AutoScrollPane(leftChosenBeliefs)
    private val rightBeliefsToChoose = Table() // Right middle part, contains the beliefs to choose
    private val rightScrollPane = AutoScrollPane(rightBeliefsToChoose)

    private val middlePanes = Table()

    private var previouslySelectedIcon: Button? = null
    private var displayName: String? = null
    private var religionName: String? = null

    // One entry per new Belief to choose - the left side will offer these below the choices from earlier in the game
    class BeliefToChoose(val type: BeliefType, var belief: Belief? = null)
    private val beliefsToChoose: Array<BeliefToChoose> =
        newBeliefsToChoose.flatMap { entry -> (0 until entry.value).map { BeliefToChoose(entry.key) } }.toTypedArray()

    private var leftSelectedButton: Button? = null
    private var leftSelectedIndex = -1
    private var rightSelectedButton: Button? = null

    init {
        leftChosenBeliefs.defaults().right().pad(10f).fillX()
        rightBeliefsToChoose.defaults().left().pad(10f).fillX()

        closeButton.isVisible = true
        setDefaultCloseAction()

        if (pickIconAndName) setupChoosableReligionIcons()
        else setupVisibleReligionIcons()

        updateLeftTable()

        middlePanes.add(leftScrollPane)
        middlePanes.addSeparatorVertical()
        middlePanes.add(rightScrollPane)

        topTable.add(topReligionIcons).minHeight(topReligionIcons.prefHeight).row()
        topTable.addSeparator()
        topTable.add(middlePanes)

        if (pickIconAndName) rightSideButton.label = "Choose a Religion".toLabel()
        else rightSideButton.label = "Enhance [${choosingCiv.religionManager.religion!!.getReligionDisplayName()}]".toLabel()
        rightSideButton.onClick(UncivSound.Choir) {
            choosingCiv.religionManager.chooseBeliefs(displayName, religionName, beliefsToChoose.map { it.belief!! })
            UncivGame.Current.setWorldScreen()
            dispose()
        }
    }

    private fun checkAndEnableRightSideButton() {
        if (pickIconAndName && (religionName == null || displayName == null)) return
        if (beliefsToChoose.any { it.belief == null }) return
        rightSideButton.enable()
    }

    private fun setupChoosableReligionIcons() {
        val descriptionLabel = "Choose an Icon and name for your Religion".toLabel()

        fun changeDisplayedReligionName(newReligionName: String) {
            displayName = newReligionName
            rightSideButton.label = "Found [$newReligionName]".toLabel()
            descriptionLabel.setText(newReligionName.tr())
        }

        val changeReligionNameButton = Button(
            ImageGetter.getImage("OtherIcons/Pencil").apply { this.color = Color.BLACK }.surroundWithCircle(30f),
            skin
        )

        addIconsScroll { button, religionName ->
            button.onClick {
                previouslySelectedIcon?.enable()
                previouslySelectedIcon = button
                button.disable()

                changeDisplayedReligionName(religionName)
                this.religionName = religionName
                changeReligionNameButton.enable()

                checkAndEnableRightSideButton()
            }
        }

        val labelTable = Table()
        labelTable.add(descriptionLabel).pad(5f)
        labelTable.add(changeReligionNameButton).pad(5f).row()
        topReligionIcons.add(labelTable).center().pad(5f).row()

        changeReligionNameButton.onClick {
            AskTextPopup(
                this,
                label = "Choose a name for your religion",
                icon = ImageGetter.getCircledReligionIcon(religionName!!, 80f),
                defaultText = religionName!!,
                validate = { religionName ->
                    religionName != Constants.noReligionName
                    && gameInfo.ruleSet.religions.none { it == religionName }
                    && gameInfo.religions.none { it.value.name == religionName }
                },
                actionOnOk = { changeDisplayedReligionName(it) }
            ).open()
        }
        changeReligionNameButton.disable()
    }

    private fun setupVisibleReligionIcons() {
        topReligionIcons.clear()
        religionName = choosingCiv.religionManager.religion!!.name
        val descriptionLabel = choosingCiv.religionManager.religion!!.getReligionDisplayName().toLabel()
        addIconsScroll { button, _ ->
            button.disable()
        }
        topReligionIcons.add(descriptionLabel).center().padBottom(15f)
    }

    private fun addIconsScroll(buttonSetup: (Button, String)->Unit) {
        var scrollTo = 0f
        val iconsTable = Table()
        iconsTable.align(Align.center)

        for (religionName in gameInfo.ruleSet.religions) {
            if (religionName == this.religionName)
                scrollTo = iconsTable.packIfNeeded().prefWidth
            val button = Button(ImageGetter.getCircledReligionIcon(religionName, 60f), skin)
            buttonSetup(button, religionName)
            if (religionName == this.religionName) button.disable(Color(0x007f00ff))
            else if (gameInfo.religions.keys.any { it == religionName }) button.disable(Color(0x7f0000ff))
            iconsTable.add(button).pad(5f)
        }
        iconsTable.row()

        AutoScrollPane(iconsTable, skin).apply {
            setScrollingDisabled(false, true)
            setupFadeScrollBars(0f, 0f)  // only way to "remove" scrollbar
            setScrollbarsOnTop(true) // don't waste space on scrollbar
            topReligionIcons.add(this).padBottom(10f).row()
            layout()
            scrollX = scrollTo - (width - 70f) / 2  // 70 = button width incl pad
        }
    }

    private fun updateLeftTable() {
        leftChosenBeliefs.clear()
        leftSelectedButton = null
        val currentReligion = choosingCiv.religionManager.religion ?: Religion("None", gameInfo, choosingCiv.civName)

        for (belief in currentReligion.getAllBeliefsOrdered()) {
            val beliefButton = getBeliefButton(belief)
            leftChosenBeliefs.add(beliefButton).row()
            beliefButton.disable(Color.GREEN)
        }

        for ((index, entry) in beliefsToChoose.withIndex()) {
            addChoosableBeliefButton(entry.belief, entry.type, index)
        }

        equalizeAllButtons(leftChosenBeliefs)
    }

    private fun loadRightTable(beliefType: BeliefType, leftButtonIndex: Int) {
        var selectedButtonY = 0f
        var selectedButtonHeight = 0f
        rightBeliefsToChoose.clear()
        rightSelectedButton = null
        val availableBeliefs = gameInfo.ruleSet.beliefs.values
            .filter { (it.type == beliefType || beliefType == BeliefType.Any) }
        for (belief in availableBeliefs) {
            val beliefButton = getBeliefButton(belief)
            beliefButton.onClick {
                rightSelectedButton?.enable()
                rightSelectedButton = beliefButton
                beliefButton.disable()
                beliefsToChoose[leftButtonIndex].belief = belief
                updateLeftTable()
                checkAndEnableRightSideButton()
            }
            when {
                beliefsToChoose[leftButtonIndex].belief == belief -> {
                    selectedButtonY = rightBeliefsToChoose.packIfNeeded().prefHeight
                    selectedButtonHeight = beliefButton.packIfNeeded().prefHeight + 20f
                    rightSelectedButton = beliefButton
                    beliefButton.disable()
                }
                beliefsToChoose.any { it.belief == belief } ||
                        choosingCiv.religionManager.religion!!.hasBelief(belief.name) -> {
                    // The Belief button should be disabled because you already have it selected
                    beliefButton.disable(Color(0x007f00ff))
                }
                gameInfo.religions.values.any { it.hasBelief(belief.name) } -> {
                    // The Belief is not available because someone already has it
                    beliefButton.disable(Color(0x7f0000ff))
                }
            }
            rightBeliefsToChoose.add(beliefButton).row()
        }
        equalizeAllButtons(rightBeliefsToChoose)

        if (rightSelectedButton == null) return
        rightScrollPane.layout()
        rightScrollPane.scrollY = selectedButtonY - (rightScrollPane.height - selectedButtonHeight) / 2
    }

    private fun equalizeAllButtons(table: Table) {
        val minWidth = table.cells.maxOfOrNull { it.prefWidth }
            ?: return

        for (buttonCell in table.cells) {
            if (buttonCell.actor is Button)
                buttonCell.minWidth(minWidth)
        }
    }

    private fun addChoosableBeliefButton(belief: Belief?, beliefType: BeliefType, index: Int) {
        val newBeliefButton = getBeliefButton(belief, beliefType)

        if (index == leftSelectedIndex) {
            newBeliefButton.disable()
            leftSelectedButton = newBeliefButton
            leftScrollPane.scrollY = leftChosenBeliefs.packIfNeeded().prefHeight -
                    (leftScrollPane.height - (newBeliefButton.prefHeight + 20f)) / 2
            leftScrollPane.updateVisualScroll()
        }
        leftChosenBeliefs.add(newBeliefButton).row()

        newBeliefButton.onClick {
            leftSelectedButton?.enable()
            leftSelectedButton = newBeliefButton
            leftSelectedIndex = index
            newBeliefButton.disable()
            loadRightTable(beliefType, index)
        }
    }

    private fun getBeliefButton(belief: Belief? = null, beliefType: BeliefType? = null): Button {
        val labelWidth = stage.width * 0.5f - 52f  // 32f empirically measured padding inside button, 20f outside padding
        return Button(skin).apply {
            when {
                belief != null -> {
                    add(belief.type.name.toLabel(fontColor = Color.valueOf(belief.type.color))).row()
                    val nameLabel = WrappableLabel(belief.name, labelWidth, fontSize = Constants.headingFontSize)
                    add(nameLabel.apply { wrap = true }).row()
                    val effectLabel = WrappableLabel(belief.uniques.joinToString("\n") { it.tr() }, labelWidth)
                    add(effectLabel.apply { wrap = true })
                }
                beliefType == BeliefType.Any ->
                    add("Choose any belief!".toLabel())
                beliefType != null ->
                    add("Choose a [${beliefType.name}] belief!".toLabel())
                else -> throw(IllegalArgumentException("getBeliefButton must have one non-null parameter"))
            }
        }
    }

    private fun Button.disable(color: Color) {
        touchable = Touchable.disabled
        this.color = color
    }
}
