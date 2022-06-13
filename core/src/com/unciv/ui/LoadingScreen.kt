package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.unciv.Constants
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.closeAllPopups
import com.unciv.ui.popup.popups
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.toLabel

/** A loading screen that creates a screenshot of the current screen and adds a "Loading..." popup on top of that */
class LoadingScreen(
    previousScreen: BaseScreen? = null
) : BaseScreen() {
    val screenshot: Texture
    init {
        screenshot = takeScreenshot(previousScreen)
        val popup = Popup(stage)
        popup.add(Constants.loading.toLabel())
        popup.open()
    }

    private fun takeScreenshot(previousScreen: BaseScreen?): Texture {
        if (previousScreen != null) {
            for (popup in previousScreen.popups) popup.isVisible = false
            previousScreen.render(Gdx.graphics.getDeltaTime())
        }
        val screenshot = Texture(Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight))
        if (previousScreen != null) {
            for (popup in previousScreen.popups) popup.isVisible = true
        }
        return screenshot
    }

    override fun render(delta: Float) {
        val camera = stage.viewport.camera
        camera.update()
        val batch = stage.batch as SpriteBatch
        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.draw(screenshot, 0f, 0f, camera.viewportWidth, camera.viewportHeight, 0, 0, screenshot.width, screenshot.height, false, true)
        stage.root.draw(batch, 1f)
        batch.end()
    }
}
