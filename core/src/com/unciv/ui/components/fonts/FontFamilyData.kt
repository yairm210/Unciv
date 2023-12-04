package com.unciv.ui.components.fonts

import com.unciv.models.translations.tr

// If save in `GameSettings` need use invariantFamily.
// If show to user need use localName.
// If save localName in `GameSettings` may generate garbled characters by encoding.
class FontFamilyData(
    val localName: String,
    val invariantName: String = localName,
    val filePath: String? = null
) {

    @Suppress("unused") // For serialization
    constructor() : this(default.localName, default.invariantName)

    // Implement kotlin equality contract such that _only_ the invariantName field is compared.
    override fun equals(other: Any?): Boolean {
        return if (other is FontFamilyData) invariantName == other.invariantName
        else super.equals(other)
    }

    override fun hashCode() = invariantName.hashCode()

    /** For SelectBox usage */
    override fun toString() = localName.tr()

    companion object {
        val default = FontFamilyData("Default Font", Fonts.DEFAULT_FONT_FAMILY)
    }
}
