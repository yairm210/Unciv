package com.unciv.models.gamebasics

import com.unciv.models.stats.INamed

open class Policy : INamed {
    lateinit var branch: PolicyBranch // not in json - added in gameBasics

    override lateinit var name: String
    lateinit var description: String
    var row: Int = 0
    var column: Int = 0
    var requires: ArrayList<String>? = null

    override fun toString(): String {
        return name
    }
}

