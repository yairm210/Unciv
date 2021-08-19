package com.unciv.models.ruleset

import com.unciv.models.stats.INamed

class RuinReward : INamed {
    override lateinit var name: String
    val notification: String = ""
    val uniques: List<String> = listOf()
    @delegate:Transient     // Defense in depth against mad modders
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
    val excludedDifficulties: List<String> = listOf()
    val weight: Int = 1
}
