package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.unciv.models.gamebasics.GameBasics
import com.unciv.UnCivGame
import com.unciv.ui.cityscreen.addClickListener

open class CameraStageBaseScreen : Screen {

    var game: UnCivGame = UnCivGame.Current
    var stage: Stage

    private val tutorialTexts = mutableListOf<String>()

    private var isTutorialShowing = false

    init {
        stage = Stage(ExtendViewport(1000f, 600f
        ), batch)// FitViewport(1000,600)
        Gdx.input.inputProcessor = stage
    }


    override fun show() {}

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        stage.act()
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun pause() {}

    override fun resume() {}

    override fun hide() {}

    override fun dispose() {}

    fun displayTutorials(name: String) {
        if (game.gameInfo.tutorial.contains(name)) return
        game.gameInfo.tutorial.add(name)
        val texts = GameBasics.Tutorials[name]!!
        tutorialTexts.addAll(texts)
        if (!isTutorialShowing) displayTutorial()
    }

    private fun displayTutorial() {
        isTutorialShowing = true
        val tutorialTable = Table().pad(10f)
        tutorialTable.background(ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(Color(0x101050cf)))
        val label = Label(tutorialTexts[0], skin)
        label.setFontScale(1.5f)
        label.setAlignment(Align.center)
        tutorialTexts.removeAt(0)
        tutorialTable.add(label).pad(10f).row()
        val button = TextButton("Close", skin)
        button.addClickListener {
                tutorialTable.remove()
                if (!tutorialTexts.isEmpty())
                    displayTutorial()
                else
                    isTutorialShowing = false
            }
        tutorialTable.add(button).pad(10f)
        tutorialTable.pack()
        tutorialTable.setPosition(stage.width / 2 - tutorialTable.width / 2,
                stage.height / 2 - tutorialTable.height / 2)
        stage.addActor(tutorialTable)
    }

    companion object {
        var skin = Skin(Gdx.files.internal("skin/flat-earth-ui.json"))
        internal var batch: Batch = SpriteBatch()
    }

}


fun TextButton.disable(){
    touchable= Touchable.disabled
    color= Color.GRAY
}
fun TextButton.enable() {
    color = Color.WHITE
    touchable = Touchable.enabled
}
fun <E> List<E>.getRandom(): E = if (size == 0) throw Exception() else get((Math.random() * size).toInt())