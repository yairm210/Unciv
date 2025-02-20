package com.unciv.models.ruleset.tile

import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.IConstruction  // Kdoc only

/** Container helps aggregating supply and demand of [resources][ResourceSupply.resource], categorized by [origin][ResourceSupply.origin].
 *
 *  @param keepZeroAmounts If `false`, entries with [amount][ResourceSupply.amount] 0 are eliminated
 */
class ResourceSupplyList(
    private val keepZeroAmounts: Boolean = false
) : ArrayList<ResourceSupplyList.ResourceSupply>(24) {
    // initialCapacity 24: Allows all resources in G&K with just _one_ Array growth step (which is 50%)

    /**
     * Holds one "data row", [resource] and [origin] function as keys while [amount] is the 'value'
     * This is not technically immutable, but **no** code outside [ResourceSupplyList] should update the value.
     * [ResourceSupplyList.add] will update the value in existing instances, and should remain the only place.
     */
    data class ResourceSupply(val resource: TileResource, val origin: String, var amount: Int) {
        fun isCityStateOrTradeOrigin() = (origin == Constants.cityStates || origin == "Trade") && amount > 0
        override fun toString() = "$amount ${resource.name} from $origin"
    }

    /** Fetch a [ResourceSupply] entry or `null` if no match found */
    fun get(resource: TileResource, origin: String) =
        firstOrNull { it.resource.name == resource.name && it.origin == origin }

    /** Get the total amount for a resource by [resourceName] */
    fun sumBy(resourceName: String) =
        asSequence().filter { it.resource.name == resourceName }.sumOf { it.amount }

    /**
     *  Add [element] unless one for [resource][ResourceSupply.resource]/[origin][ResourceSupply.origin] already exists,
     *  in which case the amounts are added up. Ensures the list contains no entries with [amount][ResourceSupply.amount] 0 unless [keepZeroAmounts] is on.
     *  @return `true` if the length of the list changed.
     */
    override fun add(element: ResourceSupply): Boolean {
        val existingResourceSupply = get(element.resource, element.origin)
        if (existingResourceSupply != null) {
            // This is at the time of writing the _only_ place updating the field.
            // To check: Change to val, comment out this line, compile, revert.
            existingResourceSupply.amount += element.amount
            if (keepZeroAmounts || existingResourceSupply.amount != 0) return false
            remove(existingResourceSupply)
        } else {
            if (!keepZeroAmounts && element.amount == 0) return false
            super.add(element)
        }
        return true
    }

    /** Add [amount] to the [entry][ResourceSupply] for [resource]/[origin] or create a new one. */
    fun add(resource: TileResource, origin: String, amount: Int = 1) {
        add(ResourceSupply(resource, origin, amount))
    }

    /** Add all [entries][ResourceSupply] from [resourceSupplyList] to this one. */
    fun add(resourceSupplyList: ResourceSupplyList) {
        for (resourceSupply in resourceSupplyList)
            add(resourceSupply)
    }

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
        for (resourceSupply in fromList)
            add(resourceSupply.resource, newOrigin, resourceSupply.amount)
        return this
    }

    /** Same as [addByResource] but ignores negative amounts */
    fun addPositiveByResource(fromList: ResourceSupplyList, newOrigin: String) {
        for (resourceSupply in fromList)
            if (resourceSupply.amount > 0)
                add(resourceSupply.resource, newOrigin, resourceSupply.amount)
    }

    /** Create a new [ResourceSupplyList] aggregating resources over all origins */
    fun sumByResource(newOrigin: String) = ResourceSupplyList(keepZeroAmounts).addByResource(this, newOrigin)

    /**
     *  Remove all entries from a specific [origin]
     *  @return `this`, allowing chaining
     */
    fun removeAll(origin: String): ResourceSupplyList {
        // The filter creates a separate list so the iteration does not modify concurrently
        filter { it.origin == origin }.forEach { remove(it) }
        return this
    }

    companion object {
        val emptyList = ResourceSupplyList()
    }
}
