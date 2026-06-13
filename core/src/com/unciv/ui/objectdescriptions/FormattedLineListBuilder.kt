package com.unciv.ui.objectdescriptions

import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.civilopediascreen.FormattedLine

/**
 *  A builder for use in [ICivilopediaText.getCivilopediaTextLines][com.unciv.ui.screens.civilopediascreen.ICivilopediaText.getCivilopediaTextLines].
 *  * API: [buildCivilopediaText]. Within its `block`, use the following methods:
 *      * [add] accepts combinations of [FormattedLine] parameters, [FormattedLine] itself, [Iterable]s or [Sequence]s of [FormattedLine],
 *        or any other [Iterable] with a transform that produces [FormattedLine] from each element.
 *      * [space] or [separator] produce a vertical spacer or a horizontal line, names chosen for clarity to distinguish from [add].
 *      * [addUniques] will add all [uniques][IHasUniques.uniques] of a [IHasUniques] receiver and is a replacement for [uniquesToCivilopediaTextLines].
 */
class FormattedLineListBuilder(capacity: Int = 16) {
    private val lines = ArrayList<FormattedLine>(capacity)

    fun add(
        text: String,
        link: String = "",
        icon: String = "",
        header: Int = 0,
        indent: Int = 0,
        color: String = "",
        starred: Boolean = false,
        centered: Boolean = false,
        iconCrossed: Boolean = false
    ) = lines.add(FormattedLine(text, link, icon, header = header, indent = indent, color = color, starred = starred, centered = centered, iconCrossed = iconCrossed))

    fun add(line: FormattedLine) = lines.add(line)
    fun add(unique: Unique, indent: Int = 0) = lines.add(FormattedLine(unique, indent))
    fun add(extraImage: String, imageSize: Float) = lines.add(FormattedLine(extraImage = extraImage, imageSize = imageSize))

    fun add(separator: SeparatorType) = separator.addTo(this)
    fun space() = lines.add(FormattedLine())
    fun separator() = lines.add(FormattedLine(separator = true))

    fun add(newLines: Iterable<FormattedLine>) = lines.addAll(newLines)
    fun add(newLines: Sequence<FormattedLine>) = lines.addAll(newLines)
    fun <T> add(input: Iterable<T>, transform: T.() -> FormattedLine) {
        for (item in input)
            add(item.transform())
    }

    context(source: IHasUniques)
    fun addUniques(
        leadingSeparator: SeparatorType = SeparatorType.Space,
        colorConsumesResources: Boolean = false,
        exclude: Unique.() -> Boolean = { false }
    ) {
        val orderedUniques = source.uniqueObjects.asSequence()
            .filterNot { it.isHiddenToUsers() || it.exclude() }

        for ((index, unique) in orderedUniques.withIndex()) {
            if (index == 0) add(leadingSeparator)
            // Optionally special-case ConsumesResources to give it a reddish color. Also ensures link always points to the resource
            // (the other constructor guesses the first object by name in the Unique parameters).
            if (colorConsumesResources && unique.type == UniqueType.ConsumesResources)
                add(unique.getDisplayText(), link = "Resources/${unique.params[1]}", color = "#F42")
            else add(unique)
        }
    }

    enum class SeparatorType {
        None {
            override fun addTo(to: FormattedLineListBuilder) {}
        }, Space {
            override fun addTo(to: FormattedLineListBuilder) { to.space() }
        }, Line {
            override fun addTo(to: FormattedLineListBuilder) { to.separator() }
        };
        internal abstract fun addTo(to: FormattedLineListBuilder)
    }

    companion object {
        fun buildCivilopediaText(capacity: Int = 16, block: FormattedLineListBuilder.() -> Unit): List<FormattedLine> {
            val builder = FormattedLineListBuilder(capacity)
            builder.block()
            return builder.lines
        }
    }
}
