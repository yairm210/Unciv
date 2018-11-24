package com.unciv.models.gamebasics

import com.unciv.models.stats.INamed

open class Policy : INamed {
    override lateinit var name: String
    lateinit var description: String
    var branch: String? = null
    var row: Int = 0
    var column: Int = 0
    var requires: ArrayList<String>? = null

    fun getBranch():PolicyBranch{
        return GameBasics.PolicyBranches[branch]!!
    }

    override fun toString(): String {
        return name
    }
}

