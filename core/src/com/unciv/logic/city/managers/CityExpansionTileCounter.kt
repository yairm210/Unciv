package com.unciv.logic.city.managers

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Readonly

/** This is just a Python-like NOP for silencing IDE complaints about empty blocks. Also a good target for breakpoints. */
private inline val pass: Unit get() = Unit

/**
 *  Counts tiles acquired per [source][OwnershipSource].
 *
 *  Previously, all costs were based on a count of owned tiles.
 *  But it's clear [(see issue #6394)](https://github.com/yairm210/Unciv/issues/6394) that:
 *  *   There's free Tiles: Shohone and Citadel should not increase price
 *  *   Culture prograssion is not altered by buying for gold
 *  *   Gold progression is not altered by culture expansion
 */
@Suppress("NOTHING_TO_INLINE") // Readability
class CityExpansionTileCounter : IsPartOfGameInfoSerialization, Json.Serializable {
    private enum class State { Migrating, Deserialized, Operational }

    private var state = State.Migrating
    private val tileCounts = IntArray(OwnershipSource.entries.size)

    @Transient
    private lateinit var city: City

    internal fun setTransients(city: City) {
        this.city = city
    }

    @Readonly
    private inline fun getUnchecked(source: OwnershipSource) = tileCounts[source.ordinal]
    @Readonly
    private inline fun getAll() = getUnchecked(OwnershipSource.All)
    private inline fun setUnchecked(source: OwnershipSource, value: Int) { tileCounts[source.ordinal] = value }
    private inline fun setAll(value: Int) = setUnchecked(OwnershipSource.All, value)

    @Readonly
    internal operator fun get(source: OwnershipSource): Int {
        require(state == State.Operational)
        return getUnchecked(source)
    }

    internal operator fun set(source: OwnershipSource, value: Int) {
        require(source != OwnershipSource.All && state == State.Operational)
        setUnchecked(source, value)
        setAll(tileCounts.sum() - getAll())
    }

    /** For use only by [CityExpansionManager.clone] */
    internal fun cloneFrom(other: CityExpansionTileCounter) {
        other.tileCounts.copyInto(tileCounts)
        state = other.state
    }

    /** Use only after clearing `[city].tiles` */
    internal fun reset() {
        tileCounts.fill(0)
        state = State.Operational
    }

    /** Reduces tile count, fixing [OwnershipSource.All].
     *  * Only necessary because we don't store source per-tile
     *  @param source If `null`, choose a source to deduct from, in favour of the player
     *  @throws IllegalStateException when state not [State.Operational] or count would become negative
     */
    internal fun relinquishOne(source: OwnershipSource?) {
        val source = when {
            // Where to deduct is passed by caller
            source != null -> source
            // since we don't store source per-tile (whether it was acquired free, or bought, or expanded) we deduct in favour of the player
            getUnchecked(OwnershipSource.Bought) > 0 -> OwnershipSource.Bought
            getUnchecked(OwnershipSource.Expansion) > 0 -> OwnershipSource.Expansion
            getUnchecked(OwnershipSource.Free) > 0 -> OwnershipSource.Free
            else -> OwnershipSource.Base
        }
        check(getUnchecked(source) > 0)
        set(source, getUnchecked(source))
    }

    /** Do necessary corrections to get to [State.Operational].
     *  This means deriving the values that are not serialized:
     *  * [OwnershipSource.Base] is derived from ruleset and tiles around [city]
     *  * [OwnershipSource.All] is set from `[city].tiles.size`
     *  * [OwnershipSource.Expansion] is set to make up the difference
     */
    internal fun normalize() {
        if (state == State.Operational) return
        if (state == State.Migrating) {
            // TODO - check all owned tiles for Citadel(equivalent)s?
            // But for now, let's compromise and assume none were bought and none were from Citadels
            pass
        }
        val all = city.tiles.size
        val base = countOwnedBaseTiles()
        val expansion = all - getUnchecked(OwnershipSource.Bought) - getUnchecked(OwnershipSource.Free) - base
        check(expansion >= 0)
        setUnchecked(OwnershipSource.Base, base)
        setUnchecked(OwnershipSource.Expansion, expansion)
        setAll(all)
        state = State.Operational
    }

    private fun verify() {
        if (!::city.isInitialized)
            return
        check(tileCounts.sum() == getAll() * 2) {
            "Tiles owned by ${city.name} have a discrepancy between sum of sources (${tileCounts.sum() - getAll()}) and source All (${getAll()})"
        }
        check(getAll() == city.tiles.size) {
            "Tiles owned by ${city.name} have a discrepancy between tiles.size (${city.tiles.size}) and expansion.tileCounts (${getAll()})"
        }
    }

    private fun countOwnedBaseTiles(): Int {
        val foundingRadius = city.expansion.foundingRadius
        fun foundingTileFilter(tile: Tile) = tile.owningCity == city && city.expansion.foundingTileFilter(tile)
        return if (foundingRadius == 1)
            1 + city.getCenterTile().neighbors.count(::foundingTileFilter)
        else
            @Suppress("DEPRECATION") // forEachTileInDistance doesn't allow chaining
            city.getCenterTile().getTilesInDistance(foundingRadius).count(::foundingTileFilter)
    }

    //region Overrides
    override fun write(json: Json) {
        normalize()
        verify()
        fun save(key: OwnershipSource) {
            if (getUnchecked(key) > 0)
                json.writeValue(key.name, getUnchecked(key))
        }
        // Don't serialize All, Expansion or Base: They can be derived.
        save(OwnershipSource.Bought)
        save(OwnershipSource.Free)
    }

    override fun read(json: Json, jsonData: JsonValue) {
        fun load(key: OwnershipSource) {
            val entry = jsonData.get(key.name) ?: return
            setUnchecked(key, entry.asInt())
        }
        load(OwnershipSource.Bought)
        load(OwnershipSource.Free)
        state = State.Deserialized
    }

    /** Debug-only visualization */
    override fun toString() =
        (if (::city.isInitialized) city.name + ": " else "") +
        "$state [${OwnershipSource.entries.joinToString { "$it=${getUnchecked(it)}" } }]"

    /** Having an equality contract saves space in serialized games */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CityExpansionTileCounter) return false
        if (equivalent(other) || other.equivalent(this)) return true
        return state == other.state && tileCounts.contentEquals(other.tileCounts)
    }
    private fun equivalent(other: CityExpansionTileCounter) =
        state == State.Operational && other.state == State.Migrating &&
            getUnchecked(OwnershipSource.Bought) == 0 && getUnchecked(OwnershipSource.Free) == 0
    override fun hashCode() = 31 * state.hashCode() + tileCounts.contentHashCode()

    //endregion
}
