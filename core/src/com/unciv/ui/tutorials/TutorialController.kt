package com.unciv.ui.tutorials

import com.badlogic.gdx.utils.Array
import com.unciv.JsonParser
import com.unciv.UncivGame
import com.unciv.models.Tutorial
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.INamed
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.SimpleCivilopediaText
import com.unciv.ui.utils.BaseScreen

class TutorialController(screen: BaseScreen) {

    private val tutorialQueue = mutableSetOf<Tutorial>()
    private var isTutorialShowing = false
    var allTutorialsShowedCallback: (() -> Unit)? = null
    private val tutorialRender = TutorialRender(screen)
    private val tutorials = JsonParser().getFromJson(LinkedHashMap<String, Array<String>>().javaClass, "jsons/Tutorials.json")

    fun showTutorial(tutorial: Tutorial) {
        tutorialQueue.add(tutorial)
        showTutorialIfNeeded()
    }

    private fun removeTutorial(tutorial: Tutorial) {
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

    private fun getTutorial(tutorial: Tutorial): Array<String> {
        return tutorials[tutorial.value] ?: Array()
    }

    /** Wrapper for a Tutorial, supports INamed and ICivilopediaText,
     *  and already provisions for the display of an ExtraImage on top.
     *  @param rawName from Tutorial.value, with underscores (this wrapper replaces them with spaces)
     *  @param lines   Array of lines exactly as stored in a TutorialController.tutorials MapEntry
     */
    class CivilopediaTutorial(
        rawName: String,
        lines: Array<String>
    ) : INamed, SimpleCivilopediaText(
        sequenceOf(FormattedLine(extraImage = rawName)),
        lines.asSequence()
    ) {
        override var name = rawName.replace("_", " ")
    }

    /** Get all Tutorials intended to be displayed in the Civilopedia
     *  as a List of wrappers supporting INamed and ICivilopediaText
     */
    fun getCivilopediaTutorials(ruleset: Ruleset): List<CivilopediaTutorial> {
        val civilopediaTutorials = tutorials.filter {
            Tutorial.findByName(it.key)!!.isCivilopedia
        }.map { tutorial ->
            val lines = tutorial.value
                
            if (tutorial.key == "Unhappiness") {
                for (unhappinessEffect in ruleset.unhappinessEffects.values.sortedByDescending { it.unhappiness }) {
                    lines.add("\n${unhappinessEffect.toCivilopediaLines()}")
                }
            }
            
            CivilopediaTutorial(tutorial.key, lines)
        }
        return civilopediaTutorials
    }
}
