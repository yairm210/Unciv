package com.unciv.models.ruleset

import com.unciv.models.stats.INamed
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.tr


enum class MilestoneType(text: String) {
    BuiltBuilding("Built [building]"),
    AddedUnitInCapital("Add [amount] [unit] units in capital"),
    DestroyAllPlayers("Destroy all players"),
    CaptureAllCapitals("Capture all capitals"),
    CompletePolicyBranches("Complete [amount] policy branches")
}


class Milestone(private val uniqueDescription: String) {

    val type: MilestoneType = MilestoneType.valueOf(uniqueDescription)
    val params by lazy { uniqueDescription.getPlaceholderParameters() }

    fun getDisplayString() = uniqueDescription.tr()
    fun hasBeenCompletedBy(civInfo: CivilizationInfo): Boolean {
        return when (type) {
            MilestoneType.BuiltBuilding ->
                civInfo.cities.any { it.cityConstructions.builtBuildings.contains(params[0])}
            MilestoneType.AddedUnitInCapital ->
                civInfo.victoryManager.currentsSpaceshipParts[params[1]]!! >= params[0].toInt()
            MilestoneType.DestroyAllPlayers ->
                civInfo.gameInfo.getAliveMajorCivs() == listOf(civInfo)
            MilestoneType.CaptureAllCapitals ->
                civInfo.cities.count {
                    it.isOriginalCapital
                    && civInfo.gameInfo.getCivilization(it.foundingCiv).isMajorCiv()
                } == civInfo.gameInfo.civilizations.count { it.isMajorCiv() }
            MilestoneType.CompletePolicyBranches ->
                civInfo.policies.completedBranches.size >= params[0].toInt()
        }
    }
}


class Victory : INamed {
    override var name = ""
    val victoryScreenHeader = "Do things to win!"
    val hiddenInVictoryScreen = false
    // Things to do to win
    val milestones = ArrayList<String>()
    val milestoneObjects by lazy { milestones.map { Milestone(it) }}
}




enum class VictoryType {
    Neutral,
    Cultural,
    Diplomatic,
    Domination,
    Scientific,
    Time,
}