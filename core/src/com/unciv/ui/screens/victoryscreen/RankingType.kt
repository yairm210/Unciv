package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.ui.images.ImageGetter

enum class RankingType(val getImage: () -> Image?, val idForSerialization: String) {
    // production, gold, happiness, and culture already have icons added when the line is `tr()`anslated
    Score(
        { ImageGetter.getImage("CityStateIcons/Cultured").apply { color = Color.FIREBRICK } },
        "S"
    ),
    Population({ ImageGetter.getStatIcon("Population") }, "N"),
    Crop_Yield({ ImageGetter.getStatIcon("Food") }, "C"),
    Production({ null }, "P"),
    Gold({ null }, "G"),
    Territory({ ImageGetter.getImage("OtherIcons/Hexagon") }, "T"),
    Force({ ImageGetter.getImage("OtherIcons/Shield") }, "F"),
    Happiness({ null }, "H"),
    Technologies({ ImageGetter.getStatIcon("Science") }, "W"),
    Culture({ null }, "A");

    companion object {
        fun fromIdForSerialization(s: String): RankingType? =
                values().firstOrNull { it.idForSerialization == s }
    }
}
