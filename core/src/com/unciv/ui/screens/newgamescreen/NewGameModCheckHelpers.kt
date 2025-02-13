package com.unciv.ui.screens.newgamescreen

import com.unciv.models.ruleset.validation.RulesetErrorList
import com.unciv.models.translations.tr
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.popups.popups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency

/**
 * Show a [ToastPopup] for this if severity is at least [isWarnUser][RulesetErrorList.isWarnUser].
 *
 * Adds an appropriate header to [getErrorText][RulesetErrorList.getErrorText],
 * exists mainly to centralize those strings.
 */
fun RulesetErrorList.showWarnOrErrorToast(screen: BaseScreen) {
    if (!isWarnUser()) return
    val headerText =
        if (isError()) "The mod combination you selected is «RED»incorrectly defined!«»"
        else "{The mod combination you selected «GOLD»has problems«».}\n" +
                "{You can play it, but «GOLDENROD»don't expect everything to work!«»}"
    val toastMessage = headerText.tr() + "\n\n{" + getErrorText() + "}"
    Concurrency.runOnGLThread {
        for (oldToast in screen.popups.filterIsInstance<ToastPopup>()) oldToast.close()
        ToastPopup(toastMessage, screen, 5000L)
    }
}
