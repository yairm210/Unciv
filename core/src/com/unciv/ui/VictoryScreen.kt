package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.translations.tr
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.utils.addSeparator
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class VictoryScreen : PickerScreen() {

    val playerCivInfo = UncivGame.Current.gameInfo.getCurrentPlayerCivilization()
    val victoryTypes = playerCivInfo.gameInfo.gameParameters.victoryTypes
    val scientificVictoryEnabled = victoryTypes.contains(VictoryType.Scientific)
    val culturalVictoryEnabled = victoryTypes.contains(VictoryType.Cultural)
    val dominationVictoryEnabled = victoryTypes.contains(VictoryType.Domination)


    val contentsTable = Table()

    init {
        val tabsTable = Table().apply { defaults().pad(10f) }
        val setMyVictoryButton = TextButton("Our status".tr(),skin)
        setMyVictoryButton.onClick { setMyVictoryTable() }
        tabsTable.add(setMyVictoryButton)
        val setGlobalVictoryButton = TextButton("Global status".tr(),skin)
        setGlobalVictoryButton .onClick { setGlobalVictoryTable() }
        tabsTable.add(setGlobalVictoryButton)
        topTable.add(tabsTable)
        topTable.addSeparator()
        topTable.add(contentsTable)

        setMyVictoryTable()

        rightSideButton.isVisible=false

        var someoneHasWon = false

        val playerVictoryType = playerCivInfo.victoryManager.hasWonVictoryType()
        if(playerVictoryType!=null){
            someoneHasWon=true
            when(playerVictoryType){
                VictoryType.Cultural -> wonOrLost("You have won a cultural victory!")
                VictoryType.Domination -> wonOrLost("You have won a domination victory!") // todo change translation
                VictoryType.Scientific -> wonOrLost("You have won a scientific victory!")
            }
        }
        for(civ in game.gameInfo.civilizations.filter { it.isMajorCiv() && it!=playerCivInfo }){
            val civVictoryType = civ.victoryManager.hasWonVictoryType()
            if(civVictoryType!=null){
                someoneHasWon=true
                val winningCivName = civ.civName
                when(civVictoryType){
                    VictoryType.Cultural -> wonOrLost("[$winningCivName] has won a cultural victory!")
                    VictoryType.Domination -> wonOrLost("[$winningCivName] has won a domination victory!")
                    VictoryType.Scientific -> wonOrLost("[$winningCivName] has  won a scientific victory!")
                }
            }
        }

        if(!someoneHasWon) setDefaultCloseAction()
    }


    fun wonOrLost(description: String) {

        val endGameMessage = when(description){
            "You have won a cultural victory!" -> "You have achieved victory through the awesome power of your Culture. Your civilization's greatness - the magnificence of its monuments and the power of its artists - have astounded the world! Poets will honor you as long as beauty brings gladness to a weary heart."
            "You have won a domination victory!" -> "The world has been convulsed by war. Many great and powerful civilizations have fallen, but you have survived - and emerged victorious! The world will long remember your glorious triumph!"
            "You have won a scientific victory!" -> "You have achieved victory through mastery of Science! You have conquered the mysteries of nature and led your people on a voyage to a brave new world! Your triumph will be remembered as long as the stars burn in the night sky!"
            else -> "You have been defeated. Your civilization has been overwhelmed by its many foes. But your people do not despair, for they know that one day you shall return - and lead them forward to victory!"
        }

        descriptionLabel.setText(description.tr()+"\n"+endGameMessage.tr() )

        rightSideButton.setText("Start new game".tr())
        rightSideButton.isVisible = true
        rightSideButton.enable()
        rightSideButton.onClick {
            UncivGame.Current.setScreen(NewGameScreen())
        }

        closeButton.setText("One more turn...!".tr())
        closeButton.onClick {
            playerCivInfo.gameInfo.oneMoreTurnMode = true
            UncivGame.Current.setWorldScreen()
        }
    }


    fun setMyVictoryTable(){
        val myVictoryStatusTable = Table()
        myVictoryStatusTable.defaults().pad(10f)
        if(scientificVictoryEnabled) myVictoryStatusTable.add("Science victory".toLabel())
        if(culturalVictoryEnabled) myVictoryStatusTable.add("Cultural victory".toLabel())
        if(dominationVictoryEnabled) myVictoryStatusTable.add("Conquest victory".toLabel())
        myVictoryStatusTable.row()
        if(scientificVictoryEnabled) myVictoryStatusTable.add(scienceVictoryColumn())
        if(culturalVictoryEnabled) myVictoryStatusTable.add(culturalVictoryColumn())
        if(dominationVictoryEnabled) myVictoryStatusTable.add(conquestVictoryColumn())
        myVictoryStatusTable.row()
        if(scientificVictoryEnabled) myVictoryStatusTable.add("Complete all the spaceship parts\n to win!".toLabel())
        if(culturalVictoryEnabled) myVictoryStatusTable.add("Complete 4 policy branches\n to win!".toLabel())
        if(dominationVictoryEnabled) myVictoryStatusTable.add("Destroy all enemies\n to win!".toLabel())

        contentsTable.clear()
        contentsTable.add(myVictoryStatusTable)
    }

    fun scienceVictoryColumn():Table{
        val t = Table()
        t.defaults().pad(5f)
        t.add(getMilestone("Built Apollo Program",playerCivInfo.containsBuildingUnique("Enables construction of Spaceship parts"))).row()

        val victoryManager= playerCivInfo.victoryManager

        for (key in victoryManager.requiredSpaceshipParts.keys)
            for (i in 0 until victoryManager.requiredSpaceshipParts[key]!!)
                t.add(getMilestone(key, victoryManager.currentsSpaceshipParts[key]!! > i)).row()     //(key, builtSpaceshipParts)

        return t
    }

    fun culturalVictoryColumn():Table{
        val t=Table()
        t.defaults().pad(5f)
        for(branch in playerCivInfo.gameInfo.ruleSet.PolicyBranches.values) {
            val finisher = branch.policies.last().name
            t.add(getMilestone(finisher, playerCivInfo.policies.isAdopted(finisher))).row()
        }
        return t
    }

    fun conquestVictoryColumn():Table{
        val table=Table()
        table.defaults().pad(5f)
        for (civ in playerCivInfo.gameInfo.civilizations) {
            if (civ.isCurrentPlayer() || !civ.isMajorCiv()) continue
            val civName =
                    if (playerCivInfo.diplomacy.containsKey(civ.civName)) civ.civName
                    else "???"
            table.add(getMilestone("Destroy [$civName]", civ.isDefeated())).row()
        }
        return table
    }

    fun getMilestone(text:String, achieved:Boolean): TextButton {
        val textButton = TextButton(text.tr(),skin)
        if(achieved) textButton.color = Color.GREEN
        else textButton.color = Color.GRAY
        return textButton
    }


    private fun setGlobalVictoryTable() {
        val majorCivs = game.gameInfo.civilizations.filter { it.isMajorCiv() }
        val globalVictoryTable = Table().apply { defaults().pad(10f) }

        if(scientificVictoryEnabled) globalVictoryTable.add(getGlobalScientificVictoryColumn(majorCivs))
        if(culturalVictoryEnabled) globalVictoryTable.add(getGlobalCulturalVictoryColumn(majorCivs))
        if(dominationVictoryEnabled) globalVictoryTable.add(getGlobalDominationVictoryColumn(majorCivs))

        contentsTable.clear()
        contentsTable.add(globalVictoryTable)
    }

    private fun getGlobalDominationVictoryColumn(majorCivs: List<CivilizationInfo>): Table {
        val dominationVictoryColumn = Table().apply { defaults().pad(10f) }

        dominationVictoryColumn.add("Undefeated civs".toLabel()).row()
        dominationVictoryColumn.addSeparator()

        for (civ in majorCivs.filter { !it.isDefeated() })
            dominationVictoryColumn.add(EmpireOverviewScreen.getCivGroup(civ, "", playerCivInfo)).row()

        for (civ in majorCivs.filter { it.isDefeated() })
            dominationVictoryColumn.add(EmpireOverviewScreen.getCivGroup(civ, "", playerCivInfo)).row()

        return dominationVictoryColumn
    }

    private fun getGlobalCulturalVictoryColumn(majorCivs: List<CivilizationInfo>): Table {
        val policyVictoryColumn = Table().apply { defaults().pad(10f) }
        policyVictoryColumn.add("Branches completed".toLabel()).row()
        policyVictoryColumn.addSeparator()

        data class civToBranchesCompleted(val civ: CivilizationInfo, val branchesCompleted: Int)

        val civsToBranchesCompleted =
                majorCivs.map { civToBranchesCompleted(it, it.policies.adoptedPolicies.count { pol -> pol.endsWith("Complete") }) }
                        .sortedByDescending { it.branchesCompleted }

        for (entry in civsToBranchesCompleted) {
            val civToBranchesHaveCompleted=EmpireOverviewScreen.getCivGroup(entry.civ, " - " + entry.branchesCompleted, playerCivInfo)
            policyVictoryColumn.add(civToBranchesHaveCompleted).row()
            civToBranchesHaveCompleted.touchable= Touchable.enabled
            civToBranchesHaveCompleted.onClick {
                game.setScreen(PolicyPickerScreen(UncivGame.Current.worldScreen,entry.civ, false))
                dispose()
            }
        }
        return policyVictoryColumn
    }

    private fun getGlobalScientificVictoryColumn(majorCivs: List<CivilizationInfo>): Table {
        val scientificVictoryColumn = Table().apply { defaults().pad(10f) }
        scientificVictoryColumn.add("Spaceship parts remaining".toLabel()).row()
        scientificVictoryColumn.addSeparator()

        data class civToSpaceshipPartsRemaining(val civ: CivilizationInfo, val partsRemaining: Int)

        val civsToPartsRemaining = majorCivs.map {
            civToSpaceshipPartsRemaining(it,
                    it.victoryManager.spaceshipPartsRemaining())
        }

        for (entry in civsToPartsRemaining) {
            val civToPartsBeRemaining=(EmpireOverviewScreen.getCivGroup(entry.civ, " - " + entry.partsRemaining, playerCivInfo))
            scientificVictoryColumn.add(civToPartsBeRemaining).row()
        }
        return scientificVictoryColumn
    }

}
