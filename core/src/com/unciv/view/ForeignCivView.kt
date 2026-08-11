package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ImmutableColor
import yairm210.purity.annotations.Readonly

/** Should contain information that should be knowable to us about foreign civilizations. Superclass of [CivView]. */
open class ForeignCivView(internal open val civ: Civilization, internal open val viewer: Civilization, val spectatorMode: Boolean = false) {
    val civName: String get() = civ.civName

    // Navigation
    @Readonly fun getCiv(): Civilization = civ
    @Readonly fun getViewer(): Civilization = viewer

    // Data retrieval
    @Readonly fun getOuterColor(): ImmutableColor = civ.nation.getOuterColor()
    @Readonly fun getInnerColor(): ImmutableColor = civ.nation.getInnerColor()

    @Readonly fun getEraName(): String = civ.getEra().name
    @Readonly fun getStyle(): String = civ.nation.style
    @Readonly fun getStyleOrCivName(): String = civ.nation.getStyleOrCivName()
    @Readonly fun getEraNumber(): Int = civ.getEraNumber()
    @Readonly fun getEraNameAt(index: Int): String = civ.gameInfo.ruleset.eras.keys.elementAt(index)

    @Readonly fun isAtWarWith(other: ForeignCivView): Boolean = civ.isAtWarWith(other.civ)
}
