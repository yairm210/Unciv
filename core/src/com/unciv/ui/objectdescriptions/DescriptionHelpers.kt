package com.unciv.ui.objectdescriptions

import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.screens.civilopediascreen.FormattedLine

/**
 *  Appends user-visible Uniques as translated text to a [line collection][lineList].
 *
 *  Follows json order.
 *  @param lineList Target collection, will be mutated. Defaults to an empty List for easier use with consumer-only client code.
 *  @param exclude Predicate that can exclude Uniques by returning `true` (defaults to return `false`).
 *  @return the [lineList] with added, translated info on [this.uniques] - for chaining
 */
fun IHasUniques.uniquesToDescription(
    lineList: MutableCollection<String> = mutableListOf(),
    exclude: Unique.() -> Boolean = {false}
): MutableCollection<String> {
    for (unique in uniqueObjects) {
        if (unique.isHiddenToUsers()) continue
        if (unique.exclude()) continue
        lineList += unique.getDisplayText().tr()
    }
    return lineList
}

/**
 *  A Sequence of user-visible Uniques as [FormattedLine]s.
 *
 *  @param leadingSeparator Tristate: If there are lines to display and this parameter is not `null`, a leading line is output, as separator or empty line.
 *  @param colorConsumesResources If set, ConsumesResources Uniques get a reddish color.
 *  @param exclude Predicate that can exclude Uniques by returning `true` (defaults to return `false`).
 */
fun IHasUniques.uniquesToCivilopediaTextLines(
    leadingSeparator: Boolean? = false,
    colorConsumesResources: Boolean = false,
    exclude: Unique.() -> Boolean = {false}
) = sequence {
    val orderedUniques = uniqueObjects.asSequence()
        .filterNot { it.isHiddenToUsers() || it.exclude() }

    for ((index, unique) in orderedUniques.withIndex()) {
        if (leadingSeparator != null && index == 0)
            yield(FormattedLine(separator = leadingSeparator))
        // Optionally special-case ConsumesResources to give it a reddish color. Also ensures link always points to the resource
        // (the other constructor guesses the first object by name in the Unique parameters).
        yield(
            if (colorConsumesResources && unique.type == UniqueType.ConsumesResources)
                FormattedLine(unique.getDisplayText(), link = "Resources/${unique.params[1]}", color = "#F42")
                else FormattedLine(unique)
        )
    }
}

/**
 *  Appends user-visible Uniques as [FormattedLine]s to [lineList].
 *
 *  @param leadingSeparator Tristate: If there are lines to display and this parameter is not `null`, a leading line is output, as separator or empty line.
 *  @param colorConsumesResources If set, ConsumesResources Uniques get a reddish color.
 *  @param exclude Predicate that can exclude Uniques by returning `true` (defaults to return `false`).
 */
fun IHasUniques.uniquesToCivilopediaTextLines(
    lineList: MutableCollection<FormattedLine>,
    leadingSeparator: Boolean? = false,
    colorConsumesResources: Boolean = false,
    exclude: Unique.() -> Boolean = {false}
) {
    uniquesToCivilopediaTextLines(leadingSeparator, colorConsumesResources, exclude)
        .toCollection(lineList)
}
