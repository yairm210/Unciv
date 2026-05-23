package com.unciv.models.translations

/**
 * Documentation template strings that need to be translatable.
 * Collected by [TranslationFileWriter] into template.properties.
 * Used by the DocWriters to generate localized documentation.
 */
object DocStrings {

    // region UniqueDocsWriter — uniques.md

    const val UNIQUES_TITLE = "# Uniques"
    const val UNIQUES_OVERVIEW = "An overview of uniques can be found [here](../Developers/Uniques.md)"
    const val UNIQUES_INTRO = "Simple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](Unique-parameters.md)"
    const val DOC_EXAMPLE = "Example:"
    const val DOC_MODIFIER_INTRO = "This unique's effect can be modified with"
    const val DOC_CACHED_UNIQUE = "Due to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work."
    const val DOC_NO_CONDITIONALS = "This unique does not support conditionals."
    const val DOC_HIDDEN_TO_USERS = "This unique is automatically hidden from users."
    const val DOC_APPLICABLE_TO = "Applicable to:"

    // endregion

    // region UiElementDocsWriter — Creating-a-UI-skin.md

    const val SKIN_DIRECTORY = "Directory"
    const val SKIN_NAME = "Name"
    const val SKIN_DEFAULT_SHAPE = "Default shape"
    const val SKIN_IMAGE = "Image"

    // endregion
}
