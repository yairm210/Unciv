package com.unciv.ui.screens.basescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.models.TutorialTrigger
import com.unciv.models.ruleset.Tutorial
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText


class TutorialController(screen: BaseScreen) {

    private val tutorialQueue = mutableSetOf<TutorialTrigger>()
    private var isTutorialShowing = false
    var allTutorialsShowedCallback: (() -> Unit)? = null
    private val tutorialRender = TutorialRender(screen)

    private val tutorials: LinkedHashMap<String, Tutorial> = loadTutorialsFromJson()

    companion object {
        // static to allow use from TutorialTranslationTests
        fun loadTutorialsFromJson(includeMods: Boolean = true): LinkedHashMap<String, Tutorial> {
            val result = linkedMapOf<String, Tutorial>()
            for (file in tutorialFiles(includeMods)) {
                json().fromJsonFile(Array<Tutorial>::class.java, file)
                    .associateByTo(result) { it.name }
            }
            return result
        }

        private fun tutorialFiles(includeMods: Boolean) = sequence<FileHandle> {
            yield(Gdx.files.internal("jsons/Tutorials.json"))
            if (!includeMods) return@sequence
            val mods = UncivGame.Current.gameInfo?.ruleset?.mods
                ?: return@sequence
            val files = mods.asSequence()
                .map { UncivGame.Current.files.getLocalFile("mods/$it/jsons/Tutorials.json") }
            yieldAll(files.filter { it.exists() })
        }
    }

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

    /** Get all Tutorials to be displayed in the Civilopedia */
    fun getCivilopediaTutorials(): Collection<ICivilopediaText> {
        // Todo This is essentially an 'un-private' kludge and the accessor
        //      in CivilopediaCategories desperately needs independence from TutorialController:
        //      Move storage to RuleSet someday?
        return tutorials.values +
            // Global Uniques
            listOfNotNull(UncivGame.Current.gameInfo?.getGlobalUniques()?.takeIf { it.hasUniques() })
    }
}


data class TutorialForRender(val tutorial: TutorialTrigger, val texts: List<String>)

class TutorialRender(private val screen: BaseScreen) {

    fun showTutorial(tutorial: TutorialForRender, closeAction: () -> Unit) {
        showDialog(tutorial.tutorial.name, tutorial.texts, closeAction)
    }

    private fun showDialog(tutorialName: String, texts: List<String>, closeAction: () -> Unit) {
        if (texts.isEmpty()) return closeAction()

        val tutorialPopup = Popup(screen)
        tutorialPopup.name = Constants.tutorialPopupNamePrefix + tutorialName

        val externalImage = ImageGetter.findExternalImage(tutorialName)
        if (externalImage != null) {
            tutorialPopup.add(ImageGetter.getExternalImage(externalImage)).row()
        }

        tutorialPopup.addGoodSizedLabel(texts[0]).row()

        tutorialPopup.addCloseButton(additionalKey = KeyCharAndCode.SPACE) {
            tutorialPopup.remove()
            showDialog(tutorialName, texts.subList(1, texts.size), closeAction)
        }
        tutorialPopup.open()
    }
}
