package com.unciv.logic.map

import com.unciv.logic.IsPartOfGameInfoSerialization


/**
 *  Encapsulates the "map size" concept, without also choosing a shape.
 *
 *  Predefined sizes are kept in the [Predefined] enum, instances derived from these have the same [name] and copied dimensions.
 *  Custom sizes always have [custom] as [name], even if created with the exact same dimensions as a [Predefined].
 *
 *  @property name
 *  @property radius
 *  @property width
 *  @property height
 *  @see MapShape
 */
/*
 *  The architecture is not as elegant as I'd like - an interface implemented by both an enum and a "custom" subclass would be shorter and nicer to read,
 *  but the obstacle of Json deserialization has -for the moment- the heavier weight. Instance creation would have to be customized, and with the Gdx.Json
 *  model, that would mean the simpler Serializable interface won't do, needing the clunky setSerializer instead.
 */
class MapSize private constructor(
    val name: String,
    var radius: Int,
    var width: Int,
    var height: Int,
) : IsPartOfGameInfoSerialization {

    /** Needed for Json parsing */
    @Suppress("unused")
    private constructor() : this("", 0, 0, 0)

    constructor(size: Predefined) : this(size.name, size.radius, size.width, size.height)

    constructor(name: String) : this(Predefined.safeValueOf(name))

    constructor(radius: Int) : this(custom, radius, 0, 0) {
        setNewRadius(radius)
    }

    constructor(width: Int, height: Int) : this(custom, HexMath.getEquivalentHexagonalRadius(width, height), width, height)

    /** Predefined Map Sizes, their name can appear in json only as copy in MapSize */
    enum class Predefined(
        val radius: Int,
        val width: Int,
        val height: Int,
        // https://civilization.fandom.com/wiki/Map_(Civ5)
        val techCostMultiplier: Float = 1f,
        val techCostPerCityModifier: Float = 0.05f,
        val policyCostPerCityModifier: Float = 0.1f,
        val unHappinesPerCity: Float = 3f
    ) {
        Duel(16, 40, 24),
        Tiny(24, 56, 36),
        Small(28, 66, 42),
        Medium(34, 80, 52, 1.1f),
        Large(43, 104, 64, 1.2f, 0.03f,0.075f, 2.4f),
        Huge(54, 128, 80, 1.3f, 0.02f,0.05f, 1.8f);

        companion object {
            fun safeValueOf(name: String) = values().firstOrNull { it.name == name } ?: Duel
        }
    }

    companion object {
        /** Not a [Predefined] enum value, but a String
         * used in [name] to indicate user-defined dimensions.
         * Do not mistake for [MapGeneratedMainType.custom]. */
        const val custom = "Custom"
        val Tiny get() = MapSize(Predefined.Tiny)
        val Small get() = MapSize(Predefined.Small)
        val Medium get() = MapSize(Predefined.Medium)
        val Huge get() = MapSize(Predefined.Huge)
        fun names() = Predefined.values().map { it.name }
    }

    fun clone() = MapSize(name, radius, width, height)

    fun getPredefinedOrNextSmaller(): Predefined {
        if (name != custom) return Predefined.safeValueOf(name)
        for (predef in Predefined.values().reversed()) {
            if (radius >= predef.radius) return predef
        }
        return Predefined.Tiny
    }

    /** Check custom dimensions, fix if too extreme
     * @param worldWrap whether world wrap is on
     * @return null if size was acceptable, otherwise untranslated reason message
     */
    fun fixUndesiredSizes(worldWrap: Boolean): String? {
        if (name != custom) return null  // predefined sizes are OK
        // world-wrap mas must always have an even width, so round down silently
        if (worldWrap && width % 2 != 0 ) width--
        // check for any bad condition and bail if none of them
        val message = when {
            worldWrap && width < 32 ->    // otherwise horizontal scrolling will show edges, empirical
                "World wrap requires a minimum width of 32 tiles"
            width < 3 || height < 3 || radius < 2 ->
                "The provided map dimensions were too small"
            radius > 500 ->
                "The provided map dimensions were too big"
            height * 16 < width || width * 16 < height ->    // aspect ratio > 16:1
                "The provided map dimensions had an unacceptable aspect ratio"
            else -> null
        } ?: return null

        // fix the size - not knowing whether hexagonal or rectangular is used
        setNewRadius(when {
            radius < 2 -> 2
            radius > 500 -> 500
            worldWrap && radius < 15 -> 15    // minimum for hexagonal but more than required for rectangular
            else -> radius
        })

        // tell the caller that map dimensions have changed and why
        return message
    }

    private fun setNewRadius(radius: Int) {
        this.radius = radius
        val size = HexMath.getEquivalentRectangularSize(radius)
        width = size.x.toInt()
        height = size.y.toInt()
    }

    // For debugging and MapGenerator console output
    override fun toString() = if (name == custom) "${width}x${height}" else name
}
