package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.ui.images.ImageGetter

enum class RankingType(
    label: String?,
    val getImage: () -> Image?,
    val idForSerialization: Char
) {
    // production, gold, happiness, and culture already have icons added when the line is `tr()`anslated
    Score({ ImageGetter.getImage("OtherIcons/Score").apply { color = Color.FIREBRICK } }, 'S'),
    Population({ ImageGetter.getStatIcon("Population") }, 'N'),
    CropYield("Crop Yield", { ImageGetter.getStatIcon("Food") }, 'C'),
    Production('P'),
    Gold('G'),
    Territory({ ImageGetter.getImage("OtherIcons/Hexagon") }, 'T'),
    Force({ ImageGetter.getImage("OtherIcons/Shield") }, 'F'),
    Happiness('H'),
    Technologies({ ImageGetter.getStatIcon("Science") }, 'W'),
    Culture('A'),
    ;
    val label = label ?: name
    constructor(getImage: () -> Image?, idForSerialization: Char) : this(null, getImage, idForSerialization)
    constructor(idForSerialization: Char) : this(null, { null }, idForSerialization)

    companion object {
        fun fromIdForSerialization(char: Char): RankingType? =
                entries.firstOrNull { it.idForSerialization == char }
    }
}
