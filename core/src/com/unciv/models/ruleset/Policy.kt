package com.unciv.models.ruleset

import com.unciv.models.stats.INamed

open class Policy : INamed, IHasUniques {
    lateinit var branch: PolicyBranch // not in json - added in gameBasics

    override lateinit var name: String
    override var uniques: ArrayList<String> = ArrayList()
    override val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
    var row: Int = 0
    var column: Int = 0
    var requires: ArrayList<String>? = null

    override fun toString() = name
}

