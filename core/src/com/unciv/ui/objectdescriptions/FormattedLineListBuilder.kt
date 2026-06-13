package com.unciv.ui.objectdescriptions

import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly

/**
 *  A builder for use in [ICivilopediaText.getCivilopediaTextLines][com.unciv.ui.screens.civilopediascreen.ICivilopediaText.getCivilopediaTextLines].
 *  * API: [buildCivilopediaText]. Within its `block`, use the following methods:
 *      * [add] accepts combinations of [FormattedLine] parameters, [FormattedLine] itself, [Iterable]s or [Sequence]s of [FormattedLine],
 *        or any other [Iterable] with a transform that produces [FormattedLine] from each element.
 *      * [space] or [separator] produce a vertical spacer or a horizontal line, names chosen for clarity to distinguish from [add].
 *      * [addUniques] will add all [uniques][IHasUniques.uniques] of a [IHasUniques] receiver and is a replacement for [uniquesToCivilopediaTextLines].
 */
interface FormattedLineListBuilder {
    companion object {
        @Readonly
        fun buildCivilopediaText(capacity: Int = 16, block: FormattedLineListBuilder.() -> Unit): List<FormattedLine> {
            @LocalState
            val builder = FormattedLineListBuilderImpl(capacity)
            builder.block()
            return builder.build()
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
        @Readonly
        internal abstract fun addTo(to: FormattedLineListBuilder)
    }

    @Readonly
    fun build(): List<FormattedLine>

    @Readonly
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
    )

    @Readonly
    fun add(line: FormattedLine): Boolean
    @Readonly
    fun add(unique: Unique, indent: Int = 0): Boolean
    @Readonly
    fun add(extraImage: String, imageSize: Float): Boolean

    @Readonly
    fun add(newLines: Iterable<FormattedLine>): Boolean
    @Readonly
    fun add(newLines: Sequence<FormattedLine>): Boolean
    @Readonly
    fun <T> add(input: Iterable<T>, transform: T.() -> FormattedLine)

    @Readonly
    fun add(separator: SeparatorType)
    @Readonly
    fun space()
    @Readonly
    fun separator()

    @Readonly
    context(source: IHasUniques)
    fun addUniques(
        leadingSeparator: SeparatorType = SeparatorType.Space,
        colorConsumesResources: Boolean = false,
        exclude: Unique.() -> Boolean = { false }
    )
}

@InternalState
private class FormattedLineListBuilderImpl(capacity: Int = 16) : FormattedLineListBuilder {
    @Cache
    private val lines = ArrayList<FormattedLine>(capacity)

    override fun build() = lines

    override fun add(
        text: String,
        link: String,
        icon: String,
        header: Int,
        indent: Int,
        color: String,
        starred: Boolean,
        centered: Boolean,
        iconCrossed: Boolean
    ) {
        lines.add(FormattedLine(text, link, icon, header = header, indent = indent, color = color, starred = starred, centered = centered, iconCrossed = iconCrossed))
    }

    override fun add(line: FormattedLine) = lines.add(line)
    override fun add(unique: Unique, indent: Int) = lines.add(FormattedLine(unique, indent))
    override fun add(extraImage: String, imageSize: Float) = lines.add(FormattedLine(extraImage = extraImage, imageSize = imageSize))

    override fun add(separator: FormattedLineListBuilder.SeparatorType) = separator.addTo(this)
    override fun space() { lines.add(FormattedLine()) }
    override fun separator() { lines.add(FormattedLine(separator = true)) }

    override fun add(newLines: Iterable<FormattedLine>) = lines.addAll(newLines)
    override fun add(newLines: Sequence<FormattedLine>) = lines.addAll(newLines)
    override fun <T> add(input: Iterable<T>, transform: T.() -> FormattedLine) {
        for (item in input)
            add(item.transform())
    }

    context(source: IHasUniques)
    override fun addUniques(
        leadingSeparator: FormattedLineListBuilder.SeparatorType,
        colorConsumesResources: Boolean,
        exclude: Unique.() -> Boolean
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
}
