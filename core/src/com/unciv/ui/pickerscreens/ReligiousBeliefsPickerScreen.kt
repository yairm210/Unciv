package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Religion
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class ChooseReligionBeliefsPickerScreen (
    private val choosingCiv: CivilizationInfo,
    private val gameInfo: GameInfo,
    private val beliefsContainer: BeliefContainer,
    private val pickIcon: Boolean
): PickerScreen(disableScroll = true) {

    // Roughly follows the layout of the original (although I am not very good at UI designing, so please improve this)
    private val topReligionIcons = Table() // Top of the layout, contains icons for religions
    private val leftChosenBeliefs = Table() // Left middle part, contains buttons to select the types of beliefs to choose
    private val rightBeliefsToChoose = Table() // Right middle part, contains the beliefs to choose
    
    private val middlePanes = Table()
 
    private var previouslySelectedIcon: Button? = null
    private var iconName: String? = null
    private var religionName: String? = null
    
    init {
        closeButton.isVisible = true
        setDefaultCloseAction()
        
        if (pickIcon) setupChoosableReligionIcons()
        else setupVisibleReligionIcons()
        
        updateLeftTable()
        
        middlePanes.add(ScrollPane(leftChosenBeliefs))
        middlePanes.addSeparatorVertical()
        middlePanes.add(ScrollPane(rightBeliefsToChoose))
        
        topTable.add(topReligionIcons).row()
        topTable.addSeparator()
        topTable.add(middlePanes)
        
        rightSideButton.label = "Choose a religion".toLabel()
        rightSideButton.onClick(UncivSound.Choir) {
            choosingCiv.religionManager.chooseBeliefs(iconName, religionName, beliefsContainer.chosenBeliefs.map { it!! })            
            UncivGame.Current.setWorldScreen()
        }
    }

    private fun checkAndEnableRightSideButton() {
        if (pickIcon && (religionName == null || iconName == null)) return
        if (beliefsContainer.chosenBeliefs.any { it == null }) return
        rightSideButton.enable()
    }

    private fun setupChoosableReligionIcons() {
        topReligionIcons.clear()
        
        // This should later be replaced with a user-modifiable text field, but not in this PR
        // Note that this would require replacing 'religion.name' with 'religion.iconName' at many spots
        val descriptionLabel = "Choose an Icon and name for your Religion".toLabel() 
        
        val iconsTable = Table()
        iconsTable.align(Align.center)
        for (religionName in gameInfo.ruleSet.religions) {
            val button = Button(
                ImageGetter.getCircledReligionIcon(religionName, 60f), 
                skin
            )
            val translatedReligionName = religionName.tr()
            button.onClick {
                if (previouslySelectedIcon != null) {
                    previouslySelectedIcon!!.enable()
                }
                iconName = religionName
                this.religionName = religionName
                previouslySelectedIcon = button
                button.disable()
                descriptionLabel.setText(translatedReligionName)
                rightSideButton.label = "Found [$translatedReligionName]".toLabel()
                checkAndEnableRightSideButton()
            }
            if (religionName == this.religionName || gameInfo.religions.keys.any { it == religionName }) button.disable()
            iconsTable.add(button).pad(5f)
        }
        iconsTable.row()
        topReligionIcons.add(iconsTable).padBottom(10f).row()
        topReligionIcons.add(descriptionLabel).center().padBottom(5f)
    }

    private fun setupVisibleReligionIcons() {
        topReligionIcons.clear()
        val descriptionLabel = choosingCiv.religionManager.religion!!.name.toLabel()
        
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
        
        for (newBelief in beliefsContainer.chosenBeliefs.withIndex()) {
            addChoosableBeliefButton(newBelief, beliefsContainer.getBeliefTypeFromIndex(newBelief.index))
        }
    }
    
    private fun loadRightTable(beliefType: BeliefType, leftButtonIndex: Int) {
        rightBeliefsToChoose.clear()
        val availableBeliefs = gameInfo.ruleSet.beliefs.values
            .filter { 
                it.type == beliefType
                && gameInfo.religions.values.none {
                    religion -> religion.hasBelief(it.name)
                }
                && (it !in beliefsContainer.chosenBeliefs)
            }
        for (belief in availableBeliefs) {
            val beliefButton = convertBeliefToButton(belief)
            beliefButton.onClick {
                beliefsContainer.chosenBeliefs[leftButtonIndex] = belief
                updateLeftTable()
                checkAndEnableRightSideButton()
            }
            rightBeliefsToChoose.add(beliefButton).pad(10f).row()
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
        contentsTable.add(belief.type.name.toLabel()).row()
        contentsTable.add(belief.name.toLabel(fontSize = 24)).row()
        contentsTable.add(belief.uniques.joinToString().toLabel())
        return Button(contentsTable, skin)
    }
    
    private fun emptyBeliefButton(beliefType: BeliefType): Button {
        val contentsTable = Table()
        contentsTable.add("Choose a [${beliefType.name}] belief!".toLabel())
        return Button(contentsTable, skin)
    }
}


data class BeliefContainer(val pantheonBeliefCount: Int = 0, val founderBeliefCount: Int = 0, val followerBeliefCount: Int = 0, val enhancerBeliefCount: Int = 0) {
    
    val chosenBeliefs: MutableList<Belief?> = MutableList(pantheonBeliefCount + founderBeliefCount + followerBeliefCount + enhancerBeliefCount) { null }
    
    fun getBeliefTypeFromIndex(index: Int): BeliefType {
        return when {
            index < pantheonBeliefCount -> BeliefType.Pantheon
            index < pantheonBeliefCount + founderBeliefCount -> BeliefType.Founder
            index < pantheonBeliefCount + founderBeliefCount + followerBeliefCount -> BeliefType.Follower
            else -> BeliefType.Enhancer
        }
    }
}