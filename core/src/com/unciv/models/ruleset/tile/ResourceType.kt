package com.unciv.models.ruleset.tile

import com.badlogic.gdx.graphics.Color

enum class ResourceType(val color: String) {
    Luxury("#ffd800"),
    Strategic("#c14d00"),
    Bonus("#a8c3c9");

    fun getColor() : Color {
        return Color.valueOf(color)
    }
}
