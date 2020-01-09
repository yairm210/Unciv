package com.unciv.models

data class UnitAction(
        var type: UnitActionType,
        var canAct: Boolean,
        var title: String = type.value,
        var isCurrentAction: Boolean = false,
        var uncivSound: UncivSound = UncivSound.Click,
        var action: (() -> Unit)? = null
)

enum class UnitActionType(val value: String) {
    Automate("Automate"),
    StopMovement("Stop movement"),
    StopAutomation("Stop automation"),
    StopExploration("Stop exploration"),
    Sleep("Sleep"),
    Fortify("Fortify"),
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