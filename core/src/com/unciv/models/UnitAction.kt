package com.unciv.models

data class UnitAction(
        val type: UnitActionType,
        val title: String = type.value,
        val isCurrentAction: Boolean = false,
        val uncivSound: UncivSound = UncivSound.Click,
        val action: (() -> Unit)? = null
)

enum class UnitActionType(val value: String) {
    SwapUnits("Swap units"),
    Automate("Automate"),
    StopAutomation("Stop automation"),
    StopMovement("Stop movement"),
    Sleep("Sleep"),
    SleepUntilHealed("Sleep until healed"),
    Fortify("Fortify"),
    FortifyUntilHealed("Fortify until healed"),
    Explore("Explore"),
    StopExploration("Stop exploration"),
    Promote("Promote"),
    Upgrade("Upgrade"),
    Pillage("Pillage"),
    Paradrop("Paradrop"),
    SetUp("Set up"),
    FoundCity("Found city"),
    ConstructImprovement("Construct improvement"),
    // Deprecated since 3.15.4
        ConstructRoad("Construct road"),
    //
    Create("Create"),
    SpreadReligion("Spread Religion"),
    HurryResearch("Hurry Research"),
    StartGoldenAge("Start Golden Age"),
    HurryWonder("Hurry Wonder"),
    ConductTradeMission("Conduct Trade Mission"),
    FoundReligion("Found a Religion"),
    DisbandUnit("Disband unit")
}