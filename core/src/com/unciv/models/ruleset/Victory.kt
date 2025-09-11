package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.stats.Stat
import com.unciv.models.stats.INamed
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly


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
    MoreCountableThanEachPlayer("Have more [countable] than each player's [countable]"),
}

class Victory : INamed, ICivilopediaText {

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

    @Readonly fun enablesMaxTurns(): Boolean = milestoneObjects.any { it.type == MilestoneType.ScoreAfterTimeOut }

    override var civilopediaText = listOf<FormattedLine>()
    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        return listOf(
            FormattedLine(victoryScreenHeader.lines().joinToString(" ")), // Remove newlines
            FormattedLine(extraImage="VictoryIllustrations/$name/Won", centered = true),
            FormattedLine(),
        ) + milestoneObjects.map { it.getFormattedLine() }
    }
    override fun makeLink() = "Victory/$name"
}

class Milestone(val uniqueDescription: String, private val parentVictory: Victory) {

    val type: MilestoneType? = MilestoneType.entries.firstOrNull { uniqueDescription.getPlaceholderText() == it.text.getPlaceholderText() }
    val params = uniqueDescription.getPlaceholderParameters()

    @Readonly
    private fun getIncompleteSpaceshipParts(civInfo: Civilization): Counter<String> {
        @LocalState val incompleteSpaceshipParts = parentVictory.requiredSpaceshipPartsAsCounter.clone()
        incompleteSpaceshipParts.remove(civInfo.victoryManager.currentsSpaceshipParts)
        return incompleteSpaceshipParts
    }

    @Readonly
    private fun originalMajorCapitalsOwned(civInfo: Civilization): Int = civInfo.cities
        .count { it.isOriginalCapital && it.foundingCiv != "" && civInfo.gameInfo.getCivilization(it.foundingCiv).isMajorCiv() }

    @Readonly
    private fun civsWithPotentialCapitalsToOwn(gameInfo: GameInfo): Set<Civilization> {
        // Capitals that still exist, even if the civ is dead
        val civsWithCapitals = gameInfo.getCities().filter { it.isOriginalCapital }
            .map { gameInfo.getCivilization(it.foundingCiv) }
            .filter { it.isMajorCiv() }.toSet()
        // If the civ is alive, they can still create a capital, so we need them as well
        val livingCivs = gameInfo.civilizations.filter { it.isMajorCiv() && !it.isDefeated() }
        return civsWithCapitals.union(livingCivs)
    }

    /**
     * Gets the percentage progress of one civilization's countable against another's countable.
     *
     * @return The progress percentage (0-100). When the result is >100, it qualifies as "more than".
     * @see getMoreCountableThanOtherCivRelevent()
     */
    @Readonly
    fun getMoreCountableThanOtherCivPercent(civ: Civilization, otherCiv: Civilization): Float {
        val countable1 = Countables.getCountableAmount(params[0], GameContext(civ)) ?: 0
        val countable2 = Countables.getCountableAmount(params[1], GameContext(otherCiv)) ?: 0
        return if (countable2 <= 0) { // Protect against zero division and negative resulting percent
            if (countable1 > countable2) 100.1f else 0f // Extra .1 so that it qualifies as >100%
        } else {
            countable1.toFloat() / countable2.toFloat() * 100f
        }
    }

    /**
     * Determines whether or not the given Civilization is relevent for the More Countable Victory Type.
     *
     * @see getMoreCountableThanOtherCivPercent()
     */
    @Readonly
    fun getMoreCountableThanOtherCivRelevent(civ: Civilization, otherCiv: Civilization): Boolean =
        civ != otherCiv && otherCiv.isMajorCiv() && otherCiv.isAlive()

    @Readonly
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
                originalMajorCapitalsOwned(civInfo) == civsWithPotentialCapitalsToOwn(civInfo.gameInfo).size
            MilestoneType.CompletePolicyBranches ->
                civInfo.policies.completedBranches.size >= params[0].toInt()
            MilestoneType.MoreCountableThanEachPlayer ->
                civInfo.gameInfo.civilizations.filter {
                    getMoreCountableThanOtherCivRelevent(civInfo, it) &&
                    getMoreCountableThanOtherCivPercent(civInfo, it) > 100f
                }.isNotEmpty()
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

    // Todo remove from here, this is models, not UI
    private fun getMilestoneButton(text: String, achieved: Boolean): TextButton {
        val textButton = text.toTextButton(hideIcons = true)
        if (achieved) textButton.color = Color.GREEN
        else textButton.color = Color.GRAY
        return textButton
    }

    @Readonly
    fun getVictoryScreenButtonHeaderText(completed: Boolean, civInfo: Civilization): String {
        return when (type!!) {
            MilestoneType.BuildingBuiltGlobally, MilestoneType.WinDiplomaticVote,
            MilestoneType.ScoreAfterTimeOut, MilestoneType.BuiltBuilding ->
                uniqueDescription
            MilestoneType.CompletePolicyBranches -> {
                val amountToDo = params[0].tr()
                val amountDone =
                    if (completed) amountToDo
                    else civInfo.getCompletedPolicyBranchesCount().tr()
                "{$uniqueDescription} (${amountDone}/${amountToDo})"
            }
            MilestoneType.CaptureAllCapitals -> {
                val amountToDo = civsWithPotentialCapitalsToOwn(civInfo.gameInfo).size
                val amountDone =
                    if (completed) amountToDo
                    else originalMajorCapitalsOwned(civInfo)
                if (civInfo.shouldHideCivCount())
                    "{$uniqueDescription} (${amountDone.tr()}/?)"
                else
                    "{$uniqueDescription} (${amountDone.tr()}/${amountToDo.tr()})"
            }
            MilestoneType.DestroyAllPlayers -> {
                val amountToDo = civInfo.gameInfo.civilizations.count { it.isMajorCiv() } - 1  // Don't count yourself
                val amountDone =
                    if (completed) amountToDo
                    else amountToDo - (civInfo.gameInfo.getAliveMajorCivs().count { it != civInfo })
                if (civInfo.shouldHideCivCount())
                    "{$uniqueDescription} (${amountDone.tr()}/?)"
                else
                    "{$uniqueDescription} (${amountDone.tr()}/${amountToDo.tr()})"
            }
            MilestoneType.MoreCountableThanEachPlayer -> {
                var amountToDo = 0; var amountDone = 0;
                for (otherCiv in civInfo.gameInfo.civilizations) {
                    if (!getMoreCountableThanOtherCivRelevent(civInfo, otherCiv)) continue
                    amountToDo++
                    if (getMoreCountableThanOtherCivPercent(civInfo, otherCiv) > 100f) amountDone++
                }
                if (civInfo.shouldHideCivCount())
                    "{$uniqueDescription} (${amountDone.tr()}/?)"
                else
                    "{$uniqueDescription} (${amountDone.tr()}/${amountToDo.tr()})"
            }
            MilestoneType.AddedSSPartsInCapital -> {
                val completeSpaceshipParts = civInfo.victoryManager.currentsSpaceshipParts
                @LocalState val incompleteSpaceshipParts = parentVictory.requiredSpaceshipPartsAsCounter.clone()
                val amountToDo = incompleteSpaceshipParts.sumValues()
                incompleteSpaceshipParts.remove(completeSpaceshipParts)

                val amountDone = amountToDo - incompleteSpaceshipParts.sumValues()

                "{$uniqueDescription} (${amountDone.tr()}/${amountToDo.tr()})"
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
                "{$uniqueDescription} (${amountDone.tr()}/${amountToDo.tr()})"
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
                val hideCivCount = civInfo.shouldHideCivCount()
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
                val hideCivCount = civInfo.shouldHideCivCount()
                val majorCivs = civInfo.gameInfo.civilizations.filter { it.isMajorCiv() }
                val originalCapitals = civInfo.gameInfo.getCities().filter { it.isOriginalCapital }
                    .associateBy { it.foundingCiv }
                for (civ in majorCivs) {
                    val city = originalCapitals[civ.civName]
                    if (city != null) {
                        val isKnown = civInfo.hasExplored(city.getCenterTile())
                        if (hideCivCount && !isKnown) continue
                        val milestoneText =
                            if (isKnown) "Capture [${city.name}]"
                            else "Capture [${Constants.unknownCityName}]"
                        buttons.add(getMilestoneButton(milestoneText, city.civ == civInfo))
                    }
                    else {
                        val milestoneText =
                            if (civInfo.knows(civ) || civ.isDefeated()) "Destroy [${civ.civName}]"
                            else "Destroy [${Constants.unknownNationName}]"
                        buttons.add(getMilestoneButton(milestoneText, civ.isDefeated()))
                    }
                }
                if (hideCivCount) buttons.add(getMilestoneButton("Capture ? * [${Constants.unknownCityName}]", false))
            }

            MilestoneType.CompletePolicyBranches -> {
                for (branch in civInfo.gameInfo.ruleset.policyBranches.values) {
                    val finisher = branch.policies.last().name
                    buttons.add(getMilestoneButton(finisher, civInfo.policies.isAdopted(finisher)))
                }
            }

            MilestoneType.MoreCountableThanEachPlayer -> {
                val hideCivCount = civInfo.shouldHideCivCount()
                for (otherCiv in civInfo.gameInfo.civilizations) {
                    if (!getMoreCountableThanOtherCivRelevent(civInfo, otherCiv)) continue
                    if (hideCivCount && !civInfo.knows(otherCiv)) continue
                    val civName = if (civInfo.knows(otherCiv)) otherCiv.civName else Constants.unknownNationName
                    val percent = getMoreCountableThanOtherCivPercent(civInfo, otherCiv)
                    // Hide the percent if it's zero, or double. Similar to "Dominant" cultural influence in BNW.
                    val milestoneText = if (percent < 1f || percent >= 200f) "[${civName}]" else "[${civName}] [${percent.toInt()}]%"
                    buttons.add(getMilestoneButton(milestoneText, percent > 100f))
                }
                if (hideCivCount) buttons.add(getMilestoneButton("[${Constants.unknownNationName}]", false))
            }

            MilestoneType.WorldReligion -> {
                val hideCivCount = civInfo.shouldHideCivCount()
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
            MilestoneType.MoreCountableThanEachPlayer -> {
                // Attempt to interpret the focus from the Countable type
                when (Countables.getMatching(params[0], ruleset)) {
                    Countables.Stats -> when (Stat.safeValueOf(params[0])) {
                        Stat.Production -> Victory.Focus.Production
                        Stat.Food -> Victory.Focus.Production
                        Stat.Gold -> Victory.Focus.Gold
                        Stat.Science -> Victory.Focus.Science
                        Stat.Culture -> Victory.Focus.Culture
                        Stat.Happiness -> Victory.Focus.Gold
                        Stat.Faith -> Victory.Focus.Faith
                        else -> Victory.Focus.Production
                    }
                    Countables.Cities, Countables.FilteredCities, Countables.FilteredBuildings, Countables.OwnedTiles -> Victory.Focus.Production
                    Countables.Units, Countables.FilteredUnits -> Victory.Focus.Military
                    Countables.PolicyBranches, Countables.FilteredPolicies -> Victory.Focus.Culture
                    Countables.TileResources, Countables.TileFilterTiles -> Victory.Focus.Production
                    else -> Victory.Focus.Score
                }
            }
            MilestoneType.WinDiplomaticVote -> Victory.Focus.CityStates
            MilestoneType.ScoreAfterTimeOut -> Victory.Focus.Score
            MilestoneType.WorldReligion -> Victory.Focus.Faith
        }
    }

    @Readonly fun getFormattedLine(): FormattedLine = when (type!!) {
        // TODO: Links should be `Building/params[0]`, but then the Wonder links don't resolve correctly
        MilestoneType.BuiltBuilding -> FormattedLine(uniqueDescription, link = "Wonder/${params[0]}")
        MilestoneType.BuildingBuiltGlobally -> FormattedLine(uniqueDescription, link = "Wonder/${params[0]}")
        MilestoneType.WorldReligion -> FormattedLine(uniqueDescription, link = "Tutorials/Religion")
        MilestoneType.CompletePolicyBranches -> FormattedLine(uniqueDescription, link = "Policies")
        else -> FormattedLine(uniqueDescription, starred = true)
    }
}
