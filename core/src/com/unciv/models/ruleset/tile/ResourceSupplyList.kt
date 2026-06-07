package com.unciv.models.ruleset.tile

import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.IConstruction  // Kdoc only
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.Readonly
import kotlin.collections.LinkedHashMap

/** Container helps to aggregate supply and demand of [resources][ResourceSupply.resource], categorized by [origin][ResourceSupply.origin].
 *  - [ResourceSupply] is an interface but still supports a constructor-like factory: [ResourceSupply(resource, origin, amount)][ResourceSupply.invoke]
 *  - Iteration and [listBy] return data rows as the immutable [ResourceSupply] interface
 *  - Keeps [ResourceSupply] internally as instances with mutable [amount][ResourceSupply.amount] for efficiency in adding up amounts
 *  - Uses double maps to ensure fast indexing with one or two keys ([get], [listBy]) for the price of ~3x memory
 *  @param keepZeroAmounts If `false`, entries with [amount][ResourceSupply.amount] 0 are eliminated
 */
@InternalState
class ResourceSupplyList(
    private val keepZeroAmounts: Boolean = false,
    private val initialCapacityResources: Int = 36, // Allows all resources in G&K
    private val initialCapacityOrigins: Int = 16
) : Iterable<ResourceSupplyList.ResourceSupply> {
    /**
     * Holds one "data row", [resource] and [origin] function as keys while [amount] is the 'value'
     */
    interface ResourceSupply {
        val resource: TileResource
        val origin: String
        val amount: Int

        companion object {
            @Readonly
            operator fun invoke(resource: TileResource, origin: String, amount: Int): ResourceSupply =
                StoredResourceSupply(resource, origin, amount)
        }

        @Readonly fun isCityStateOrTradeOrigin() = (origin == Constants.cityStates || origin == "Trade") && amount > 0
        @Readonly fun copy() = this // Since the interface is immutable, and adding the result of copy() to another ResourceSupplyList will instantiate a fresh row...
        @Readonly fun copy(amount: Int) = ResourceSupply(resource, origin, amount)
    }

    private data class StoredResourceSupply(
        override val resource: TileResource,
        override val origin: String,
        override var amount: Int
    ) : ResourceSupply {
        override fun toString() = "$amount ${resource.name} from $origin"
    }

    private val mapByResource = LinkedHashMap<TileResource, MutableMap<String, StoredResourceSupply>>(initialCapacityResources)
    private val mapByOrigin = LinkedHashMap<String, MutableMap<TileResource, StoredResourceSupply>>(initialCapacityOrigins)

    companion object {
        val emptyList get() = ResourceSupplyList()
    }

    ///////////////////////////// Query API /////////////////////////////

    @Suppress("NOTHING_TO_INLINE")
    @Readonly
    private inline fun getInternal(resource: TileResource, origin: String) = mapByResource[resource]?.get(origin)

    /** Fetch a [ResourceSupply] entry or `null` if no match found */
    @Readonly
    operator fun get(resource: TileResource, origin: String): ResourceSupply? = getInternal(resource, origin)

    @Readonly
    fun first() = mapByResource.values.first().values.first() as ResourceSupply

    val size get() = mapByOrigin.values.sumOf { it.size }

    @Readonly
    fun isEmpty() = mapByResource.isEmpty()

    override operator fun iterator(): Iterator<ResourceSupply> =
        mapByResource.values.flatMap { it.values }.iterator()

    @Readonly
    fun resources() = mapByResource.keys
    @Readonly
    fun origins() = mapByOrigin.keys

    @Readonly
    fun listBy(resource: TileResource): Sequence<ResourceSupply> =
        mapByResource[resource]?.values?.asSequence()?.map { it as ResourceSupply } ?: emptySequence()
    @Readonly
    fun listBy(origin: String): Sequence<ResourceSupply> =
        mapByOrigin[origin]?.values?.asSequence()?.map { it as ResourceSupply } ?: emptySequence()

    /** Get the total amount for a resource by [resource] */
    @Readonly
    fun sumBy(resource: TileResource) = listBy(resource).sumOf { it.amount }

    /** Get the total amount for a resource by [resourceName] */
    @Readonly
    fun sumBy(resourceName: String): Int {
        val resource = mapByResource.keys.firstOrNull { it.name == resourceName }
            ?: return 0
        return sumBy(resource)
    }

    /** Get the total amount for a resource by [origin] */
    @Readonly
    fun sumByOrigin(origin: String) = listBy(origin).sumOf { it.amount }

    ///////////////////////////// Mutating API /////////////////////////////

    /** Overwrite a [ResourceSupply] entry if it exists, or add if not */
    operator fun set(resource: TileResource, origin: String, amount: Int) {
        val existing = getInternal(resource, origin)
            ?: return addRow(resource, origin, amount)
        existing.amount = amount
        if (keepZeroAmounts || amount != 0) return
        removeRow(existing)
    }

    /** Add [amount] to the [entry][ResourceSupply] for [resource]/[origin] or create a new one. */
    fun add(resource: TileResource, origin: String, amount: Int = 1) {
        if (amount == 0) return
        val existing = getInternal(resource, origin)
        if (existing == null) {
            addRow(resource, origin, amount)
            return
        }
        existing.amount += amount
        if (!keepZeroAmounts && existing.amount == 0)
            removeRow(existing)
    }

    /**
     *  Add [element] unless one for [resource][ResourceSupply.resource]/[origin][ResourceSupply.origin] already exists,
     *  in which case the amounts are added up. Ensures the list contains no entries with [amount][ResourceSupply.amount] 0 unless [keepZeroAmounts] is on.
     *  @return `true` if the length of the list changed.
     */
    fun add(element: ResourceSupply) = add(element.resource, element.origin, element.amount)
    operator fun plusAssign(row: ResourceSupply) { add(row) }

    /** Add all [entries][ResourceSupply] from [resourceSupplyList] to this one. */
    fun add(resourceSupplyList: ResourceSupplyList) {
        for (row in resourceSupplyList) add(row)
    }
    operator fun plusAssign(resourceSupplyList: ResourceSupplyList) = add(resourceSupplyList)

    /** Add entries from a requirements list (as produced by [IConstruction.getResourceRequirementsPerTurn]), expressing requirement as negative supply. */
    fun subtractResourceRequirements(resourceRequirements: HashMap<String, Int>, ruleset: Ruleset, origin: String) {
        for ((resourceName, amount) in resourceRequirements) {
            val resource = ruleset.tileResources[resourceName] ?: continue
            add(resource, origin, -amount)
        }
    }

    /**
     *  Aggregate [fromList] by resource into this (by adding all entries replacing their origin with [newOrigin])
     *  @return `this`, allowing chaining
     */
    fun addByResource(fromList: ResourceSupplyList, newOrigin: String): ResourceSupplyList {
        for ((resource, subMap) in fromList.mapByResource) {
            val amount = subMap.values.sumOf { it.amount }
            add(resource, newOrigin, amount)
        }
        return this
    }

    /** Same as [addByResource] but ignores negative amounts */
    fun addPositiveByResource(fromList: ResourceSupplyList, newOrigin: String) {
        for ((resource, subMap) in fromList.mapByResource) {
            val amount = subMap.values.sumOf { if (it.amount > 0) it.amount else 0 }
            add(resource, newOrigin, amount)
        }
    }

    /** Create a new [ResourceSupplyList] aggregating resources over all origins */
    fun sumByResource(newOrigin: String) =
        ResourceSupplyList(keepZeroAmounts).addByResource(this, newOrigin)

    /**
     * Applies the given modifier function to the resource supplies.
     */
    fun applyModifiers(resourceModifier: (TileResource) -> Float) {
        for (subMap in mapByResource.values) {
            for (resourceSupply in subMap.values) {
                val modifier = resourceModifier(resourceSupply.resource)
                if (modifier == 1f) continue
                resourceSupply.amount = (resourceSupply.amount.toFloat() * modifier).toInt()
            }
        }
    }

    /**
     *  Remove all entries from a specific [origin]
     *  @return `this`, allowing chaining
     */
    fun removeAll(origin: String): ResourceSupplyList {
        val toRemoveMap = mapByOrigin.remove(origin) ?: return this
        // Now remove the rows we know need removing from mapByResource too
        for (resource in toRemoveMap.keys) {
            val subMap = mapByResource[resource]!!
            subMap.remove(origin)
            if (subMap.isEmpty())
                mapByResource.remove(resource)
        }
        return this
    }

    fun removeAll(predicate: (ResourceSupply) -> Boolean) {
        for (entry in filter(predicate)) // Filter materializes a List so no CCM
            removeRow(entry)
    }

    ///////////////////////////// Private helpers /////////////////////////////

    private fun addRow(resource: TileResource, origin: String, amount: Int) {
        val row = StoredResourceSupply(resource, origin, amount)
        mapByResource.getOrPut(resource) { LinkedHashMap(initialCapacityOrigins) }[origin] = row
        mapByOrigin.getOrPut(origin) { LinkedHashMap(initialCapacityResources) }[resource] = row
    }

    private fun removeRow(row: ResourceSupply) {
        mapByResource[row.resource]?.remove(row.origin)
        mapByOrigin[row.origin]?.remove(row.resource)
        if (mapByResource[row.resource]?.isEmpty() == true) mapByResource.remove(row.resource)
        if (mapByOrigin[row.origin]?.isEmpty() == true) mapByOrigin.remove(row.origin)
    }
}
