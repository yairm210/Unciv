package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Belief
import com.unciv.ui.utils.*
import kotlin.math.max

class FoundReligionPickerScreen (
    private val choosingCiv: CivilizationInfo,
    private val gameInfo: GameInfo
): PickerScreen() {

    // Roughly follows the layout of the original (although I suck at UI designing, so please improve this)
    private val topReligionIcons = Table() // Top of the layout, contains icons for religions
    private val leftChosenBeliefs = Table() // Left middle part, contains buttons to select the types of beliefs to choose
    private val rightBeliefsToChoose: ScrollPane // Right middle part, contains the beliefs to choose
    
    private val middlePanes: SplitPane
    private val newPanes: SplitPane
 
    private var previouslySelectedIcon: Button? = null
    private var iconName: String? = null
    private var religionName: String? = null
    private var chosenFounderBelief: Belief? = null
    private var chosenFollowerBelief: Belief? = null

    init {
        closeButton.isVisible = true
        setDefaultCloseAction()
        
        stage.clear()
        
        setupReligionIcons()
                
        rightBeliefsToChoose = ScrollPane(Table())
        
        descriptionLabel.center(bottomTable)
        
        middlePanes = SplitPane(leftChosenBeliefs, rightBeliefsToChoose, false, skin)
        middlePanes.splitAmount = 0.5f
        
        newPanes = SplitPane(topReligionIcons, middlePanes, true, skin)
        newPanes.splitAmount = 0.15f
        
        splitPane = SplitPane(newPanes, bottomTable, true, skin)
        splitPane.splitAmount = screenSplit
        splitPane.setFillParent(true)
        stage.addActor(splitPane)
        
        rightSideButton.label = "Choose a religion".toLabel()
        rightSideButton.onClick(UncivSound.Choir) {
            choosingCiv.religionManager.foundReligion(iconName!!, religionName!!, "", "", /**chosenFollowerBelief!!.name, chosenFounderBelief!!.name*/)            
            UncivGame.Current.setWorldScreen()
        }
    }
    
    private fun checkAndEnableRightSideButton() {
        if (religionName == null) return
        // check if founder belief chosen
        // check if follower belief chosen
        rightSideButton.enable()
    }

    private fun setupReligionIcons() {
        topReligionIcons.clear()
        
        val descriptionLabel = Label("Choose an Icon and name for your Religion", skin)
        
        val iconsTable = Table()
        iconsTable.align(Align.center)
        for (religionName in listOf("Christianity", "Islam", "Taoism", "Hinduism", "Buddhism")) {
            if (gameInfo.religions.keys.any { it == religionName }) continue
            val image = ImageGetter.getReligionIcon(religionName)
            image.setColor(0f,0f,0f,1f)
            val icon = image.surroundWithCircle(60f)
            val button = Button(icon, skin)
            button.onClick {
                if (previouslySelectedIcon != null) {
                    previouslySelectedIcon!!.enable()
                }
                iconName = religionName
                this.religionName = religionName
                previouslySelectedIcon = button
                button.disable()
                descriptionLabel.setText(religionName)
                rightSideButton.label = "Found $religionName".toLabel()
                checkAndEnableRightSideButton()
            }
            if (religionName == this.religionName) button.disable()
            iconsTable.add(button).pad(5f)
        }
        iconsTable.row()
        topReligionIcons.add(iconsTable).padBottom(10f).row()
        topReligionIcons.add(descriptionLabel).center()
    }
}