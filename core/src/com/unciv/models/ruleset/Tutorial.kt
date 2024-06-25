package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.ui.screens.civilopediascreen.FormattedLine

/**
 *  Container for json-read "Tutorial" text, potentially decorated.
 *  Two types for now - triggered (which can be hidden from Civilopedia via the usual unique) and Civilopedia-only.
 *  Triggered ones are displayed in a Popup, the relation is via `name` (the enum name cleaned by dropping leading '_'
 *  and replacing other '_' with blanks must match a json entry name exactly).
 *
 *  TODO WorldScreen.getCurrentTutorialTask() has similar, but independent Tutorials - consolidate
 *
 *  @see com.unciv.models.TutorialTrigger
 */
class Tutorial : RulesetObject() {
    // Has access to the full power of uniques for:
    // * Easier integration into Civilopedia
    // * HiddenWithoutReligion, HiddenFromCivilopedia work _directly_
    // * Future expansion - other meta tests to display or not are thinkable,
    //   e.g. modders may want to hide instructions until you discover the game element?
    // -SomeTrog
    override var name = ""  // overridden only to have the name seen first by TranslationFileWriter

    /** These lines will be displayed (when the Tutorial is _triggered_) one after another,
     *  and the Tutorial is marked as completed only once the last line is dismissed with "OK" */
    //todo migrate to civilopediaText then remove or deprecate?
    val steps: ArrayList<String>? = null

    override fun getUniqueTarget() = UniqueTarget.Tutorial
    override fun makeLink() = "Tutorial/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset) =
        steps?.map { FormattedLine(it) }.orEmpty()
}
