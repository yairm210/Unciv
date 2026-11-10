package com.unciv.ui.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.ui.popups.popups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.popups.LoadingPopup

/** A loading screen that creates a screenshot of the current screen and adds a "Loading..." popup on top of that */
class LoadingScreen(
    previousScreen: BaseScreen? = null
) : BaseScreen() {
    private val screenshot: Texture
    private var loadingPopup: LoadingPopup? = null

    init {
        screenshot = takeScreenshot(previousScreen)
        val image = object : Image(
            TextureRegion(
                screenshot,
                0,
                screenshot.height,
                screenshot.width,
                -screenshot.height
            )
        ) {
            override fun draw(batch: Batch, parentAlpha: Float) {
                // The screenshot RGB is already composited. Do not blend its alpha again.
                val wasBlendingEnabled = batch.isBlendingEnabled
                batch.disableBlending()
                try {
                    super.draw(batch, parentAlpha)
                } finally {
                    if (wasBlendingEnabled) batch.enableBlending()
                }
            }
        }
        image.width = stage.width
        image.height = stage.height
        stage.addActor(image)
        stage.addAction(Actions.sequence(
            Actions.delay(1000f),
            Actions.run {
                loadingPopup = LoadingPopup(this)
            }
        ))
    }

    private fun takeScreenshot(previousScreen: BaseScreen?): Texture {
        if (previousScreen != null) {
            for (popup in previousScreen.popups) popup.isVisible = false
            previousScreen.render(Gdx.graphics.deltaTime)
        }
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        val screenshot = try {
            Texture(pixmap)
        } finally {
            pixmap.dispose()
        }

        if (previousScreen != null) {
            for (popup in previousScreen.popups) popup.isVisible = true
        }
        return screenshot
    }


    override fun dispose() {
        screenshot.dispose()
        stage.root.clearActions() // super.dispose does that too, but prevent race condition
        loadingPopup?.close() // Prevent leak due to EventReceiver reference
        super.dispose()
    }
}
