package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.UniqueTarget

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
    // Why does this override RulesetObject()? The only unique it overrides is `Will not be displayed in Civilopedia`,
    // so allowing it access to the full power of uniques is completely unnecessary.
    // (Also, what even would it mean for this to have uniques like "[+10]% Production"? When should it even apply.)
    // imo just having a flag for this (and maybe one if religion is disabled, but even then, that should be a ruleset choice) should suffice.
    // -xlenstra
    override var name = ""  // overridden only to have the name seen first by TranslationFileWriter

    /** These lines will be displayed (when the Tutorial is _triggered_) one after another,
     *  and the Tutorial is marked as completed only once the last line is dismissed with "OK" */
    //todo migrate to civilopediaText then remove or deprecate?
    val steps: ArrayList<String>? = null

    override fun getUniqueTarget() = UniqueTarget.Tutorial
    override fun makeLink() = "Tutorial/$name"
}
