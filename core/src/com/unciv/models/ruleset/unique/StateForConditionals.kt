package com.unciv.models.ruleset.unique

import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo

data class StateForConditionals(
    val civInfo: CivilizationInfo? = null,
    val cityInfo: CityInfo? = null,
)