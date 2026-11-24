package com.unciv.logic

import com.unciv.UncivGame
import com.unciv.models.translations.tr
import yairm210.purity.annotations.Pure

/**
 *  Wrapper for a release version.
 *
 *  - An instance holding current values is created as [UncivGame.VERSION] via a workflow editing source.
 *  - Formatter: [toNiceString] - localized
 *
 *  @property text corresponds to BuildConfig.appVersion
 *  @property number corresponds to BuildConfig.appCodeNumber
 */
data class Version(
    val text: String,
    val number: Int
) : IsPartOfGameInfoSerialization {
    @Suppress("unused") // used by json serialization
    internal constructor() : this("", -1)

    @Pure fun toNiceString() = "[$text] (Build [$number])".tr()
    @Pure fun toSerializeString() = "$text (Build $number)"
}

interface HasGameInfoSerializationVersion {
    val version: CompatibilityVersion
}

data class CompatibilityVersion(
    /** Minimum save format the reader must support to play this game. */
    val number: Int,
    /** This is the version that saved the game, not the one that did the "new game". */
    val createdWith: Version
) : IsPartOfGameInfoSerialization, Comparable<CompatibilityVersion> {
    @Suppress("unused") // used by json serialization
    internal constructor() : this(-1, Version())

    @Pure
    override operator fun compareTo(other: CompatibilityVersion) = number.compareTo(other.number)

    companion object {
        /** Raise the base format for incompatible changes that apply to every game. */
        private val baseVersion = CompatibilityVersion(4, UncivGame.VERSION)
        private const val CITY_GREAT_PERSON_POINTS_FORMAT = 5
        val CURRENT_COMPATIBILITY_VERSION = CompatibilityVersion(maxOf(baseVersion.number, CITY_GREAT_PERSON_POINTS_FORMAT), UncivGame.VERSION)

        fun forGame(game: GameInfo) =
            if (game.greatPersonPointsInCities) CURRENT_COMPATIBILITY_VERSION else baseVersion

        /** This is the version just before this field was introduced, i.e. all saves without any version will be from this version */
        val FIRST_WITHOUT = CompatibilityVersion(1, Version("4.1.14", 731))
    }
}

/** Class to use when parsing a saved game json if you only want the serialization [version]. */
class GameInfoSerializationVersion : HasGameInfoSerializationVersion {
    override var version = CompatibilityVersion.FIRST_WITHOUT
}
