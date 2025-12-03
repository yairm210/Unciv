package com.unciv.ui.screens.modmanager

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.Constants
import com.unciv.logic.UncivShowableException
import com.unciv.models.translations.tr
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.clearActivationActions
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.CoroutineScope

internal class DownloadMissingModsPopup(
    stageToShowOn: Stage,
    private val missingMods: Iterable<String>,
    private val onModDownloaded: (String)->Unit,
    private val onDownloadFinished: ()->Unit
) : ConfirmPopup(stageToShowOn, "", Constants.yes, true, action = {}) {
    private var newMods = false
    val labels = mutableMapOf<String, Label>()

    init {
        topTable.clear()
        addGoodSizedLabel("This mod requires the following missing mods:").row()
        addGoodSizedLabel(missingMods.joinToString()).row()
        addGoodSizedLabel("{${LoadGameScreen.downloadMissingMods}}{?}", 24).padTop(10f).row()
        bottomTable.children.last().also {
            it.clearActivationActions(ActivationTypes.Tap)
            it.onActivation(::startDownload)
        }
    }

    private fun startDownload() {
        clear()
        for (mod in missingMods) {
            val label = Label("$mod...", skin)
            add(label).left().width(stageToShowOn.width / 2).row()
            labels[mod] = label
        }

        val job = Concurrency.runOnNonDaemonThreadPool(LoadGameScreen.downloadMissingMods) {
            downloadTask()
        }

        addButton(Constants.cancel) {
            job.cancel()
        }
    }

    private suspend fun CoroutineScope.downloadTask() {
        try {
            LoadGameScreen.loadMissingMods(
                missingMods,
                onProgress = { mod, state, percent ->
                    val labelText = "{$mod}: ${state.message(percent)}".tr()
                    launchOnGLThread {
                        labels[mod]?.setText(labelText)
                    }
                },
                onModDownloaded = { mod ->
                    newMods = true
                    launchOnGLThread {
                        labels[mod]?.setText("[$mod] Downloaded!".tr())
                        onModDownloaded(mod)
                    }
                },
                onCompleted = {
                    showSuccessOrCancel("Missing mods are downloaded successfully.", Color.OLIVE) {
                        addOKButton { close() }
                    }
                },
                onCancelled = {
                    showSuccessOrCancel("Mod download cancelled!", Color.ORANGE) {
                        addCloseButton()
                    }
                }
            )
        } catch(ex: UncivShowableException) {
            showFailure(ex.message)
        } catch(_: Throwable) {
            showFailure("Could not load the missing mods!")
        }
    }

    private fun CoroutineScope.showSuccessOrCancel(message: String, color: Color, action: () -> Unit) {
        launchOnGLThread {
            addGoodSizedLabel(message, color = color).padTop(20f).row()
            onDownloadFinished()
            bottomTable.clear()
            action()
        }
    }

    private fun CoroutineScope.showFailure(message: String) {
        launchOnGLThread {
            addGoodSizedLabel(message, color = Color.RED).padTop(20f).row()
            bottomTable.clear()
            addButton("Retry", action = ::startDownload)
        }
    }
}
