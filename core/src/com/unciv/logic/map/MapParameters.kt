package com.unciv.logic.map

enum class MapSize(val radius: Int) {
    Tiny(10),
    Small(15),
    Medium(20),
    Large(30),
    Huge(40)
}

class MapParameters {
    var name = ""
    var type = MapType.pangaea
    var size: MapSize = MapSize.Medium
    var noRuins = false
    var noNaturalWonders = true
}