package com.unciv.models.helpers

import com.badlogic.gdx.graphics.Color
import com.unciv.models.ruleset.Era
import com.unciv.models.ruleset.unit.BaseUnit

/** Base interface for classes the instances of which signify a distinctive type of look and feel with which to draw arrows on the map. */
sealed interface MapArrowType

/** Base interface for constant stylings of map arrows. All [MapArrowType] instances will either be this, or (either implicitly or explicitly) refer to one of these (E.G. while additionally selecting a unit or era variant or adding a tint). */
sealed interface BaseMapArrowType: MapArrowType {
    val name: String
}

/** Enum constants describing how/why a unit changed position. Each is also associated with an arrow type to draw on the map overlay. */
enum class UnitMovementMemoryType: BaseMapArrowType {
    UnitMoved,
    UnitAttacked, // For when attacked, killed, and moved into tile.
    UnitWithdrew, // Caravel, destroyer, etc.
    UnitTeleported, // Paradrop, open borders end, air rebase, etc.
}

/** Enum constants describing assorted commonly used arrow types. */
enum class MiscArrowTypes: BaseMapArrowType {
    Generic,
    UnitMoving,
    UnitHasAttacked, // For attacks that didn't result in moving into the target tile. E.G. Ranged, air strike, melee but the target survived, melee but not allowed in target terrain.
    CityHasAttacked,
}

/** Class for arrow types signifying that a base arrow type should be rendered in the style of a particular unit. */
data class UnitArrowType(val baseUnit: BaseUnit, val baseArrowType: BaseMapArrowType): MapArrowType

/** Class for arrow types signifying that a base arrow type should be rendered in the style of a city and era. */
data class CityArrowType(val era: Era?, val baseArrowType: BaseMapArrowType): MapArrowType

/** Class for arrow types signifying that a generic arrow style should be used and tinted.
 * @property color The colour that the arrow should be tinted. */
data class TintedMapArrow(val color: Color): MapArrowType
// Not currently used in core code, but allows one-off colour-coded arrows to be drawn without having to add a whole new texture and enum constant. Could be useful for debug— Visualize what your AI is doing, or which tiles are affecting resource placement or whatever. Also thinking of mod scripting.
