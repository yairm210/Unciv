package com.unciv.ui.screens.civilopediascreen

import com.unciv.ui.screens.civilopediascreen.CivilopediaHistory.Entry

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

    override fun iterator() = stack.iterator()

    fun push(category: CivilopediaCategories, entry: String, scroll: Float) {
        if (navigatingAwayFrom in stack.indices) {
            if (stack[navigatingAwayFrom].sameEntry(category, entry))
                updateScroll(navigatingAwayFrom, scroll)
            return
        }
        if (position in stack.indices && stack[position].sameEntry(category, entry))
            return updateScroll(position, scroll)
        if (position + 1 < stack.size)
            for (index in stack.size - 1 downTo position + 1)
                stack.removeAt(index)
        stack.add(Entry(category, entry, scroll))
        if (stack.size > MAX_DEPTH) stack.removeFirst()
        position = stack.size - 1
    }

    fun updateScroll(category: CivilopediaCategories, entry: String, scroll: Float) {
        val index = if (navigatingAwayFrom < 0) position else navigatingAwayFrom
        if (index !in stack.indices) return
        if (stack[index].sameEntry(category, entry))
            updateScroll(position, scroll)
    }

    fun back(): Entry? {
        if (!hasBack()) return null
        position--
        return stack[position]
    }
    fun hasBack() = position > 0

    fun forward(): Entry? {
        if (!hasForward()) return null
        position++
        return stack[position]
    }
    fun hasForward() = position < stack.size - 1

    fun current(): Entry? =
        if (position in stack.indices) stack[position] else null

    fun isEmpty() = stack.isEmpty()

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
        stack[position] = old.copy(scroll = scroll)
    }
}
