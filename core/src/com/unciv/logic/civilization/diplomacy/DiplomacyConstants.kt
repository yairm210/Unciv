package com.unciv.logic.civilization.diplomacy

object DiplomacyConstants {
    const val DENOUNCE_PENALTY = -35f
    const val DENOUNCE_TURNS = 30

    // Major Civ section
    const val UNFORGIVABLE_OPINION_THRESHOLD = -80
    const val ENEMY_OPINION_THRESHOLD = -40
    const val COMPETITOR_OPINION_THRESHOLD = -15
    const val ALLY_OPINION_THRESHOLD = 80
    const val FRIEND_OPINION_THRESHOLD = 40
    const val FAVORABLE_OPINION_THRESHOLD = 15

    // City state section
    const val MINIMUM_CITY_STATE_INFLUENCE = -60f
    const val FRIEND_INFLUENCE_THRESHOLD = 30
    const val ALLY_INFLUENCE_MIN_THRESHOLD = 60
    const val UNFORGIVABLE_INFLUENCE_THRESHOLD = -30

    const val BASE_NATURAL_INFLUENCE_CHANGE = 1f
    const val BASE_HOSTILE_INFLUENCE_DEGRADATION = 1.5f
    const val BASE_MINOR_AGGRESSOR_INFLUENCE_DEGRADATION = 2f
    const val RELIGION_BONUS_INFLUENCE_DEGRADATION = 25f
    const val RELIGION_BONUS_INFLUENCE_RECOVERY = 50f


}
