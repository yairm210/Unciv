package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.unciv.Constants
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen

/** A loading screen that creates a screenshot of the current screen and adds a "Loading..." popup on top of that */
class LoadingScreen : BaseScreen() {
    val screenshot = Texture(Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight))
    init {
        val popup = Popup(stage)
        popup.addGoodSizedLabel(Constants.loading)
        popup.open()
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
