package com.unciv.ui.screens.civilopediascreen

import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.screens.civilopediascreen.CivilopediaHistory.Entry
import org.jetbrains.annotations.VisibleForTesting

object CivilopediaHistory : Iterable<Entry> {
    private const val MAX_DEPTH = 50

    data class Entry(
        val category: CivilopediaCategories,
        val entry: String,
        val scroll: Float
    )

    private val stack = ArrayDeque<Entry>()
    var position = 0
        private set
    private var navigatingAwayFrom: Int = -1
    private var context: List<String>? = null

    override fun iterator() = stack.iterator()

    /** Allows the history to reset when the [ruleset] changes */
    fun setContext(ruleset: Ruleset) {
        setContext(ruleset.mods.toList().ifEmpty { listOf(ruleset.name) })
    }

    @VisibleForTesting
    fun setContext(rulesets: List<String>) {
        if (context == rulesets) return
        clear()
        context = rulesets.toList()
    }

    @VisibleForTesting
    fun clear() {
        stack.clear()
        position = 0
        navigatingAwayFrom = -1
        context = null
    }

    /** Records a new [position][Entry].
     *
     *  If [category]/[entry] match the [current] entry, calls [updateScroll] instead of adding.
     *  If there are [hasForward] entries, those are pruned before adding the new entry.
     *  Forbidden while [navigateTo] is underway.
     */
    fun push(category: CivilopediaCategories, entry: String, scroll: Float) {
        check(navigatingAwayFrom == -1) { "CivilopediaHistory.push called while navigating inside history" }
        if (position in stack.indices && stack[position].sameEntry(category, entry))
            return updateScroll(position, scroll)
        if (position + 1 < stack.size)
            for (index in stack.size - 1 downTo position + 1)
                stack.removeAt(index)
        stack.add(Entry(category, entry, scroll))
        if (stack.size > MAX_DEPTH) stack.removeFirst()
        position = stack.size - 1
    }

    /** Updates [the vertical scroll position][Entry.scroll] for [category]/[entry].
     *
     *  Updates [current] if any, or the previous entry while [navigateTo] is underway.
     *  Silently ignores when [category]/[entry] do not match the "current" entry.
     */
    fun updateScroll(category: CivilopediaCategories, entry: String, scroll: Float) {
        val index = if (navigatingAwayFrom < 0) position else navigatingAwayFrom
        if (index !in stack.indices) return
        if (stack[index].sameEntry(category, entry))
            updateScroll(index, scroll)
    }

    /** Navigate one step forward, if any. Does nothing if [isEmpty] or [hasBack]`==false`.
     *  @param isValid - A lambda that can mark entries to skip
     *  @param action - Should handle actual navigation. While this runs, [push] is forbidden and [updateScroll] is redirected to the previous [current]
     *  @return The state of [hasBack] before the navigation
     */
    fun back(isValid: (Entry) -> Boolean = { true }, action: (Entry) -> Unit): Boolean {
        val index = findPosition(-1, isValid)
        if (index < 0) return false
        navigateTo(index, stack[index], action)
        return true
    }
    /** Is there an entry to go back to ?
     *  @param isValid - A lambda that can mark entries to skip */
    fun hasBack(isValid: (Entry) -> Boolean = { true }) = findPosition(-1, isValid) >= 0

    /** Navigate one step forward, if any. Does nothing if [isEmpty] or [hasForward]`==false`.
     *  @param isValid - A lambda that can mark entries to skip
     *  @param action - Should handle actual navigation. While this runs, [push] is forbidden and [updateScroll] is redirected to the previous [current]
     */
    // Note: Unlike `back`, this has no return value, because it helps only `CivilopediaScreen.navigateToDefault`
    fun forward(isValid: (Entry) -> Boolean = { true }, action: (Entry) -> Unit) {
        val index = findPosition(1, isValid)
        if (index < 0) return
        navigateTo(index, stack[index], action)
    }
    /** Is there an entry to go forward to ?
     *  @param isValid - A lambda that can mark entries to skip */
    fun hasForward(isValid: (Entry) -> Boolean = { true }) = findPosition(1, isValid) >= 0

    private fun findPosition(direction: Int, isValid: (Entry) -> Boolean): Int {
        var index = position + direction
        while (index in stack.indices) {
            if (isValid(stack[index])) return index
            index += direction
        }
        return -1
    }

    fun current(): Entry? =
        if (position in stack.indices) stack[position] else null

    fun isEmpty() = stack.isEmpty()

    /** Navigation helper that ensures code in [action] can't call [push] or [updateScroll] the wrong position.
     *
     *  For use by [back], [forward] and [CivilopediaHistoryMenu].
     */
    @VisibleForTesting
    fun navigateTo(index: Int, entry: Entry, action: (Entry) -> Unit) {
        check(stack[index].sameEntry(entry.category, entry.entry)) { "CivilopediaHistory.navigateTo called with `index` not matching `entry` parameter" }
        navigatingAwayFrom = position
        position = index
        try {
            action(entry)
        } finally {
            navigatingAwayFrom = -1
        }
    }

    private fun Entry.sameEntry(category: CivilopediaCategories, entry: String) =
        this.category == category && this.entry == entry

    private fun updateScroll(index: Int, scroll: Float) {
        val old = stack[index]
        if (old.scroll == scroll) return
        stack[index] = old.copy(scroll = scroll)
    }

    /** Non-allocating version of `for ((index, entry) in CivilopediaHistory.withIndex().reversed())` */
    fun forEachIndexedReversed(action: (Int, Entry) -> Unit) {
        for (index in stack.indices.reversed())
            action(index, stack[index])
    }

    /** Debugger visualization */
    override fun toString(): String {
        return buildString {
            appendLine(CivilopediaHistory::class.simpleName)
            forEachIndexedReversed { index, entry ->
                if (index == position) append("*")
                appendLine("\t${entry.category.label} - ${entry.entry} (${entry.scroll})")
            }
        }
    }
}
