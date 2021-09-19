package com.unciv.ui.utils.DragAndDropTools

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.utils.toLabel

// generic so the body can be text, picture, video whatever
class DragAndDropRectangle<T>(header: Label, body: T, rectangleColor: Color) : Actor() {
    val _header = header
    val _body = body
    val _rectangleColor = rectangleColor
    val table = Table()
    var firstTime = true

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)

        val sr = ShapeRenderer()
        sr.setAutoShapeType(true)

        stage.addActor("fajsdk".toLabel())


        sr.begin(ShapeRenderer.ShapeType.Filled)
        sr.color = _rectangleColor
        sr.rect(x, y, 96f, 120f)
        sr.end()


    }

    fun start(stage: Stage){
        table.setPosition(400f, 50f)
        table.add(_header)
        stage.addActor(table)
    }


}