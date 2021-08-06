package com.unciv.logic.civilization

import com.unciv.models.translations.fillPlaceholders

enum class CityStateType(
    val friendBonusText: String
) {
    Cultured ("Provides [amount] culture at 30 Influence"),
    Maritime ("Provides 3 food in capital and 1 food in other cities at 30 Influence"),
    Mercantile ("Provides 3 happiness at 30 Influence"),
    Militaristic ("Provides land units every 20 turns at 30 Influence"),
    ;
    fun getBonusText(viewingCiv: CivilizationInfo) = when(this) {
        Cultured -> friendBonusText.fillPlaceholders((3 * (viewingCiv.getEraNumber() + 1)).toString())
        else -> friendBonusText
    }
}

enum class CityStatePersonality {
    Friendly,
    Neutral,
    Hostile,
    Irrational
}