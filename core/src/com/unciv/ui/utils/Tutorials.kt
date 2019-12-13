package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Array
import com.unciv.UncivGame
import com.unciv.models.ruleset.tr
import com.unciv.ui.worldscreen.optionstable.PopupTable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.set

class Tutorials{

    class Tutorial(var name: String, var texts: ArrayList<String>)

    private val tutorialTexts = mutableListOf<Tutorial>()

    var isTutorialShowing = false


    fun displayTutorials(name: String, screen:CameraStageBaseScreen) {
        if (!UncivGame.Current.settings.showTutorials) return
        if (UncivGame.Current.settings.tutorialsShown.contains(name)) return

        var texts: ArrayList<String>
        try {
            texts = getTutorials(name, UncivGame.Current.settings.language)
        } catch (ex: Exception) {
            texts = ArrayList<String>().apply { add("Could not find matching tutorial: $name") }
        }
        if(tutorialTexts.any { it.name==name }) return // currently showing
        tutorialTexts.add(Tutorial(name, texts))
        if (!isTutorialShowing) displayTutorial(screen)
    }

    fun getTutorialsOfLanguage(language: String): HashMap<String, ArrayList<String>> {
        val filePath = "jsons/Tutorials/Tutorials_$language.json"
        if(!Gdx.files.internal(filePath).exists()) return hashMapOf()

        // ...Yes. Disgusting. I wish I didn't have to do this.
        val x = LinkedHashMap<String, Array<Array<String>>>()
        val tutorials: LinkedHashMap<String, Array<Array<String>>> =
                UncivGame.Current.ruleset.getFromJson(x.javaClass, filePath)
        val tutorialMap = HashMap<String, ArrayList<String>>()
        for (tutorial in tutorials){
            val list = ArrayList<String>()
            for(paragraph in tutorial.value)
                list += paragraph.joinToString("\n")
            tutorialMap[tutorial.key] = list
        }
        return tutorialMap
    }

    fun getTutorials(name:String, language:String): ArrayList<String> {
        val tutorialsOfLanguage = getTutorialsOfLanguage(language)
        if(tutorialsOfLanguage.containsKey(name)) return tutorialsOfLanguage[name]!!
        return getTutorialsOfLanguage("English")[name]!!
    }

    private fun displayTutorial(screen:CameraStageBaseScreen) {
        isTutorialShowing = true
        val tutorialTable = PopupTable(screen)
        val currentTutorial = tutorialTexts[0]

        if(Gdx.files.internal("ExtraImages/"+currentTutorial.name).exists())
            tutorialTable.add(ImageGetter.getExternalImage(currentTutorial.name)).row()

        tutorialTable.addGoodSizedLabel(currentTutorial.texts[0]).row()
        val button = TextButton("Close".tr(), CameraStageBaseScreen.skin)

        currentTutorial.texts.removeAt(0)
        if(currentTutorial.texts.isEmpty()) tutorialTexts.removeAt(0)

        button.onClick {
            tutorialTable.remove()
            if(!UncivGame.Current.settings.tutorialsShown.contains(currentTutorial.name)) {
                UncivGame.Current.settings.tutorialsShown.add(currentTutorial.name)
                UncivGame.Current.settings.save()
            }
            if (tutorialTexts.isNotEmpty())
                displayTutorial(screen)
            else
                isTutorialShowing = false
        }
        tutorialTable.add(button).pad(10f)
        tutorialTable.open()
    }
}