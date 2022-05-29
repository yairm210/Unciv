package com.unciv.ui.multiplayer

import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.toLabel

class LoadDeepLinkScreen : BaseScreen() {
    init {
        val loadingLabel = "Loading...".toLabel()
        stage.addActor(loadingLabel)
        loadingLabel.center(stage)
    }
}
