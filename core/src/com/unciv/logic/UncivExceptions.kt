package com.unciv.logic

import com.unciv.models.translations.tr
import yairm210.purity.annotations.Readonly

/**
 * An [Exception] wrapper marking an Exception as suitable to be shown to the user.
 *
 * @param [errorText] should be the _**untranslated**_ error message.
 * Use [getLocalizedMessage] to get the translated [message], _**or**_ use auto-translating helpers like .toLabel() or FormattedLine().
 * Usual formatting (`[] or {}`) applies, as does the need to include the text in templates.properties.
 */
open class UncivShowableException(
    errorText: String,
    cause: Throwable? = null
) : Exception(errorText, cause) {
    // override because we _definitely_ have a non-null message from [errorText]
    override val message: String
        get() = super.message!!
    override fun getLocalizedMessage() = message.tr()
}

/** An [Exception] indicating a game or map cannot be loaded because [mods][com.unciv.models.metadata.GameParameters.mods] are missing.
 *  @param missingMods Any [Iterable] or [Collection] of Strings - will be stored entirely,
 *      but be included in the Exception's message only up to its five first elements.
 */
class MissingModsException(
    val missingMods: Iterable<String>
) : UncivShowableException("Missing mods: [${shorten(missingMods)}]") {
    companion object {
        @Readonly private fun shorten(missingMods: Iterable<String>) = missingMods.joinToString(limit = 5) { it }
    }
}
