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
    AddedSSPartsInCapital("Add all spaceship parts in capital"),
    DestroyAllPlayers("Destroy all players"),
    CaptureAllCapitals("Capture all capitals"),
    CompletePolicyBranches("Complete [amount] Policy branches")
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
}

class Milestone(private val uniqueDescription: String, private val accompaniedVictory: Victory) {

    val type: MilestoneType = MilestoneType.values().first { uniqueDescription.getPlaceholderText() == it.text.getPlaceholderText() }
    val params by lazy { uniqueDescription.getPlaceholderParameters() }

    fun getDisplayString() = uniqueDescription.tr()
    fun hasBeenCompletedBy(civInfo: CivilizationInfo): Boolean {
        return when (type) {
            MilestoneType.BuiltBuilding ->
                civInfo.cities.any { it.cityConstructions.builtBuildings.contains(params[0])}
            MilestoneType.AddedSSPartsInCapital ->
                civInfo.victoryManager.spaceshipPartsRemaining() == 0
            MilestoneType.DestroyAllPlayers ->
                civInfo.gameInfo.getAliveMajorCivs() == listOf(civInfo)
            MilestoneType.CaptureAllCapitals ->
                civInfo.originalMajorCapitalsOwned() == civInfo.gameInfo.civilizations.count { it.isMajorCiv() }
            MilestoneType.CompletePolicyBranches ->
                civInfo.policies.completedBranches.size >= params[0].toInt()
        }
    }

    private fun getMilestoneButton(text: String, achieved: Boolean): TextButton {
        val textButton = text.toTextButton()
        if (achieved) textButton.color = Color.GREEN
        else textButton.color = Color.GRAY
        return textButton
    }
    
    private fun getVictoryScreenButtonHeader(completed: Boolean, civInfo: CivilizationInfo): TextButton {
        return when (type) {
            MilestoneType.BuiltBuilding -> getMilestoneButton(uniqueDescription, completed)
            MilestoneType.CompletePolicyBranches -> {
                val amountToDo = params[0]
                val amountDone =
                    if (completed) amountToDo
                    else civInfo.getCompletedPolicyBranchesCount()
                getMilestoneButton("[$uniqueDescription] ($amountDone/$amountToDo)", completed)
            }
            MilestoneType.CaptureAllCapitals -> {
                val amountToDo = civInfo.gameInfo.civilizations.count { it.isMajorCiv() }
                val amountDone =
                    if (completed) amountToDo
                    else civInfo.originalMajorCapitalsOwned()
                getMilestoneButton("[$uniqueDescription] ($amountDone/$amountToDo)", completed)
            }
            MilestoneType.DestroyAllPlayers -> {
                val amountToDo = civInfo.gameInfo.civilizations.count { it.isMajorCiv() } - 1  // Don't count yourself
                val amountDone =
                    if (completed) amountToDo
                    else amountToDo - (civInfo.gameInfo.getAliveMajorCivs().count() - 1) // Don't count yourself (again)
                getMilestoneButton("[$uniqueDescription] ($amountDone/$amountToDo)", completed)
            }
            MilestoneType.AddedSSPartsInCapital -> {
                val amountToDo = accompaniedVictory.spaceshipParts.count()
                val amountDone =
                    if (completed) amountToDo
                    else amountToDo - civInfo.victoryManager.spaceshipPartsRemaining()
                getMilestoneButton("[$uniqueDescription] ($amountDone/$amountToDo)", completed)
            }
        }
    }
    
    fun getVictoryScreenButtons(completionStatus: CompletionStatus, civInfo: CivilizationInfo): List<TextButton> {
        val headerButton = getVictoryScreenButtonHeader(completionStatus == CompletionStatus.Completed, civInfo)
        if (completionStatus == CompletionStatus.Completed || completionStatus == CompletionStatus.Incomplete) {
            // When done or almost done, only show the header button
            return listOf(headerButton)
        }
        // Otherwise, append the partial buttons of each step
        val buttons = mutableListOf(headerButton)
        when (type) {
            MilestoneType.BuiltBuilding -> {} // No extra buttons necessary
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