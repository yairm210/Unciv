package com.unciv.models.ruleset

import com.unciv.models.stats.INamed

class Victory : INamed {
    override var name = ""
    val victoryScreenHeader = "Do things to win!"
    val hiddenInVictoryScreen = false
    // Things to do to win
    val milestones = ArrayList<String>()
}

enum class VictoryType {
    Neutral,
    Cultural,
    Diplomatic,
    Domination,
    Scientific,
    Time,
}