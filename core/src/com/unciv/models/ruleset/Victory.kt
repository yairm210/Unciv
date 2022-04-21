package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.models.stats.INamed
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Counter
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.models.translations.tr
import com.unciv.ui.utils.toTextButton


enum class MilestoneType(val text: String) {
    BuiltBuilding("Built [building]"),
    BuildingBuiltGlobally("[building] built globally"),
    AddedSSPartsInCapital("Add all spaceship parts in capital"),
    DestroyAllPlayers("Destroy all players"),
    CaptureAllCapitals("Capture all capitals"),
    CompletePolicyBranches("Complete [amount] Policy branches"),
    WinDiplomaticVote("Win diplomatic vote"),
    ScoreAfterTimeOut("Have highest score after max turns"),
}

enum class CompletionStatus {
    Completed,
    Partially,
    Incomplete
}

class Victory : INamed {
    override var name = ""
    val victoryScreenHeader = "Do things to win!"
    val hiddenInVictoryScreen = false
    // Things to do to win
    // Needs to be ordered, as the milestones are supposed to be obtained in a specific order
    val milestones = ArrayList<String>()
    val milestoneObjects by lazy { milestones.map { Milestone(it, this) }}
    val spaceshipParts = ArrayList<String>()

    val getRequiredSpaceshipParts by lazy {
        val parts = Counter<String>()
        for (spaceshipPart in spaceshipParts)
            parts.add(spaceshipPart, 1)
        parts
    }
    
    val victoryString = "Your civilization stands above all others! The exploits of your people shall be remembered until the end of civilization itself!"
    val defeatString = "You have been defeated. Your civilization has been overwhelmed by its many foes. But your people do not despair, for they know that one day you shall return - and lead them forward to victory!"
    
    fun enablesMaxTurns(): Boolean = milestoneObjects.any { it.type == MilestoneType.ScoreAfterTimeOut }
}

class Milestone(private val uniqueDescription: String, private val accompaniedVictory: Victory) {

    val type: MilestoneType = MilestoneType.values().first { uniqueDescription.getPlaceholderText() == it.text.getPlaceholderText() }
    val params by lazy { uniqueDescription.getPlaceholderParameters() }

    fun getDisplayString() = uniqueDescription.tr()
    fun hasBeenCompletedBy(civInfo: CivilizationInfo): Boolean {
        return when (type) {
            MilestoneType.BuiltBuilding ->
                civInfo.cities.any { it.cityConstructions.builtBuildings.contains(params[0])}
            MilestoneType.AddedSSPartsInCapital -> {
                val incompleteSpaceshipParts = accompaniedVictory.getRequiredSpaceshipParts.clone()
                incompleteSpaceshipParts.remove(civInfo.victoryManager.currentsSpaceshipParts)
                incompleteSpaceshipParts.isEmpty()
            }
            MilestoneType.DestroyAllPlayers ->
                civInfo.gameInfo.getAliveMajorCivs() == listOf(civInfo)
            MilestoneType.CaptureAllCapitals ->
                civInfo.originalMajorCapitalsOwned() == civInfo.gameInfo.civilizations.count { it.isMajorCiv() }
            MilestoneType.CompletePolicyBranches ->
                civInfo.policies.completedBranches.size >= params[0].toInt()
            MilestoneType.BuildingBuiltGlobally -> civInfo.gameInfo.getCities().any {
                it.cityConstructions.builtBuildings.contains(params[0])
            }
            MilestoneType.WinDiplomaticVote -> civInfo.victoryManager.hasEverWonDiplomaticVote
            MilestoneType.ScoreAfterTimeOut -> {
                civInfo.gameInfo.turns >= civInfo.gameInfo.gameParameters.maxTurns
                && civInfo == civInfo.gameInfo.civilizations.maxByOrNull { it.calculateTotalScore() }
            }
        }
    }

    private fun getMilestoneButton(text: String, achieved: Boolean): TextButton {
        val textButton = text.toTextButton()
        if (achieved) textButton.color = Color.GREEN
        else textButton.color = Color.GRAY
        return textButton
    }
    
    fun getVictoryScreenButtonHeaderText(completed: Boolean, civInfo: CivilizationInfo): String {
        return when (type) {
            MilestoneType.BuildingBuiltGlobally, MilestoneType.WinDiplomaticVote, 
            MilestoneType.ScoreAfterTimeOut, MilestoneType.BuiltBuilding -> 
                uniqueDescription
            MilestoneType.CompletePolicyBranches -> {
                val amountToDo = params[0]
                val amountDone =
                    if (completed) amountToDo
                    else civInfo.getCompletedPolicyBranchesCount()
                "[$uniqueDescription] ($amountDone/$amountToDo)"
            }
            MilestoneType.CaptureAllCapitals -> {
                val amountToDo = civInfo.gameInfo.civilizations.count { it.isMajorCiv() }
                val amountDone =
                    if (completed) amountToDo
                    else civInfo.originalMajorCapitalsOwned()
                "[$uniqueDescription] ($amountDone/$amountToDo)"
            }
            MilestoneType.DestroyAllPlayers -> {
                val amountToDo = civInfo.gameInfo.civilizations.count { it.isMajorCiv() } - 1  // Don't count yourself
                val amountDone =
                    if (completed) amountToDo
                    else amountToDo - (civInfo.gameInfo.getAliveMajorCivs().filter { it != civInfo }.count())
                "[$uniqueDescription] ($amountDone/$amountToDo)"
            }
            MilestoneType.AddedSSPartsInCapital -> {
                val completeSpaceshipParts = civInfo.victoryManager.currentsSpaceshipParts
                val incompleteSpaceshipParts = accompaniedVictory.getRequiredSpaceshipParts.clone()
                val amountToDo = incompleteSpaceshipParts.sumValues()
                incompleteSpaceshipParts.remove(completeSpaceshipParts)
                
                val amountDone = amountToDo - incompleteSpaceshipParts.sumValues()
                
                "[$uniqueDescription] ($amountDone/$amountToDo)"
            }
        }
    }
    
    fun getVictoryScreenButtons(completionStatus: CompletionStatus, civInfo: CivilizationInfo): List<TextButton> {
        val headerButton = getMilestoneButton(
            getVictoryScreenButtonHeaderText(completionStatus == CompletionStatus.Completed, civInfo), 
            completionStatus == CompletionStatus.Completed
        )
        if (completionStatus == CompletionStatus.Completed || completionStatus == CompletionStatus.Incomplete) {
            // When done or not working on this milestone, only show the header button
            return listOf(headerButton)
        }
        // Otherwise, append the partial buttons of each step
        val buttons = mutableListOf(headerButton)
        when (type) {
            // No extra buttons necessary
            MilestoneType.BuiltBuilding, MilestoneType.BuildingBuiltGlobally, 
            MilestoneType.ScoreAfterTimeOut, MilestoneType.WinDiplomaticVote -> {} 
            MilestoneType.AddedSSPartsInCapital -> {
                val completedSpaceshipParts = civInfo.victoryManager.currentsSpaceshipParts
                val incompleteSpaceshipParts = accompaniedVictory.getRequiredSpaceshipParts.clone()
                incompleteSpaceshipParts.remove(completedSpaceshipParts)
                
                for (part in completedSpaceshipParts) {
                    repeat(part.value) {
                        buttons.add(getMilestoneButton(part.key, true))
                    }
                }
                for (part in incompleteSpaceshipParts) {
                    repeat(part.value) {
                        buttons.add(getMilestoneButton(part.key, false))
                    }
                }
            }
            
            MilestoneType.DestroyAllPlayers -> {
                for (civ in civInfo.gameInfo.civilizations.filter { it != civInfo && it.isMajorCiv() && !it.isAlive() }) {
                    buttons.add(getMilestoneButton("Destroy [${civ.civName}]", true))
                }
                for (civ in civInfo.gameInfo.getAliveMajorCivs().filter { it != civInfo}) {
                    buttons.add(getMilestoneButton("Destroy [${civ.civName}]", false))
                }
            }
            
            MilestoneType.CaptureAllCapitals -> {
                for (city in civInfo.gameInfo.getAliveMajorCivs()
                    .mapNotNull { 
                        civ -> civ.cities.firstOrNull { it.isOriginalCapital && it.foundingCiv == civ.civName } 
                    }
                ) {
                    buttons.add(getMilestoneButton("Capture [${city.name}]", false))
                }
            }
            
            MilestoneType.CompletePolicyBranches -> {
                for (branch in civInfo.gameInfo.ruleSet.policyBranches.values) {
                    val finisher = branch.policies.last().name
                    buttons.add(getMilestoneButton(finisher, civInfo.policies.isAdopted(finisher)))
                }
            }
        }
        return buttons
    }
}


enum class VictoryType {
    Neutral,
    Cultural,
    Diplomatic,
    Domination,
    Scientific,
    Time,
}