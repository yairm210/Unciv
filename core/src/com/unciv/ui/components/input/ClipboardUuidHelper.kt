package com.unciv.ui.components.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.unciv.Constants
import com.unciv.logic.IdChecker
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.popups.Popup

object ClipboardUuidHelper {
    fun pasteUuidIntoTextField(
        stage: Stage,
        targetTextField: UncivTextField,
        onNoUuidFound: () -> Unit = {},
        onAfterPaste: (() -> Unit)? = null
    ) {
        chooseUuidFromClipboard(
            stage = stage,
            onUuidSelected = {
                targetTextField.text = it
                onAfterPaste?.invoke()
            },
            onNoUuidFound = onNoUuidFound
        )
    }

    fun chooseUuidFromClipboard(
        stage: Stage,
        onUuidSelected: (String) -> Unit,
        onNoUuidFound: () -> Unit,
        onClipboardReadFailed: (() -> Unit)? = null
    ) {
        val clipboardText = try {
            Gdx.app.clipboard.contents ?: ""
        } catch (_: Throwable) {
            (onClipboardReadFailed ?: onNoUuidFound).invoke()
            return
        }

        val candidates = IdChecker.extractUuidCandidates(clipboardText.take(Constants.clipboardScanLengthLimit))
        when (candidates.size) {
            0 -> onNoUuidFound()
            1 -> onUuidSelected(candidates.first())
            else -> showUuidSelectionPopup(stage, candidates, onUuidSelected)
        }
    }

    private fun showUuidSelectionPopup(
        stage: Stage,
        candidates: List<String>,
        onUuidSelected: (String) -> Unit
    ) {
        val popup = Popup(stage, Popup.Scrollability.All)
        popup.addGoodSizedLabel("Multiple IDs found in clipboard. Please select one.".tr())
        popup.row()

        for (uuid in candidates) {
            popup.add(uuid.toTextButton().onClick {
                popup.close()
                onUuidSelected(uuid)
            }).fillX().pad(4f).row()
        }

        popup.addCloseButton()
        popup.open(true)
    }
}
