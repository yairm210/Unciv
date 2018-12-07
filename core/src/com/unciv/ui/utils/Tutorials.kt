package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import java.util.LinkedHashMap
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.set

class Tutorials{

    class Tutorial(var name: String, var texts: ArrayList<String>)

    private val tutorialTexts = mutableListOf<Tutorial>()

    private var isTutorialShowing = false


    fun displayTutorials(name: String, stage: Stage) {
        if (UnCivGame.Current.settings.tutorialsShown.contains(name)) return
        UnCivGame.Current.settings.tutorialsShown.add(name)
        UnCivGame.Current.settings.save()
        val texts = getTutorials(name, UnCivGame.Current.settings.language)
        tutorialTexts.add(Tutorial(name,texts))
        if (!isTutorialShowing) displayTutorial(stage)
    }

    fun getTutorialsOfLanguage(language: String): HashMap<String, ArrayList<String>> {
        if(!Gdx.files.internal("jsons/Tutorials_$language.json").exists()) return hashMapOf()

        // ...Yes. Disgusting. I wish I didn't have to do this.
        val x = LinkedHashMap<String, Array<Array<String>>>()
        val tutorials: LinkedHashMap<String, Array<Array<String>>> =
                GameBasics.getFromJson(x.javaClass, "Tutorials_$language")
        val tutorialMap = HashMap<String, ArrayList<String>>()
        for (tut in tutorials){
            val list = ArrayList<String>()
            for(paragraph in tut.value)
                list += paragraph.joinToString("\n")
            tutorialMap[tut.key] = list
        }
        return tutorialMap
    }

    fun getTutorials(name:String, language:String): ArrayList<String> {
        val tutorialsOfLanguage = getTutorialsOfLanguage(language)
        if(tutorialsOfLanguage.containsKey(name)) return tutorialsOfLanguage[name]!!
        return getTutorialsOfLanguage("English")[name]!!
    }

    private fun displayTutorial(stage: Stage) {
        isTutorialShowing = true
        val tutorialTable = Table().pad(10f)
        tutorialTable.background = ImageGetter.getBackground(Color(0x101050cf))
        val currentTutorial = tutorialTexts[0]
        val label = Label(currentTutorial.texts[0], CameraStageBaseScreen.skin)
        label.setAlignment(Align.center)
        if(Gdx.files.internal("ExtraImages/"+currentTutorial.name+".png").exists())
            tutorialTable.add(Table().apply { add(ImageGetter.getExternalImage(currentTutorial.name)) }).row()
        tutorialTable.add(label).pad(10f).row()
        val button = TextButton("Close".tr(), CameraStageBaseScreen.skin)

        currentTutorial.texts.removeAt(0)
        if(currentTutorial.texts.isEmpty()) tutorialTexts.removeAt(0)

        button.onClick {
            tutorialTable.remove()
            if (!tutorialTexts.isEmpty())
                displayTutorial(stage)
            else
                isTutorialShowing = false
        }
        tutorialTable.add(button).pad(10f)
        tutorialTable.pack()
        tutorialTable.center(stage)
        stage.addActor(tutorialTable)
    }
}