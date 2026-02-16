package com.unciv.models.metadata

import com.unciv.utils.JsonSerialized

@Suppress("EnumEntryName")  // These merit unusual names
@JsonSerialized
enum class BaseRuleset(val fullName: String) {
    Civ_V_Vanilla("Civ V - Vanilla"),
    Civ_V_GnK("Civ V - Gods & Kings"),
}
