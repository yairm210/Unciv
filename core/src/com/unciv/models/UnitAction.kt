package com.unciv.models

data class UnitAction(
        val type: UnitActionType,
        val title: String = type.value,
        val isCurrentAction: Boolean = false,
        val uncivSound: UncivSound = UncivSound.Click,
        val action: (() -> Unit)? = null
)

enum class UnitActionType(val value: String) {
    Automate("Automate"),
    StopMovement("Stop movement"),
    StopAutomation("Stop automation"),
    StopExploration("Stop exploration"),
    Sleep("Sleep"),
    SleepUntilHealed("Sleep until healed"),
    Fortify("Fortify"),
    FortifyUntilHealed("Fortify until healed"),
    Explore("Explore"),
    Promote("Promote"),
    Upgrade("Upgrade"),
    Pillage("Pillage"),
    SetUp("Set up"),
    FoundCity("Found city"),
    ConstructImprovement("Construct improvement"),
    ConstructRoad("Construct road"),
    Create("Create"),
    HurryResearch("Hurry Research"),
    StartGoldenAge("Start Golden Age"),
    HurryWonder("Hurry Wonder"),
    ConductTradeMission("Conduct Trade Mission"),
    DisbandUnit("Disband unit")
}