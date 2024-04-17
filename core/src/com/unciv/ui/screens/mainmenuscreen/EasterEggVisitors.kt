package com.unciv.ui.screens.mainmenuscreen

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Align
import com.unciv.ui.images.ImageGetter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class EasterEggVisitors(stage: Stage, name: String) : WidgetGroup() {
    private val images = sequence {
        for (index in 1..99) {
            val textureName = "EasterEggs/$name$index"
            if (!ImageGetter.imageExists(textureName)) break
            yield(ImageGetter.getImage(textureName))
        }
    }.toList()
    private val centerX = stage.width / 2
    private val centerY = stage.height / 2
    private val placementRadius = sqrt(
        centerX * centerX + centerY * centerY
            + (images.maxOfOrNull { it.prefWidth } ?: 0f).pow(2)
            + (images.maxOfOrNull { it.prefHeight } ?: 0f).pow(2)
    )

    init {
        if (images.isNotEmpty()) {
            setFillParent(true)
            stage.addActor(this)
            nextImage()
        }
    }

    private fun nextImage() {
        addAction(
            Actions.delay(Random.nextFloat() * 4f + 2f, Actions.run {
                val image = images.random()
                val angle = Random.nextDouble() * 2.0 * PI
                val angle2 = angle + (Random.nextDouble() * 0.3333 - 0.1667) * PI // +/- 30° so they won't always cross the center exactly
                val offsetX = (placementRadius * cos(angle)).toFloat()
                val offsetY = (placementRadius * sin(angle)).toFloat()
                val moveToX = (placementRadius * cos(angle2)).toFloat()
                val moveToY = (placementRadius * sin(angle2)).toFloat()
                image.setPosition(centerX - offsetX, centerY - offsetY, Align.center)
                addActor(image)
                image.animate(offsetX + moveToX, offsetY + moveToY)
            })
        )
    }

    private fun Image.animate(moveByX: Float, moveByY: Float) {
        val duration = Random.nextFloat() * 8f + 3f
        val interpolation = when(Random.nextInt(6)) {
            1 -> Interpolation.bounce
            2 -> Interpolation.swing
            3 -> Interpolation.smoother
            4 -> Interpolation.fastSlow
            5 -> Interpolation.slowFast
            else -> Interpolation.linear
        }
        color.a = 1f
        addAction(Actions.sequence(
            Actions.moveBy(moveByX, moveByY, duration, interpolation),
            Actions.fadeOut(0.2f),
            Actions.run(::nextImage),
            Actions.removeActor()
        ))
    }
}
