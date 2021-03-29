package com.unciv.models.ruleset

import com.unciv.models.stats.INamed
import java.util.ArrayList

class Belief:INamed {
    override var name:String=""
    var type:String=""
    var uniques = ArrayList<String>()
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
}
