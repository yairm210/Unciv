package com.unciv.ui.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.tr
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.CompletionStatus
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen

class VictoryScreen(val worldScreen: WorldScreen) : PickerScreen() {

    val gameInfo = worldScreen.gameInfo
    private val playerCivInfo = worldScreen.viewingCiv
    val victoryTypes = gameInfo.gameParameters.victoryTypes
    private val scientificVictoryEnabled = victoryTypes.contains(VictoryType.Scientific)
    private val culturalVictoryEnabled = victoryTypes.contains(VictoryType.Cultural)
    private val dominationVictoryEnabled = victoryTypes.contains(VictoryType.Domination)

    private val contentsTable = Table()

    init {
        val difficultyLabel = ("{Difficulty}: {${gameInfo.difficulty}}").toLabel()
        difficultyLabel.setPosition(10f, stage.height - 10, Align.topLeft)
        stage.addActor(difficultyLabel)

        val tabsTable = Table().apply { defaults().pad(10f) }
        val setMyVictoryButton = "Our status".toTextButton().onClick { setOurVictoryTable() }
        if (!playerCivInfo.isSpectator()) tabsTable.add(setMyVictoryButton)
        val setGlobalVictoryButton = "Global status".toTextButton().onClick { setGlobalVictoryTable() }
        tabsTable.add(setGlobalVictoryButton)
        val setCivRankingsButton = "Rankings".toTextButton().onClick { setCivRankingsTable() }
        tabsTable.add(setCivRankingsButton)
        topTable.add(tabsTable)
        topTable.addSeparator()
        topTable.add(contentsTable)

        if (playerCivInfo.isSpectator())
            setGlobalVictoryTable()
        else
            setOurVictoryTable()

        rightSideButton.isVisible = false

        var someoneHasWon = false

        val playerVictoryType = playerCivInfo.victoryManager.hasWonVictoryType()
        if (playerVictoryType != null) {
            someoneHasWon = true
            wonOrLost("You have won a [${playerVictoryType.name}] Victory!")
        }
        for (civ in gameInfo.civilizations.filter { it.isMajorCiv() && it != playerCivInfo }) {
            val civVictoryType = civ.victoryManager.hasWonVictoryType()
            if (civVictoryType != null) {
                someoneHasWon = true
                val winningCivName = civ.civName
                wonOrLost("[$winningCivName] has won a [${civVictoryType.name}] Victory!")
            }
        }

        if (playerCivInfo.isDefeated()) {
            wonOrLost("")
        } else if (!someoneHasWon) {
            setDefaultCloseAction()
            onBackButtonClicked { game.setWorldScreen() }
        }
    }


    private fun wonOrLost(description: String) {
        // description will be empty when the player loses - no parameters - so this will be when(null) and end up in the else branch:
        val endGameMessage = when (description.getPlaceholderParameters().firstOrNull()) {
            VictoryType.Time.name -> "The world has been convulsed by war. Many great and powerful civilizations have fallen, but you have survived - and emerged victorious! The world will long remember your glorious triumph!"
            VictoryType.Cultural.name -> "You have achieved victory through the awesome power of your Culture. Your civilization's greatness - the magnificence of its monuments and the power of its artists - have astounded the world! Poets will honor you as long as beauty brings gladness to a weary heart."
            VictoryType.Domination.name -> "The world has been convulsed by war. Many great and powerful civilizations have fallen, but you have survived - and emerged victorious! The world will long remember your glorious triumph!"
            VictoryType.Scientific.name -> "You have achieved victory through mastery of Science! You have conquered the mysteries of nature and led your people on a voyage to a brave new world! Your triumph will be remembered as long as the stars burn in the night sky!"
            VictoryType.Diplomatic.name -> "You have triumphed over your foes through the art of diplomacy! Your cunning and wisdom have earned you great friends - and divided and sown confusion among your enemies! Forever will you be remembered as the leader who brought peace to this weary world!"
            VictoryType.Neutral.name -> "Your civilization stands above all others! The exploits of your people shall be remembered until the end of civilization itself!"
            else -> "You have been defeated. Your civilization has been overwhelmed by its many foes. But your people do not despair, for they know that one day you shall return - and lead them forward to victory!"
        }

        descriptionLabel.setText(description.tr() + "\n" + endGameMessage.tr())

        rightSideButton.setText("Start new game".tr())
        rightSideButton.isVisible = true
        rightSideButton.enable()
        rightSideButton.onClick {
            val newGameSetupInfo = GameSetupInfo(gameInfo)
            newGameSetupInfo.mapParameters.reseed()
            game.setScreen(NewGameScreen(this, newGameSetupInfo))
        }

        closeButton.setText("One more turn...!".tr())
        closeButton.onClick {
            gameInfo.oneMoreTurnMode = true
            game.setWorldScreen()
        }
    }

    private fun setOurVictoryTable() {
        val ourVictoryStatusTable = Table()
        ourVictoryStatusTable.defaults().pad(10f)
        val victoriesToShow = gameInfo.ruleSet.victories.filter { !it.value.hiddenInVictoryScreen && victoryTypes.contains(VictoryType.valueOf(it.key))}
        
        for (victory in victoriesToShow) {
            ourVictoryStatusTable.add("[${victory.key}] Victory".toLabel())
        }
        ourVictoryStatusTable.row()
        
        for (victory in victoriesToShow) {
            ourVictoryStatusTable.add(getOurVictoryColumn(victory.key))
        }
        ourVictoryStatusTable.row()
        
        for (victory in victoriesToShow) {
            ourVictoryStatusTable.add(victory.value.victoryScreenHeader.toLabel())
        }
        
        contentsTable.clear()
        contentsTable.add(ourVictoryStatusTable)
    }

    private fun getOurVictoryColumn(victory: String): Table {
        val victoryObject = gameInfo.ruleSet.victories[victory]!!
        val table = Table()
        table.defaults().pad(5f)
        var firstIncomplete: Boolean = true
        for (milestone in victoryObject.milestoneObjects) {
            val completionStatus =
                when {
                    milestone.hasBeenCompletedBy(playerCivInfo) -> CompletionStatus.Completed
                    firstIncomplete -> {
                        firstIncomplete = false
                        CompletionStatus.Partially
                    }
                    else -> CompletionStatus.Incomplete
                }
            for (button in milestone.getVictoryScreenButtons(completionStatus, playerCivInfo)) {
                table.add(button).row()
            }
        }
        return table
    }

    private fun setGlobalVictoryTable() {
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }
        val globalVictoryTable = Table().apply { defaults().pad(10f) }

        if (scientificVictoryEnabled) globalVictoryTable.add(getGlobalScientificVictoryColumn(majorCivs))
        if (culturalVictoryEnabled) globalVictoryTable.add(getGlobalCulturalVictoryColumn(majorCivs))
        if (dominationVictoryEnabled) globalVictoryTable.add(getGlobalDominationVictoryColumn(majorCivs))

        contentsTable.clear()
        contentsTable.add(globalVictoryTable)
    }

    private fun getGlobalDominationVictoryColumn(majorCivs: List<CivilizationInfo>): Table {
        val dominationVictoryColumn = Table().apply { defaults().pad(10f) }

        dominationVictoryColumn.add("Undefeated civs".toLabel()).row()
        dominationVictoryColumn.addSeparator()

        for (civ in majorCivs.filter { !it.isDefeated() })
            dominationVictoryColumn.add(getCivGroup(civ, "", playerCivInfo)).fillX().row()

        for (civ in majorCivs.filter { it.isDefeated() })
            dominationVictoryColumn.add(getCivGroup(civ, "", playerCivInfo)).fillX().row()

        return dominationVictoryColumn
    }

    private fun getGlobalCulturalVictoryColumn(majorCivs: List<CivilizationInfo>): Table {
        val policyVictoryColumn = Table().apply { defaults().pad(10f) }
        policyVictoryColumn.add("Branches completed".toLabel()).row()
        policyVictoryColumn.addSeparator()

        data class CivToBranchesCompleted(val civ: CivilizationInfo, val branchesCompleted: Int)

        val civsToBranchesCompleted = majorCivs.map {
            CivToBranchesCompleted(it, it.policies.adoptedPolicies.count { pol -> Policy.isBranchCompleteByName(pol) })
        }.sortedByDescending { it.branchesCompleted }

        for (entry in civsToBranchesCompleted) {
            val civToBranchesHaveCompleted = getCivGroup(entry.civ, " - " + entry.branchesCompleted, playerCivInfo)
            policyVictoryColumn.add(civToBranchesHaveCompleted).fillX().row()
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
            val civToPartsBeRemaining = (getCivGroup(entry.civ, " - " + entry.partsRemaining, playerCivInfo))
            scientificVictoryColumn.add(civToPartsBeRemaining).fillX().row()
        }
        return scientificVictoryColumn
    }

    private fun setCivRankingsTable() {
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }
        val civRankingsTable = Table().apply { defaults().pad(5f) }

        for (category in RankingType.values()) {
            val column = Table().apply { defaults().pad(5f) }
            column.add(category.name.replace('_',' ').toLabel()).row()
            column.addSeparator()

            for (civ in majorCivs.sortedByDescending { it.getStatForRanking(category) }) {
                column.add(getCivGroup(civ, ": " + civ.getStatForRanking(category).toString(), playerCivInfo)).fillX().row()
            }

            civRankingsTable.add(column)
        }

        contentsTable.clear()
        contentsTable.add(civRankingsTable)
    }

    companion object {
        fun getCivGroup(civ: CivilizationInfo, afterCivNameText:String, currentPlayer:CivilizationInfo): Table {
            val civGroup = Table()

            var labelText = civ.civName.tr()+afterCivNameText
            var labelColor = Color.WHITE
            val backgroundColor: Color

            if (civ.isDefeated()) {
                civGroup.add(ImageGetter.getImage("OtherIcons/DisbandUnit")).size(30f)
                backgroundColor = Color.LIGHT_GRAY
                labelColor = Color.BLACK
            } else if (currentPlayer == civ  // || game.viewEntireMapForDebug
                || currentPlayer.knows(civ) || currentPlayer.isDefeated() || currentPlayer.victoryManager.hasWon()) {
                civGroup.add(ImageGetter.getNationIndicator(civ.nation, 30f))
                backgroundColor = civ.nation.getOuterColor()
                labelColor = civ.nation.getInnerColor()
            } else {
                civGroup.add(ImageGetter.getRandomNationIndicator(30f))
                backgroundColor = Color.DARK_GRAY
                labelText = Constants.unknownNationName
            }

            civGroup.background = ImageGetter.getRoundedEdgeRectangle(backgroundColor)
            val label = labelText.toLabel(labelColor)
            label.setAlignment(Align.center)

            civGroup.add(label).padLeft(10f)
            civGroup.pack()
            return civGroup
        }
    }
}
