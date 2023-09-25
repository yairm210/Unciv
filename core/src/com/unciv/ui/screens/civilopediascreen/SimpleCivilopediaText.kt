package com.unciv.ui.screens.civilopediascreen

/** Storage class for instantiation of the simplest form containing only the lines collection */
open class SimpleCivilopediaText(
    override var civilopediaText: List<FormattedLine>
) : ICivilopediaText {
    constructor(strings: Sequence<String>) : this(
        strings.map { FormattedLine(it) }.toList())
    constructor(first: Sequence<FormattedLine>, strings: Sequence<String>) : this(
        (first + strings.map { FormattedLine(it) }).toList())

    override fun makeLink() = ""
}
