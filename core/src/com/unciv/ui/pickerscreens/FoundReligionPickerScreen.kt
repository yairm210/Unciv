package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Religion
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Belief
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class FoundReligionPickerScreen (
    private val choosingCiv: CivilizationInfo,
    private val gameInfo: GameInfo,
    followerBeliefsToChoose: Int = 1,
    founderBeliefsToChoose: Int = 1,
): PickerScreen(disableScroll = true) {

    // Roughly follows the layout of the original (although I am not very good at UI designing, so please improve this)
    private val topReligionIcons = Table() // Top of the layout, contains icons for religions
    private val leftChosenBeliefs = Table() // Left middle part, contains buttons to select the types of beliefs to choose
    private val rightBeliefsToChoose = Table() // Right middle part, contains the beliefs to choose
    
    private val middlePanes = Table()
 
    private var previouslySelectedIcon: Button? = null
    private var iconName: String? = null
    private var religionName: String? = null
    private val chosenFollowerBeliefs: MutableList<Belief?> = MutableList(followerBeliefsToChoose) { null }
    private val chosenFounderBeliefs: MutableList<Belief?> = MutableList(founderBeliefsToChoose) { null }

    init {
        closeButton.isVisible = true
        setDefaultCloseAction()
        
        setupReligionIcons()
        
        updateLeftTable()
        
        middlePanes.add(ScrollPane(leftChosenBeliefs))
        middlePanes.addSeparatorVertical()
        middlePanes.add(ScrollPane(rightBeliefsToChoose))
        
        topTable.add(topReligionIcons).row()
        // commented out, as the middle panes will always be empty for now, and this will create a random line otherwise
        topTable.addSeparator()
        topTable.add(middlePanes)
        
        rightSideButton.label = "Choose a religion".toLabel()
        rightSideButton.onClick(UncivSound.Choir) {
            choosingCiv.religionManager.foundReligion(
                iconName!!, religionName!!, "" /**chosenFollowerBeliefs.map {it!!.name} */, chosenFollowerBeliefs.map { it!!.name}
            )            
            UncivGame.Current.setWorldScreen()
        }
    }

    private fun checkAndEnableRightSideButton() {
        if (religionName == null) return
        println(chosenFollowerBeliefs)
        if (chosenFollowerBeliefs.any { it == null }) return
        // check if founder belief chosen
        rightSideButton.enable()
    }

    private fun setupReligionIcons() {
        topReligionIcons.clear()
        
        // This should later be replaced with a user-modifiable text field, but not in this PR
        val descriptionLabel = "Choose an Icon and name for your Religion".toLabel() 
        
        val iconsTable = Table()
        iconsTable.align(Align.center)
        for (religionName in gameInfo.ruleSet.religions) {
            val image = ImageGetter.getReligionIcon(religionName)
            image.color = Color.BLACK
            val icon = image.surroundWithCircle(60f)
            val button = Button(icon, skin)
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

    private fun updateLeftTable() {
        leftChosenBeliefs.clear()
        val currentReligion = choosingCiv.religionManager.religion ?: Religion("Unknown", gameInfo, choosingCiv.civName)
        
        for (pantheon in currentReligion.getPantheonBeliefs() + currentReligion.getFollowerBeliefs()) {
            val beliefButton = convertBeliefToButton(pantheon)
            leftChosenBeliefs.add(beliefButton).pad(10f).row()
            beliefButton.disable()
        }
        
        for (newFollowerBelief in chosenFollowerBeliefs.withIndex()) {
            val newFollowerBeliefButton =
                if (newFollowerBelief.value == null) emptyBeliefButton("Follower")
                else convertBeliefToButton(newFollowerBelief.value!!)
                
            leftChosenBeliefs.add(newFollowerBeliefButton).pad(10f).row()
            newFollowerBeliefButton.onClick {
                loadRightTable("Follower", newFollowerBelief.index)
            }
        }
    }
    
    private fun loadRightTable(beliefType: String, leftButtonIndex: Int) {
        rightBeliefsToChoose.clear()
        val availableBeliefs = gameInfo.ruleSet.beliefs.values
            .filter { 
                it.type == beliefType
                && gameInfo.religions.values.none {
                    religion -> religion.hasBelief(it.name)
                }
                && (!chosenFollowerBeliefs.contains(it) || chosenFollowerBeliefs[leftButtonIndex] == it)
            }
        for (belief in availableBeliefs) {
            val beliefButton = convertBeliefToButton(belief)
            beliefButton.onClick {
                if (beliefType == "Follower") chosenFollowerBeliefs[leftButtonIndex] = belief
                else if (beliefType == "Founder") chosenFounderBeliefs[leftButtonIndex] = belief
                updateLeftTable()
                checkAndEnableRightSideButton()
            }
            rightBeliefsToChoose.add(beliefButton).pad(10f).row()
        }
    }
    
    private fun convertBeliefToButton(belief: Belief): Button {
        val contentsTable = Table()
        contentsTable.add(belief.type.toLabel()).row()
        contentsTable.add(belief.name.toLabel(fontSize = 24)).row()
        contentsTable.add(belief.uniques.joinToString().toLabel())
        return Button(contentsTable, skin)
    }
    
    private fun emptyBeliefButton(beliefType: String): Button {
        val contentsTable = Table()
        contentsTable.add("Choose a [$beliefType] belief!".toLabel())
        return Button(contentsTable, skin)
    }
}