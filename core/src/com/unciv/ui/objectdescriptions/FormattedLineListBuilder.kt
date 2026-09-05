package com.unciv.ui.objectdescriptions

import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly

/** A builder for use in [ICivilopediaText.getCivilopediaTextLines].
 *  * API: [buildCivilopediaText]. Within its `block`, use the following methods:
 *      * [defaults] change the default values set in the [buildCivilopediaText] call.
 *      * [add] accepts combinations of [FormattedLine] parameters, [FormattedLine] itself, [Iterable]s or [Sequence]s of [FormattedLine],
 *        or any other [Iterable] with a transform that produces [FormattedLine] from each element.
 *        Therefore, [ICivilopediaText.civilopediaText] is a valid argument.
 *      * [space] or [separator] produce a vertical spacer or a horizontal line, names chosen for clarity to distinguish from [add] (see [add]([SeparatorType]) for a dynamic version).
 *      * [addUniques] will add all [uniques][IHasUniques.uniques] of a [IHasUniques] receiver and is a replacement for [uniquesToCivilopediaTextLines].
 */
// Note on all the `@Readonly` annotations: They are regrettably required, to allow use of the builder in @Readonly client code.
// Read as @Readonly relative to the context of the builder, not referring to the build itself.
interface FormattedLineListBuilder {
    companion object {
        /** Starts a builder resulting in a `List<FormattedLine>`
         *  @param defaults An instance of [FormattedLine] to copy default values from. Ignored by some [add] overloads, their Kdoc will indicate this.
         *  @param capacity Initial capacity.
         *  @param block Your code, having direct access to [FormattedLineListBuilder] methods.
         */
        @Readonly
        fun buildCivilopediaText(
            defaults: FormattedLine = FormattedLine(),
            capacity: Int = 16,
            block: FormattedLineListBuilder.() -> Unit
        ): List<FormattedLine> {
            @LocalState
            val builder = FormattedLineListBuilderImpl(defaults, capacity)
            builder.block()
            return builder.build()
        }
    }

    /** Allows dynamic vertical separators:
     *
     * [None] maps to nothing, [Space] to [space], Line to [separator]
     */
    enum class SeparatorType {
        None {
            override fun addTo(to: FormattedLineListBuilder, size: Int, color: String) {}
        }, Space {
            override fun addTo(to: FormattedLineListBuilder, size: Int, color: String) { to.space() }
        }, Line {
            override fun addTo(to: FormattedLineListBuilder, size: Int, color: String) { to.separator(size, color) }
        };
        @Readonly
        internal abstract fun addTo(to: FormattedLineListBuilder, size: Int, color: String)
    }

    /** Called automatically, not part of the client API */
    @Readonly
    fun build(): List<FormattedLine>

    /** Flexible line addition, allows all parameters the [FormattedLine] constructor does, but defaults each to the template set in [defaults]. */
    @Readonly
    fun add(
        text: String = defaults().text,
        link: String = defaults().link,
        icon: String = defaults().icon,
        extraImage: String = defaults().extraImage,
        imageSize: Float = defaults().imageSize,
        size: Int = defaults().size,
        header: Int = defaults().header,
        indent: Int = defaults().indent,
        padding: Float = defaults().padding,
        color: String = defaults().color,
        separator: Boolean = defaults().separator,
        starred: Boolean = defaults().starred,
        centered: Boolean = defaults().centered,
        iconCrossed: Boolean = defaults().iconCrossed
    )

    /** Add a complete [FormattedLine], ignoring [defaults]. */
    @Readonly
    fun add(line: FormattedLine)

    /** Add a line for a [Unique], ignoring [defaults] except for [indent].
     *
     *  See also: The [FormattedLine]`(Unique)` constructor for features like supporting automatic links for ruleset objects mentioned in Unique parameters.
     */
    @Readonly
    fun add(unique: Unique, indent: Int = defaults().indent)

    /** Add an image, see [FormattedLine.extraImage]. */
    @Readonly
    fun add(extraImage: String, imageSize: Float)

    /** Add several lines, ignoring [defaults]. */
    @Readonly
    fun add(newLines: Iterable<FormattedLine>)

    /** Add several lines, ignoring [defaults]. */
    @Readonly
    fun add(newLines: Sequence<FormattedLine>)

    /** Add several lines, ignoring [defaults]. Each is built from one input element, transformed by [transform]. */
    @Readonly
    fun <T> add(input: Iterable<T>, transform: T.() -> FormattedLine)

    /** Add a vertical separator of type [separator]. [size] (line thickness) and [color] are used for type [SeparatorType.Line]. */
    @Readonly
    fun add(separator: SeparatorType, size: Int = defaults().size, color: String = defaults().color)

    /** Add a vertical space half the height of a normal line. */
    @Readonly
    fun space()

    /** Add a horizontal line separator. [size] is the line thickness. */
    @Readonly
    fun separator(size: Int = defaults().size, color: String = defaults().color)

    /** Add lines for the Uniques of [source], ignoring [defaults].
     *
     *  Supports automatic links for ruleset objects mentioned in Unique parameters.
     *  @param leadingSeparator Used only when actual uniques content follows, calls [add]([SeparatorType]).
     *  @param colorConsumesResources If set, ConsumesResources Uniques get a reddish color.
     *  @param exclude Predicate that can exclude Uniques by returning `true` (defaults to return `false`).
     */
    @Readonly
    context(source: IHasUniques)
    fun addUniques(
        leadingSeparator: SeparatorType = SeparatorType.Space,
        colorConsumesResources: Boolean = false,
        exclude: Unique.() -> Boolean = { false }
    )

    /** Change the template default values are drawn from */
    @Readonly
    fun defaults(line: FormattedLine)
    /** Retrieve the template default values are drawn from */
    @Readonly
    fun defaults(): FormattedLine
}

@InternalState
private class FormattedLineListBuilderImpl(
    defaults: FormattedLine,
    capacity: Int
) : FormattedLineListBuilder {
    @Cache
    private val lines = ArrayList<FormattedLine>(capacity)

    @Cache
    private var defaultsLine = defaults

    override fun build() = lines

    override fun add(
        text: String,
        link: String,
        icon: String,
        extraImage: String,
        imageSize: Float,
        size: Int,
        header: Int,
        indent: Int,
        padding: Float,
        color: String,
        separator: Boolean,
        starred: Boolean,
        centered: Boolean,
        iconCrossed: Boolean
    ) {
        lines.add(FormattedLine(text, link, icon, extraImage, imageSize, size, header, indent, padding, color, separator, starred, centered, iconCrossed))
    }

    override fun add(line: FormattedLine) {
        lines.add(line)
    }
    override fun add(unique: Unique, indent: Int) {
        lines.add(FormattedLine(unique, indent))
    }
    override fun add(extraImage: String, imageSize: Float) {
        lines.add(FormattedLine(extraImage = extraImage, imageSize = imageSize))
    }

    override fun add(separator: FormattedLineListBuilder.SeparatorType, size: Int, color: String) =
        separator.addTo(this, size, color)
    override fun space() {
        lines.add(FormattedLine())
    }
    override fun separator(size: Int, color: String) {
        lines.add(FormattedLine(separator = true, size = size, color = color))
    }

    override fun add(newLines: Iterable<FormattedLine>) {
        lines.addAll(newLines)
    }
    override fun add(newLines: Sequence<FormattedLine>) {
        lines.addAll(newLines)
    }
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

    override fun defaults(line: FormattedLine) {
        defaultsLine = line
    }
    override fun defaults() = defaultsLine
}
