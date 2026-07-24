package com.unciv.models.ruleset.unique

import com.unciv.utils.Log

object UniqueTriggerActivationLimiter {
    var maxTriggerRecursionDepth = 42 // var and public to allow making it a ModConstant later

    /** Tracker for triggers - used in two ways:
     *  - A TriggerFunction is wrapped so the triggering Unique is added for the duration of the actual invoke and removed afterwards
     *  - An extra [add] from [addFreeBuildings][com.unciv.logic.civilization.CivConstructions.addFreeBuildings] because that loop doesn't happen _inside_ an actual trigger function.
     *    Since there's no clear point to place a [remove], [CityTurnManager.startTurn][com.unciv.logic.city.managers.CityTurnManager.startTurn] calls [clear] twice.
     */
    private val recursionLog = ArrayDeque<Unique>()

    /** Tracker to display overflow cause - separate so we can catch all factors contributing to the typical freee buildings loop.
     *  Simply does not remove after the TriggerFunction invoke finishes, instead it's cleared whenever [recursionLog] is emptied.
     */
    private val displayLog = ArrayDeque<Unique>()

    fun clear() {
        recursionLog.clear()
        displayLog.clear()
    }

    fun add(unique: Unique) {
        if (recursionLog.size >= maxTriggerRecursionDepth)
            throw InfiniteRecursionException()
        recursionLog.addLast(unique)
        displayLog.addLast(unique)
        Log.debug("Added %s (depth %d)", unique.text, recursionLog.size)
    }

    fun remove(unique: Unique) {
        val poppedUnique = recursionLog.removeLastOrNull()
        if (poppedUnique != unique)
            throw IllegalStateException("Trigger recursion checker failure: Stack top did not contain the correct Unique")
        if (recursionLog.isEmpty()) displayLog.clear()
    }

    internal fun TriggerFunction.wrapRecursionLimiter(unique: Unique): TriggerFunction {
        if (this == null) return null
        return {
            add(unique)
            try {
                this.invoke() // returned boolean becomes value of try and thereby result of wrapper lambda
            } finally {
                remove(unique)
            }
        }
    }

    class InfiniteRecursionException : Exception(getRecursionMessage())

    private fun Unique.isEqual(other: Unique) =
        text == other.text && sourceObjectType == other.sourceObjectType && sourceObjectName == other.sourceObjectName

    private fun getRecursionMessage(): String {
        // Try to find the loop, and if found, limit display to first occurrence to second occurrence inclusive
        // Since Unique doesn't implement equality contract, roll our own - it may be wrong, the same Unique text _might_ come from
        // different trigger sources when for some reason its source properties were not filled - but it's only display on CrashScreen for the moment.
        val displayUniques = mutableListOf<Unique>()
        for (unique in displayLog) {
            val loopFound = displayUniques.any { it.isEqual(unique) }
            displayUniques.add(unique)
            if (loopFound) break
        }
        return displayUniques.joinToString(
            " →\n    ",
            prefix = "Trigger recursion depth exceeds maximum.\n    ",
            postfix = "\nThis is a Mod error."
        ) { it.text }
    }
}
