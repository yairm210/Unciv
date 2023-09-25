package com.unciv.models.ruleset

import com.unciv.models.Counter
import com.unciv.models.stats.NamedStats
import com.unciv.ui.components.extensions.colorFromRGB

class Specialist: NamedStats() {
    var color = ArrayList<Int>()
    val colorObject by lazy { colorFromRGB(color) }
    var greatPersonPoints = Counter<String>()
}
