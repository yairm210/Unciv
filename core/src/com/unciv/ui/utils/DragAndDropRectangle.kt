package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.maps.objects.RectangleMapObject
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import java.util.concurrent.RecursiveTask

// generic so the body can be text, picture, video whatever
class DragAndDropRectangle<T>(header: Label, body: T, rectangleColor: Color, screenStage: Stage) : Actor() {
    val _header = header
    val _body = body
    val _rectangleColor = rectangleColor
    val _stage = screenStage

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        val sr = ShapeRenderer()
        sr.setAutoShapeType(true)
        println(color)

        sr.begin(ShapeRenderer.ShapeType.Filled)
        sr.color = _rectangleColor
        sr.rect(48f, 80f, 96f, 120f)
        sr.end()

        val table = Table()
        table.setPosition(x, y)
        table.add(_header)

        _stage.addActor(table)
    }


}