package com.unciv.ui.civilopedia

/** Enum used as keys for Civilopedia "pages" (categories).
 *
 *  Note names are singular on purpose - a "link" allows both key and label
 *
 * @param label Translatable caption for the Civilopedia button
 */
enum class CivilopediaCategories (val label: String) {
    Building ("Buildings"),
    Wonder ("Wonders"),
    Resource ("Resources"),
    Terrain ("Terrains"),
    Improvement ("Tile Improvements"),
    Unit ("Units"),
    Nation ("Nations"),
    Technology ("Technologies"),
    Promotion ("Promotions"),
    Tutorial ("Tutorials"),
    Difficulty ("Difficulty levels"),
    Policy ("Policies");                // Omitted on CivilopediaScreen

    companion object {
        fun fromLink(name: String): CivilopediaCategories? =
            values().firstOrNull { it.name == name }
            ?: values().firstOrNull { it.label == name }
    }
}
