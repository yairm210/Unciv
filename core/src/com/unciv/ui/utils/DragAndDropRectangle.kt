package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
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
class DragAndDropRectangle(header: String, body: String, rectangleColor: Color) : Actor() {
    val _header = header
    val _body = body
    val _rectangleColor = rectangleColor
    val table = Table()
    var firstTime = true

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)

        val sr = ShapeRenderer()
        sr.setAutoShapeType(true)

        val fr = BitmapFont()

        fr.draw(batch, _header, x+10, y+10)

        sr.begin(ShapeRenderer.ShapeType.Filled)
        sr.color = _rectangleColor
        sr.rect(x, y, 96f, 120f)
        sr.end()


    }

    private fun start(){
        table.setPosition(400f, 50f)
        table.add(_header)
        stage.addActor(table)
    }


}