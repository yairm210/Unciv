package com.unciv.logic

import com.unciv.models.translations.tr

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

class MissingModsException(
    val missingMods: String
) : UncivShowableException("Missing mods: [$missingMods]")
