package com.unciv.models.ruleset.tile

import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.logic.city.IConstruction  // Kdoc only


/** Container helps aggregating supply and demand of [resources][ResourceSupply.resource], categorized by [origin][ResourceSupply.origin].
 *
 *  @param keepZeroAmounts If `false`, entries with [amount][ResourceSupply.amount] 0 are eliminated 
 */
class ResourceSupplyList(
    private val keepZeroAmounts: Boolean = false
) : ArrayList<ResourceSupplyList.ResourceSupply>(28) {

    /** Holds one "data row", [resource] and [origin] function as keys while [amount] is the 'value' */
    data class ResourceSupply(val resource: TileResource, val origin: String, var amount: Int) {
        fun isCityStateOrTrade() = (origin == Constants.cityStates || origin == "Trade") && amount > 0
        override fun toString() = "$amount ${resource.name} from $origin"
    }

    /** Fetch a [ResourceSupply] entry or `null` if no match found */
    fun get(resource: TileResource, origin: String): ResourceSupply? {
        var result: ResourceSupply? = null
        Instrumentation.get.measure {
            result = firstOrNull { it.resource == resource && it.origin == origin }
        }
        return result
    }

    /** Get the total amount for a resource by [resourceName] */
    fun sumBy(resourceName: String) : Int {
        var result = 0
        Instrumentation.sumBy.measure {
            result = asSequence().filter { it.resource.name == resourceName }.sumOf { it.amount }
        }
        return result
    }

    /**
     *  Add [element] unless one for [resource][ResourceSupply.resource]/[origin][ResourceSupply.origin] already exists,
     *  in which case the amounts are added up. Ensures the list contains no entries with [amount][ResourceSupply.amount] 0 unless [keepZeroAmounts] is on.
     *  @return `true` if the length of the list changed.
     */
    override fun add(element: ResourceSupply): Boolean {
        var result = false
        Instrumentation.add.measure {
            result = fun(): Boolean {
                val existingResourceSupply = get(element.resource, element.origin)
                if (existingResourceSupply != null) {
                    existingResourceSupply.amount += element.amount
                    if (keepZeroAmounts || existingResourceSupply.amount != 0) return false
                    remove(existingResourceSupply)
                } else {
                    if (!keepZeroAmounts && element.amount == 0) return false
                    super.add(element)
                }
                return true
            }()
        }
        return result
    }

    /** Add [amount] to the [entry][ResourceSupply] for [resource]/[origin] or create a new one. */
    fun add(resource: TileResource, origin: String, amount: Int = 1) {
        Instrumentation.add_params.measure {
            add(ResourceSupply(resource, origin, amount))
        }
    }

    /** Add all [entries][ResourceSupply] from [resourceSupplyList] to this one. */
    fun add(resourceSupplyList: ResourceSupplyList) {
        Instrumentation.add_list.measure {
            for (resourceSupply in resourceSupplyList)
                add(resourceSupply)
        }
    }

    /** Add entries from a requirements list (as produced by [IConstruction.getResourceRequirements]), expressing requirement as negative supply. */
    fun subtractResourceRequirements(resourceRequirements: HashMap<String, Int>, ruleset: Ruleset, origin: String) {
        Instrumentation.subtractResourceRequirements.measure {
            for ((resourceName, amount) in resourceRequirements) {
                val resource = ruleset.tileResources[resourceName] ?: continue
                add(resource, origin, -amount)
            }
        }
    }

    /**
     *  Aggregate [fromList] by resource into this (by adding all entries replacing their origin with [newOrigin])
     *  @return `this`, allowing chaining
     */
    fun addByResource(fromList: ResourceSupplyList, newOrigin: String): ResourceSupplyList {
        Instrumentation.addByResource.measure {
            for (resourceSupply in fromList)
                add(resourceSupply.resource, newOrigin, resourceSupply.amount)
        }
        return this
    }

    /** Same as [addByResource] but ignores negative amounts */
    fun addPositiveByResource(fromList: ResourceSupplyList, newOrigin: String) {
        Instrumentation.addPositiveByResource.measure {
            for (resourceSupply in fromList)
                if (resourceSupply.amount > 0)
                    add(resourceSupply.resource, newOrigin, resourceSupply.amount)
        }
    }

    /** Create a new [ResourceSupplyList] aggregating resources over all origins */
    fun sumByResource(newOrigin: String): ResourceSupplyList {
        lateinit var result: ResourceSupplyList
        Instrumentation.sumByResource.measure {
            result = ResourceSupplyList(keepZeroAmounts).addByResource(this, newOrigin)
        }
        return result
    }

    /**
     *  Remove all entries from a specific [origin]
     *  @return `this`, allowing chaining
     */
    fun removeAll(origin: String): ResourceSupplyList {
        Instrumentation.removeAll.measure {
            // The filter creates a separate list so the iteration does not modify concurrently
            filter { it.origin == origin }.forEach { remove(it) }
        }
        return this
    }

    companion object {
        val emptyList = ResourceSupplyList()
        fun printInstrumentation() = Instrumentation.print()

        @Suppress("EnumEntryName")
        private enum class Instrumentation(
            val calls: Instrumentation? = null
        ) {
            get,
            sumBy,
            add,
            add_params(add),
            add_list,
            subtractResourceRequirements,
            addByResource,
            sumByResource(addByResource),
            addPositiveByResource,
            removeAll,
            ;
            var count: Int = 0
            var totalTime: Long = 0L
            private var entryTime: Long = 0L

            fun measure(runnable: ()->Unit) {
                entryTime = System.nanoTime()
                runnable()
                totalTime += System.nanoTime() - entryTime
                count++
            }
            fun averageTime() = if (count == 0) 0 else totalTime / count
            fun calledBy() = values().firstOrNull { it.calls == this }

            companion object {
                fun print() {
                    println("ResourceSupplyList<Map>:")
                    for (entry in values()) {
                        val correctedCount = entry.count - (entry.calledBy()?.count ?: 0)
                        val correctedTime = entry.averageTime() - (entry.calls?.averageTime() ?: 0L)
                        println(
                            "\t${entry.name}: $correctedCount calls @ ${correctedTime}ns" +
                                    (entry.calledBy()?.run { ", called by $name which ran $count times, total ${entry.count} times" } ?: "") +
                                    (entry.calls?.run { ", calling $name @ ${averageTime()}ns, inclusive avg: ${entry.averageTime()}" } ?: "")
                        )
                    }
                }
            }
        }
    }
}
