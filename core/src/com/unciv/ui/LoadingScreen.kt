package com.unciv.ui

import com.unciv.Constants
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.toLabel

class LoadingScreen : BaseScreen() {
    init {
        val loadingLabel = Constants.loading.toLabel()
        stage.addActor(loadingLabel)
        loadingLabel.center(stage)
    }
}
