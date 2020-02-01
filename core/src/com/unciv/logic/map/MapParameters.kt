package com.unciv.logic.map

enum class MapSize(val radius: Int) {
    Tiny(10),
    Small(15),
    Medium(20),
    Large(30),
    Huge(40)
}

enum class ResourceLevel(val percent: Float) {
    Low(0.5f),
    Medium(1f),
    High(1.5f)
}

class MapParameters {
    var name = ""
    var type = MapType.pangaea
    var resourceLevel = ResourceLevel.Medium
    var size: MapSize = MapSize.Medium
    var noRuins = false
    var noNaturalWonders = true
}