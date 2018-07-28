package com.unciv.models.gamebasics

import com.unciv.models.gamebasics.tech.TechEra

class PolicyBranch : Policy() {
    var policies: ArrayList<Policy> = arrayListOf()
    lateinit var era: TechEra
}
