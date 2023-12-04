package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Counter
import com.unciv.models.stats.INamed
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.ui.components.extensions.toTextButton


enum class MilestoneType(val text: String) {
    BuiltBuilding("Build [building]"),
    BuildingBuiltGlobally("Anyone should build [building]"),
    AddedSSPartsInCapital("Add all [comment] in capital"),
    DestroyAllPlayers("Destroy all players"),
    CaptureAllCapitals("Capture all capitals"),
    CompletePolicyBranches("Complete [amount] Policy branches"),
    WorldReligion("Become the world religion"),
    WinDiplomaticVote("Win diplomatic vote"),
    ScoreAfterTimeOut("Have highest score after max turns"),
}

class Victory : INamed {

    enum class CompletionStatus {
        Completed,
        Partially,
        Incomplete
    }

    enum class Focus {
        Production,
        Gold,
        Culture,
        Science,
        Faith,
        Military,
        CityStates,
        Score,
    }

    override var name = ""
    val victoryScreenHeader = "Do things to win!"
    val hiddenInVictoryScreen = false
    // Things to do to win
    // Needs to be ordered, as the milestones are supposed to be obtained in a specific order
    val milestones = ArrayList<String>()
    val milestoneObjects by lazy { milestones.map { Milestone(it, this) }}
    val requiredSpaceshipParts = ArrayList<String>()

    val requiredSpaceshipPartsAsCounter by lazy {
        val parts = Counter<String>()
        for (spaceshipPart in requiredSpaceshipParts)
            parts.add(spaceshipPart, 1)
        parts
    }

    val victoryString = "Your civilization stands above all others! The exploits of your people shall be remembered until the end of civilization itself!"
    val defeatString = "You have been defeated. Your civilization has been overwhelmed by its many foes. But your people do not despair, for they know that one day you shall return - and lead them forward to victory!"

    fun enablesMaxTurns(): Boolean = milestoneObjects.any { it.type == MilestoneType.ScoreAfterTimeOut }
    fun getThingsToFocus(civInfo: Civilization): Set<Focus> = milestoneObjects
        .filter { !it.hasBeenCompletedBy(civInfo) }
        .map { it.getFocus(civInfo) }
        .toSet()
}

class Milestone(val uniqueDescription: String, private val parentVictory: Victory) {

    val type: MilestoneType? = MilestoneType.values().firstOrNull { uniqueDescription.getPlaceholderText() == it.text.getPlaceholderText() }
    val params = uniqueDescription.getPlaceholderParameters()

    private fun getIncompleteSpaceshipParts(civInfo: Civilization): Counter<String> {
        val incompleteSpaceshipParts = parentVictory.requiredSpaceshipPartsAsCounter.clone()
        incompleteSpaceshipParts.remove(civInfo.victoryManager.currentsSpaceshipParts)
        return incompleteSpaceshipParts
    }

    fun hasBeenCompletedBy(civInfo: Civilization): Boolean {
        return when (type!!) {
            MilestoneType.BuiltBuilding ->
                civInfo.cities.any { it.cityConstructions.isBuilt(params[0])}
            MilestoneType.AddedSSPartsInCapital -> {
                getIncompleteSpaceshipParts(civInfo).isEmpty()
            }
            MilestoneType.DestroyAllPlayers ->
                civInfo.gameInfo.getAliveMajorCivs() == listOf(civInfo)
            MilestoneType.CaptureAllCapitals ->
                civInfo.originalMajorCapitalsOwned() == civInfo.gameInfo.civilizations.count { it.isMajorCiv() }
            MilestoneType.CompletePolicyBranches ->
                civInfo.policies.completedBranches.size >= params[0].toInt()
            MilestoneType.BuildingBuiltGlobally -> civInfo.gameInfo.getCities().any {
                it.cityConstructions.isBuilt(params[0])
            }
            MilestoneType.WinDiplomaticVote -> civInfo.victoryManager.hasEverWonDiplomaticVote
            MilestoneType.ScoreAfterTimeOut -> {
                civInfo.gameInfo.turns >= civInfo.gameInfo.gameParameters.maxTurns
                && civInfo == civInfo.gameInfo.civilizations.maxByOrNull { it.calculateTotalScore() }
            }
            MilestoneType.WorldReligion -> {
                civInfo.gameInfo.isReligionEnabled()
                        && civInfo.religionManager.religion != null
                        && civInfo.gameInfo.civilizations
                    .filter { it.isMajorCiv() && it.isAlive() }
                    .all {
                        it.religionManager.isMajorityReligionForCiv(civInfo.religionManager.religion!!)
                    }
            }
        }
    }

    private fun getMilestoneButton(text: String, achieved: Boolean): TextButton {
        val textButton = text.toTextButton()
        if (achieved) textButton.color = Color.GREEN
        else textButton.color = Color.GRAY
        return textButton
    }

    fun getVictoryScreenButtonHeaderText(completed: Boolean, civInfo: Civilization): String {
        return when (type!!) {
            MilestoneType.BuildingBuiltGlobally, MilestoneType.WinDiplomaticVote,
            MilestoneType.ScoreAfterTimeOut, MilestoneType.BuiltBuilding ->
                uniqueDescription
            MilestoneType.CompletePolicyBranches -> {
                val amountToDo = params[0]
                val amountDone =
                    if (completed) amountToDo
                    else civInfo.getCompletedPolicyBranchesCount()
                "{$uniqueDescription} ($amountDone/$amountToDo)"
            }
            MilestoneType.CaptureAllCapitals -> {
                val amountToDo = civInfo.gameInfo.civilizations.count { it.isMajorCiv() }
                val amountDone =
                    if (completed) amountToDo
                    else civInfo.originalMajorCapitalsOwned()
                if (civInfo.hideCivCount())
                    "{$uniqueDescription} ($amountDone/?)"
                else
                    "{$uniqueDescription} ($amountDone/$amountToDo)"
            }
            MilestoneType.DestroyAllPlayers -> {
                val amountToDo = civInfo.gameInfo.civilizations.count { it.isMajorCiv() } - 1  // Don't count yourself
                val amountDone =
                    if (completed) amountToDo
                    else amountToDo - (civInfo.gameInfo.getAliveMajorCivs().count { it != civInfo })
                if (civInfo.hideCivCount())
                    "{$uniqueDescription} ($amountDone/?)"
                else
                    "{$uniqueDescription} ($amountDone/$amountToDo)"
            }
            MilestoneType.AddedSSPartsInCapital -> {
                val completeSpaceshipParts = civInfo.victoryManager.currentsSpaceshipParts
                val incompleteSpaceshipParts = parentVictory.requiredSpaceshipPartsAsCounter.clone()
                val amountToDo = incompleteSpaceshipParts.sumValues()
                incompleteSpaceshipParts.remove(completeSpaceshipParts)

                val amountDone = amountToDo - incompleteSpaceshipParts.sumValues()

                "{$uniqueDescription} ($amountDone/$amountToDo)"
            }
            MilestoneType.WorldReligion -> {
                val amountToDo = civInfo.gameInfo.civilizations.count { it.isMajorCiv() && it.isAlive() } - 1  // Don't count yourself
                val amountDone =
                    when {
                        completed -> amountToDo
                        civInfo.religionManager.religion == null -> 0
                        civInfo.religionManager.religion!!.isPantheon() -> 1
                        else -> civInfo.gameInfo.civilizations.count {
                            it.isMajorCiv() && it.isAlive() &&
                            it.religionManager.isMajorityReligionForCiv(civInfo.religionManager.religion!!)
                        }
                    }
                "{$uniqueDescription} ($amountDone/$amountToDo)"
            }
        }
    }

    fun getVictoryScreenButtons(completionStatus: Victory.CompletionStatus, civInfo: Civilization): List<TextButton> {
        val headerButton = getMilestoneButton(
            getVictoryScreenButtonHeaderText(completionStatus == Victory.CompletionStatus.Completed, civInfo),
            completionStatus == Victory.CompletionStatus.Completed
        )
        if (completionStatus == Victory.CompletionStatus.Completed || completionStatus == Victory.CompletionStatus.Incomplete) {
            // When done or not working on this milestone, only show the header button
            return listOf(headerButton)
        }
        // Otherwise, append the partial buttons of each step
        val buttons = mutableListOf(headerButton)
        when (type) {
            // No extra buttons necessary
            null,
            MilestoneType.BuiltBuilding, MilestoneType.BuildingBuiltGlobally,
            MilestoneType.ScoreAfterTimeOut, MilestoneType.WinDiplomaticVote -> {}

            MilestoneType.AddedSSPartsInCapital -> {
                val completedSpaceshipParts = civInfo.victoryManager.currentsSpaceshipParts
                val incompleteSpaceshipParts = getIncompleteSpaceshipParts(civInfo)

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
                val hideCivCount = civInfo.hideCivCount()
                for (civ in civInfo.gameInfo.civilizations) {
                    if (civ == civInfo || !civ.isMajorCiv()) continue
                    if (hideCivCount && !civInfo.knows(civ)) continue
                    val milestoneText =
                        if (civInfo.knows(civ) || civ.isDefeated()) "Destroy [${civ.civName}]"
                        else "Destroy [${Constants.unknownNationName}]"
                    buttons.add(getMilestoneButton(milestoneText, civ.isDefeated()))
                }
                if (hideCivCount) buttons.add(getMilestoneButton("Destroy ? * [${Constants.unknownNationName}]", false))
            }

            MilestoneType.CaptureAllCapitals -> {
                val hideCivCount = civInfo.hideCivCount()
                val originalCapitals = civInfo.gameInfo.getCities().filter { it.isOriginalCapital }
                for (city in originalCapitals) {
                    val isKnown = civInfo.hasExplored(city.getCenterTile())
                    if (hideCivCount && !isKnown) continue
                    val milestoneText =
                        if (isKnown) "Capture [${city.name}]"
                        else "Capture [${Constants.unknownCityName}]"
                    buttons.add(getMilestoneButton(milestoneText, city.civ == civInfo))
                }
                if (hideCivCount) buttons.add(getMilestoneButton("Capture ? * [${Constants.unknownCityName}]", false))
            }

            MilestoneType.CompletePolicyBranches -> {
                for (branch in civInfo.gameInfo.ruleset.policyBranches.values) {
                    val finisher = branch.policies.last().name
                    buttons.add(getMilestoneButton(finisher, civInfo.policies.isAdopted(finisher)))
                }
            }

            MilestoneType.WorldReligion -> {
                val hideCivCount = civInfo.hideCivCount()
                val majorCivs = civInfo.gameInfo.civilizations.filter { it.isMajorCiv() && it.isAlive() }
                val civReligion = civInfo.religionManager.religion
                for (civ in majorCivs) {
                    if (hideCivCount && !civInfo.knows(civ)) continue
                    val milestoneText =
                        if (civInfo.knows(civ)) "Majority religion of [${civ.civName}]"
                        else "Majority religion of [${Constants.unknownNationName}]"
                    val milestoneMet = civReligion != null
                            && (!civReligion.isPantheon() || civInfo == civ)
                            && civ.religionManager.isMajorityReligionForCiv(civReligion)
                    buttons.add(getMilestoneButton(milestoneText, milestoneMet))
                }
                if (hideCivCount) buttons.add(getMilestoneButton("Majority religion of ? * [${Constants.unknownCityName}]", false))
            }
        }
        return buttons
    }

    fun getFocus(civInfo: Civilization): Victory.Focus {
        val ruleset = civInfo.gameInfo.ruleset
        return when (type!!) {
            MilestoneType.BuiltBuilding -> {
                val building = ruleset.buildings[params[0]]!!
                if (!civInfo.tech.isResearched(building)) Victory.Focus.Science
//                if (building.hasUnique(UniqueType.Unbuildable)) Stat.Gold // Temporary, should be replaced with whatever is required to buy
                Victory.Focus.Production
            }
            MilestoneType.BuildingBuiltGlobally -> {
                val building = ruleset.buildings[params[0]]!!
                if (!civInfo.tech.isResearched(building)) Victory.Focus.Science
//                if (building.hasUnique(UniqueType.Unbuildable)) Victory.Focus.Gold
                Victory.Focus.Production
            }
            MilestoneType.AddedSSPartsInCapital -> {
                val constructions =
                    getIncompleteSpaceshipParts(civInfo).keys.map {
                        if (it in ruleset.buildings)
                            ruleset.buildings[it]!!
                        else ruleset.units[it]!!
                    }
                if (constructions.any { !civInfo.tech.isResearched(it) } ) Victory.Focus.Science
//                if (constructions.any { it.hasUnique(UniqueType.Unbuildable) } ) Stat.Gold
                Victory.Focus.Production
            }
            MilestoneType.DestroyAllPlayers, MilestoneType.CaptureAllCapitals -> Victory.Focus.Military
            MilestoneType.CompletePolicyBranches -> Victory.Focus.Culture
            MilestoneType.WinDiplomaticVote -> Victory.Focus.CityStates
            MilestoneType.ScoreAfterTimeOut -> Victory.Focus.Score
            MilestoneType.WorldReligion -> Victory.Focus.Faith
        }
    }
}
