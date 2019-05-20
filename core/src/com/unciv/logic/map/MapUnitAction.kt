package com.unciv.logic.map

open class MapUnitAction(
        @Transient var unit: MapUnit = MapUnit(),
        var name: String = ""
)


class BuildLongRoadAction(
        mapUnit: MapUnit = MapUnit()
) : MapUnitAction(mapUnit, "Build Long Road") {



}