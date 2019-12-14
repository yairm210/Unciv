package com.unciv.models.ruleset.tile

import com.unciv.models.ruleset.tr
import com.unciv.models.stats.NamedStats
import java.util.*

class NaturalWonder : NamedStats() {
    fun getDescription(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.appendln(this.clone().toString())
        val terrainsCanBeBuiltOnString: ArrayList<String> = arrayListOf()
        for (i in terrainsCanBeFoundOn) {
            terrainsCanBeBuiltOnString.add(i.tr())
        }
        stringBuilder.appendln("Can be found on ".tr() + terrainsCanBeBuiltOnString.joinToString(", "))
        return stringBuilder.toString()
    }

    var size: Int = 1
    var terrainsCanBeFoundOn: List<String> = listOf()
}