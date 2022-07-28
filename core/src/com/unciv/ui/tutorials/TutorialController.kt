package com.unciv.ui.tutorials

import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.models.TutorialTrigger
import com.unciv.models.ruleset.Tutorial
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.SimpleCivilopediaText
import com.unciv.ui.utils.BaseScreen


class TutorialController(screen: BaseScreen) {

    private val tutorialQueue = mutableSetOf<TutorialTrigger>()
    private var isTutorialShowing = false
    var allTutorialsShowedCallback: (() -> Unit)? = null
    private val tutorialRender = TutorialRender(screen)

    //todo These should live in a ruleset allowing moddability
    private val tutorials: LinkedHashMap<String, Tutorial> =
            json().fromJsonFile(Array<Tutorial>::class.java, "jsons/Tutorials.json")
                .associateByTo(linkedMapOf()) { it.name }

    fun showTutorial(tutorial: TutorialTrigger) {
        tutorialQueue.add(tutorial)
        showTutorialIfNeeded()
    }

    private fun removeTutorial(tutorial: TutorialTrigger) {
        isTutorialShowing = false
        tutorialQueue.remove(tutorial)
        with(UncivGame.Current.settings) {
            if (!tutorialsShown.contains(tutorial.name)) {
                tutorialsShown.add(tutorial.name)
                save()
            }
        }
        showTutorialIfNeeded()
    }

    private fun showTutorialIfNeeded() {
        if (!UncivGame.Current.settings.showTutorials) return
        val tutorial = tutorialQueue.firstOrNull()
        if (tutorial == null) {
            allTutorialsShowedCallback?.invoke()
        } else if (!isTutorialShowing) {
            isTutorialShowing = true
            val texts = getTutorial(tutorial)
            tutorialRender.showTutorial(TutorialForRender(tutorial, texts)) {
                removeTutorial(tutorial)
            }
        }
    }

    private fun getTutorial(tutorial: TutorialTrigger): List<String> {
        val name = tutorial.value.replace('_', ' ').trimStart()
        return tutorials[name]?.steps ?: emptyList()
    }

    /** Wrapper for a Tutorial, supports INamed and ICivilopediaText,
     *  and already provisions for the display of an ExtraImage on top.
     *  @param name from Tutorial.name, also used for ExtraImage (with spaces replaced by underscores)
     *  @param tutorial provides [Tutorial.civilopediaText] and [Tutorial.steps] for display
     */
    //todo Replace - Civilopedia should display Tutorials directly as the RulesetObjects they are
    class CivilopediaTutorial(
        override var name: String,
        tutorial: Tutorial
    ) : INamed, SimpleCivilopediaText(
        sequenceOf(FormattedLine(extraImage = name.replace(' ', '_'))) + tutorial.civilopediaText.asSequence(),
        tutorial.steps?.asSequence() ?: emptySequence()
    )

    /** Get all Tutorials intended to be displayed in the Civilopedia
     *  as a List of wrappers supporting INamed and ICivilopediaText
     */
    fun getCivilopediaTutorials(): List<CivilopediaTutorial> {
        val civilopediaTutorials = tutorials.filter {
            !it.value.hasUnique(UniqueType.HiddenFromCivilopedia)
        }.map {
            tutorial -> CivilopediaTutorial(tutorial.key, tutorial.value)
        }
        return civilopediaTutorials
    }
}
