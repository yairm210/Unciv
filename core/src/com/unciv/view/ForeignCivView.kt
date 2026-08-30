package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ImmutableColor
import com.unciv.models.ruleset.tile.ResourceSupplyList
import yairm210.purity.annotations.Readonly

/** Should contain information that should be knowable to us about foreign civilizations. Superclass of [CivView]. */
open class ForeignCivView(protected open val civ: Civilization, viewer: Civilization, spectatorMode: Boolean = false) : GameBasedView<Civilization>(civ, viewer, spectatorMode) {
    val civName: String get() = civ.civName
    val gold: Int get() = civ.gold
    val ruleset = civ.gameInfo.ruleset

    // Navigation
    // TEMP - should be removed once migration ends
    @JvmName("getCivPublic") @Readonly fun getCiv(): Civilization = civ

    // Data retrieval
    @Readonly fun getOuterColor(): ImmutableColor = civ.nation.getOuterColor()
    @Readonly fun getInnerColor(): ImmutableColor = civ.nation.getInnerColor()

    @Readonly fun getEraName(): String = civ.getEra().name
    @Readonly fun getStyle(): String = civ.nation.style
    @Readonly fun getStyleOrCivName(): String = civ.nation.getStyleOrCivName()
    @Readonly fun getEraNumber(): Int = civ.getEraNumber()
    @Readonly fun getEraNameAt(index: Int): String = civ.gameInfo.ruleset.eras.keys.elementAt(index)

    @Readonly fun isAtWarWith(other: ForeignCivView): Boolean = civ.isAtWarWith(other.civ)

    @Readonly fun getGoldPerTurn(): Int = civ.stats.statsForNextTurn.gold.toInt()
    @Readonly fun getPerTurnResourcesWithOriginsForTrade(): ResourceSupplyList = civ.getPerTurnResourcesWithOriginsForTrade()
    @Readonly fun getResearchAgreementCost(other: ForeignCivView): Int = civ.diplomacyFunctions.getResearchAgreementCost(other.civ)
}
