package com.unciv.models.gamebasics

class PolicyBranch : Policy() {
    var policies: ArrayList<Policy> = arrayListOf()
    lateinit var era:TechEra
}
