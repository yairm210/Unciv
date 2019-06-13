package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.addSeparator
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class VictoryScreen : PickerScreen() {

    val playerCivInfo = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()

    val contentsTable = Table()

    init {
        val tabsTable = Table().apply { defaults().pad(10f) }
        val setMyVictoryButton = TextButton("Our status",skin)
        setMyVictoryButton.onClick { setMyVictoryTable() }
        tabsTable.add(setMyVictoryButton)
        val setGlobalVictoryButton = TextButton("Global status",skin)
        setGlobalVictoryButton .onClick { setGlobalVictoryTable() }
        tabsTable.add(setGlobalVictoryButton)
        topTable.add(tabsTable)
        topTable.addSeparator()
        topTable.add(contentsTable)

        setMyVictoryTable()

        rightSideButton.isVisible=false

        if(playerCivInfo.victoryManager.hasWonScientificVictory()){
            won("You have won a scientific victory!")
        }
        else if(playerCivInfo.victoryManager.hasWonCulturalVictory()){
            won("You have won a cultural victory!")
        }
        else if(playerCivInfo.victoryManager.hasWonConquestVictory()){
            won("You have won a conquest victory!")
        }

        else setDefaultCloseAction()
    }


    fun won(description: String) {
        descriptionLabel.setText(description.tr())

        rightSideButton.setText("Start new game".tr())
        rightSideButton.isVisible = true
        rightSideButton.enable()
        rightSideButton.onClick { UnCivGame.Current.startNewGame() }

        closeButton.setText("One more turn...!".tr())
        closeButton.onClick {
            playerCivInfo.gameInfo.oneMoreTurnMode = true
            UnCivGame.Current.setWorldScreen()
        }
    }


    fun setMyVictoryTable(){
        val myVictoryStatusTable = Table()
        myVictoryStatusTable.defaults().pad(10f)
        myVictoryStatusTable.add("Science victory".toLabel())
        myVictoryStatusTable.add("Cultural victory".toLabel())
        myVictoryStatusTable.add("Conquest victory".toLabel())
        myVictoryStatusTable.row()
        myVictoryStatusTable.add(scienceVictoryColumn())
        myVictoryStatusTable.add(culturalVictoryColumn())
        myVictoryStatusTable.add(conquestVictoryColumn())
        myVictoryStatusTable.row()
        myVictoryStatusTable.add("Complete all the spaceship parts\n to win!".toLabel())
        myVictoryStatusTable.add("Complete 4 policy branches\n to win!".toLabel())
        myVictoryStatusTable.add("Destroy all enemies\n to win!".toLabel())

        contentsTable.clear()
        contentsTable.add(myVictoryStatusTable)
    }

    fun scienceVictoryColumn():Table{
        val t = Table()
        t.defaults().pad(5f)
        t.add(getMilestone("Built Apollo Program".tr(),playerCivInfo.getBuildingUniques().contains("Enables construction of Spaceship parts"))).row()

        val victoryManager= playerCivInfo.victoryManager

        for (key in victoryManager.requiredSpaceshipParts.keys)
            for (i in 0 until victoryManager.requiredSpaceshipParts[key]!!)
                t.add(getMilestone(key, victoryManager.currentsSpaceshipParts[key]!! > i)).row()     //(key, builtSpaceshipParts)

        return t
    }

    fun culturalVictoryColumn():Table{
        val t=Table()
        t.defaults().pad(5f)
        for(branch in GameBasics.PolicyBranches.values) {
            val finisher = branch.policies.last().name
            t.add(getMilestone(finisher, playerCivInfo.policies.isAdopted(finisher))).row()
        }
        return t
    }

    fun conquestVictoryColumn():Table{
        val table=Table()
        table.defaults().pad(5f)
        for (civ in playerCivInfo.gameInfo.civilizations) {
            if (civ.isPlayerCivilization() || civ.isBarbarianCivilization() || civ.isCityState()) continue
            val civName =
                    if (playerCivInfo.diplomacy.containsKey(civ.civName)) civ.civName
                    else "???"
            table.add(getMilestone("Destroy [$civName]".tr(), civ.isDefeated())).row()
        }
        return table
    }

    fun getMilestone(text:String, achieved:Boolean): TextButton {
        val textButton = TextButton(text,skin)
        if(achieved) textButton.color = Color.GREEN
        else textButton.color = Color.GRAY
        return textButton
    }


    private fun setGlobalVictoryTable() {
        val majorCivs = game.gameInfo.civilizations.filter { it.isMajorCiv() }
        val globalVictoryTable = Table().apply { defaults().pad(10f) }

        globalVictoryTable.add(getGlobalScientificVictoryColumn(majorCivs))
        globalVictoryTable.add(getGlobalPolicyVictoryColumn(majorCivs))
        globalVictoryTable.add(getGlobalDominationVictoryColumn(majorCivs))

        contentsTable.clear()
        contentsTable.add(globalVictoryTable)
    }

    private fun getGlobalDominationVictoryColumn(majorCivs: List<CivilizationInfo>): Table {
        val dominationVictoryColumn = Table().apply { defaults().pad(10f) }

        dominationVictoryColumn.add("Undefeated civs".toLabel()).row()
        dominationVictoryColumn.addSeparator()

        for (civ in majorCivs.filter { !it.isDefeated() })
            dominationVictoryColumn.add(TextButton(civ.civName.tr(), skin).apply { color = Color.GREEN }).row()

        for (civ in majorCivs.filter { it.isDefeated() })
            dominationVictoryColumn.add(TextButton(civ.civName.tr(), skin).apply { color = Color.GRAY }).row()
        return dominationVictoryColumn
    }

    private fun getGlobalPolicyVictoryColumn(majorCivs: List<CivilizationInfo>): Table {
        val policyVictoryColumn = Table().apply { defaults().pad(10f) }
        policyVictoryColumn.add("Branches completed".toLabel()).row()
        policyVictoryColumn.addSeparator()

        data class civToBranchesCompleted(val civ: CivilizationInfo, val branchesCompleted: Int)

        val civsToBranchesCompleted =
                majorCivs.map { civToBranchesCompleted(it, it.policies.adoptedPolicies.count { pol -> pol.endsWith("Complete") }) }
                        .sortedByDescending { it.branchesCompleted }

        for (entry in civsToBranchesCompleted)
            policyVictoryColumn.add(TextButton(entry.civ.civName.tr() + " - " + entry.branchesCompleted, skin)).row()
        return policyVictoryColumn
    }

    private fun getGlobalScientificVictoryColumn(majorCivs: List<CivilizationInfo>): Table {
        val scientificVictoryColumn = Table().apply { defaults().pad(10f) }
        scientificVictoryColumn.add("Spaceship parts remaining".toLabel()).row()
        scientificVictoryColumn.addSeparator()

        data class civToSpaceshipPartsRemaining(val civ: CivilizationInfo, val partsRemaining: Int)

        val civsToPartsRemaining = majorCivs.map {
            civToSpaceshipPartsRemaining(it,
                    it.victoryManager.requiredSpaceshipParts.size - it.victoryManager.currentsSpaceshipParts.size)
        }

        for (entry in civsToPartsRemaining)
            scientificVictoryColumn.add(TextButton(entry.civ.civName.tr() + " - " + entry.partsRemaining, skin)).row()
        return scientificVictoryColumn
    }

}