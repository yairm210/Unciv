package com.unciv.logic

import com.unciv.UncivGame
import com.unciv.models.translations.tr
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

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
    /** Contains the current serialization version of [GameInfo], i.e. when this number is not equal to [CURRENT_COMPATIBILITY_NUMBER], it means
     * this instance has been loaded from a save file json that was made with another version of the game. */
    val number: Int,
    /** This is the version that saved the game, not the one that did the "new game". */
    val createdWith: Version
) : IsPartOfGameInfoSerialization, Comparable<CompatibilityVersion> {
    @Suppress("unused") // used by json serialization
    internal constructor() : this(-1, Version())

    @Pure
    override operator fun compareTo(other: CompatibilityVersion) = number.compareTo(other.number)

    companion object {
        /** The current compatibility version of [GameInfo]. This number is incremented whenever changes are made to the save file structure that guarantee that
         * previous versions of the game will not be able to load or play a game normally. */
        const val CURRENT_COMPATIBILITY_NUMBER = 4

        val CURRENT_COMPATIBILITY_VERSION = CompatibilityVersion(CURRENT_COMPATIBILITY_NUMBER, UncivGame.Companion.VERSION)

        /** This is the version just before this field was introduced, i.e. all saves without any version will be from this version */
        val FIRST_WITHOUT = CompatibilityVersion(1, Version("4.1.14", 731))
    }
}

/** Class to use when parsing a saved game json if you only want the serialization [version]. */
class GameInfoSerializationVersion : HasGameInfoSerializationVersion {
    override var version = CompatibilityVersion.FIRST_WITHOUT
}
