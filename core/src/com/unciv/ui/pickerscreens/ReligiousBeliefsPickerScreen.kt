package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
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
    private val beliefsToChoose: Counter<BeliefType>,
    private val pickIconAndName: Boolean
): PickerScreen(disableScroll = true) {

    // Roughly follows the layout of the original (although I am not very good at UI designing, so please improve this)
    private val topReligionIcons = Table() // Top of the layout, contains icons for religions
    private val leftChosenBeliefs = Table() // Left middle part, contains buttons to select the types of beliefs to choose
    private val rightBeliefsToChoose = Table() // Right middle part, contains the beliefs to choose

    private val middlePanes = Table()

    private var previouslySelectedIcon: Button? = null
    private var displayName: String? = null
    private var religionName: String? = null

    private val chosenBeliefs: Array<Belief?> = Array(beliefsToChoose.values.sum()) { null }

    init {
        closeButton.isVisible = true
        setDefaultCloseAction()

        if (pickIconAndName) setupChoosableReligionIcons()
        else setupVisibleReligionIcons()

        updateLeftTable()

        middlePanes.add(ScrollPane(leftChosenBeliefs))
        middlePanes.addSeparatorVertical()
        middlePanes.add(ScrollPane(rightBeliefsToChoose))

        topTable.add(topReligionIcons).row()
        topTable.addSeparator()
        topTable.add(middlePanes)

        if (pickIconAndName) rightSideButton.label = "Choose a religion".toLabel()
        else rightSideButton.label = "Enhance [${choosingCiv.religionManager.religion!!.getReligionDisplayName()}]".toLabel()
        rightSideButton.onClick(UncivSound.Choir) {
            choosingCiv.religionManager.chooseBeliefs(displayName, religionName, chosenBeliefs.map { it!! })
            UncivGame.Current.setWorldScreen()
        }
    }

    private fun checkAndEnableRightSideButton() {
        if (pickIconAndName && (religionName == null || displayName == null)) return
        if (chosenBeliefs.any { it == null }) return
        rightSideButton.enable()
    }

    private fun setupChoosableReligionIcons() {
        topReligionIcons.clear()

        // This should later be replaced with a user-modifiable text field, but not in this PR
        // Note that this would require replacing 'religion.name' with 'religion.iconName' at many spots
        val descriptionLabel = "Choose an Icon and name for your Religion".toLabel()

        fun changeDisplayedReligionName(newReligionName: String) {
            displayName = newReligionName
            rightSideButton.label = "Found [$newReligionName]".toLabel()
            descriptionLabel.setText(newReligionName)
        }

        val changeReligionNameButton = Button(
            ImageGetter.getImage("OtherIcons/Pencil").apply { this.color = Color.BLACK }.surroundWithCircle(30f),
            skin
        )

        val iconsTable = Table()
        iconsTable.align(Align.center)
        for (religionName in gameInfo.ruleSet.religions) {
            val button = Button(
                ImageGetter.getCircledReligionIcon(religionName, 60f),
                skin
            )
            button.onClick {
                if (previouslySelectedIcon != null) {
                    previouslySelectedIcon!!.enable()
                }
                previouslySelectedIcon = button
                button.disable()

                changeDisplayedReligionName(religionName)
                this.religionName = religionName
                changeReligionNameButton.enable()

                checkAndEnableRightSideButton()
            }
            if (religionName == this.religionName || gameInfo.religions.keys.any { it == religionName }) button.disable()
            iconsTable.add(button).pad(5f)
        }
        iconsTable.row()
        topReligionIcons.add(iconsTable).pad(5f).row()
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
        val descriptionLabel = choosingCiv.religionManager.religion!!.getReligionDisplayName().toLabel()

        val iconsTable = Table()

        for (religionName in gameInfo.ruleSet.religions) {
            val button = Button(
                ImageGetter.getCircledReligionIcon(religionName, 60f),
                skin
            )
            button.disable()
            iconsTable.add(button).pad(5f)
        }
        topReligionIcons.add(iconsTable).padBottom(10f).row()
        topReligionIcons.add(descriptionLabel).center().padBottom(5f)
    }

    private fun updateLeftTable() {
        leftChosenBeliefs.clear()
        val currentReligion = choosingCiv.religionManager.religion ?: Religion("None", gameInfo, choosingCiv.civName)

        for (belief in currentReligion.getAllBeliefsOrdered()) {
            val beliefButton = convertBeliefToButton(belief)
            leftChosenBeliefs.add(beliefButton).pad(10f).row()
            beliefButton.disable()
        }

        for (newBelief in chosenBeliefs.withIndex()) {
            addChoosableBeliefButton(newBelief, getBeliefTypeFromIndex(newBelief.index))
        }

        equalizeAllButtons(leftChosenBeliefs)
    }

    private fun loadRightTable(beliefType: BeliefType, leftButtonIndex: Int) {
        rightBeliefsToChoose.clear()
        val availableBeliefs = gameInfo.ruleSet.beliefs.values
            .filter {
                (it.type == beliefType || beliefType == BeliefType.Any)
                && gameInfo.religions.values.none {
                    religion -> religion.hasBelief(it.name)
                }
                && (it !in chosenBeliefs)
            }
        for (belief in availableBeliefs) {
            val beliefButton = convertBeliefToButton(belief)
            beliefButton.onClick {
                chosenBeliefs[leftButtonIndex] = belief
                updateLeftTable()
                checkAndEnableRightSideButton()
            }
            rightBeliefsToChoose.add(beliefButton).left().pad(10f).row()
        }
        equalizeAllButtons(rightBeliefsToChoose)
    }

    private fun equalizeAllButtons(table: Table) {
        val minWidth = table.cells
            .filter { it.actor is Button }
            .maxOfOrNull { it.actor.width }
            ?: return

        for (button in table.cells) {
            if (button.actor is Button)
                button.minWidth(minWidth)
        }
    }

    private fun addChoosableBeliefButton(belief: IndexedValue<Belief?>, beliefType: BeliefType) {
        val newBeliefButton =
            if (belief.value == null) emptyBeliefButton(beliefType)
            else convertBeliefToButton(belief.value!!)

        leftChosenBeliefs.add(newBeliefButton).pad(10f).row()
        newBeliefButton.onClick {
            loadRightTable(beliefType, belief.index)
        }
    }

    private fun convertBeliefToButton(belief: Belief): Button {
        val contentsTable = Table()
        contentsTable.add(belief.type.name.toLabel(fontColor = Color.valueOf(belief.type.color))).row()
        contentsTable.add(belief.name.toLabel(fontSize = 24)).row()
        contentsTable.add(belief.uniques.joinToString("\n") { it.tr() }.toLabel())
        return Button(contentsTable, skin)
    }

    private fun emptyBeliefButton(beliefType: BeliefType): Button {
        val contentsTable = Table()
        if (beliefType != BeliefType.Any)
            contentsTable.add("Choose a [${beliefType.name}] belief!".toLabel())
        else
            contentsTable.add("Choose any belief!".toLabel())
        return Button(contentsTable, skin)
    }

    private fun getBeliefTypeFromIndex(index: Int): BeliefType {
        return when {
            index < beliefsToChoose.filter { it.key <= BeliefType.Pantheon }.values.sum() -> BeliefType.Pantheon
            index < beliefsToChoose.filter { it.key <= BeliefType.Founder }.values.sum() -> BeliefType.Founder
            index < beliefsToChoose.filter { it.key <= BeliefType.Follower }.values.sum() -> BeliefType.Follower
            index < beliefsToChoose.filter { it.key <= BeliefType.Enhancer }.values.sum() -> BeliefType.Enhancer
            else -> BeliefType.Any
        }
    }
}
