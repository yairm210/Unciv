package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.models.metadata.GameParameters
import com.unciv.ui.images.ImageGetter
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

enum class RankingType(
    label: String?,
    val getImage: () -> Image?,
    val idForSerialization: Char,
    val isVanilla: Boolean = true
) {
    // production, gold, happiness, and culture already have icons added when the line is `tr()`anslated
    Score({ ImageGetter.getImage("OtherIcons/Score").apply { color = Color.FIREBRICK } }, 'S'),
    Population({ ImageGetter.getStatIcon("Population") }, 'N'),
    Growth("Growth", { ImageGetter.getStatIcon("Food") }, 'C'),
    Production('P'),
    Gold('G'),
    Territory({ ImageGetter.getImage("OtherIcons/Hexagon") }, 'T'),
    Force({ ImageGetter.getImage("OtherIcons/Shield") }, 'F'),
    Happiness('H'),
    Technologies({ ImageGetter.getStatIcon("Science") }, 'W'),
    Culture('A'),
    // Non vanilla ranking types
    TilesExplored("Tiles Explored", { ImageGetter.getImage("UnitPromotionIcons/Scouting") }, 'E', false),
    ;
    val label = label ?: name
    constructor(getImage: () -> Image?, idForSerialization: Char) : this(null, getImage, idForSerialization)
    constructor(idForSerialization: Char) : this(null, { null }, idForSerialization)

    companion object {
        @Pure fun fromIdForSerialization(char: Char): RankingType? =
                entries.firstOrNull { it.idForSerialization == char }
        @Readonly fun filteredEntries(gameParameters: GameParameters) =
                entries.filter { it.isVanilla || gameParameters.showAdditionalRankingTypes }
    }
}
