package com.unciv.models

enum class CivilopediaCategories(private val _label: String? = null)
{
    Unselected (""),

    Buildings,
    Resources,
    Terrains,
    Improvements ("Tile Improvements"),
    Units,
    Nations,
    Technologies,
    Promotions,
    Tutorials,
    ;
    val label: String
        get() = _label ?: name
}