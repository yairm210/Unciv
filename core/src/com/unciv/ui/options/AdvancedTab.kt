package com.unciv.ui.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.TranslationFileWriter
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.popup.YesNoPopup
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import java.util.*

fun advancedTab(
    optionsPopup: OptionsPopup,
    onFontChange: () -> Unit
) = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings

    addAutosaveTurnsSelectBox(this, settings)

    optionsPopup.addCheckbox(
        this, "{Show experimental world wrap for maps}\n{HIGHLY EXPERIMENTAL - YOU HAVE BEEN WARNED!}",
        settings.showExperimentalWorldWrap
    ) {
        settings.showExperimentalWorldWrap = it
    }

    addMaxZoomSlider(this, settings)

    val screen = optionsPopup.screen
    if (screen.game.platformSpecificHelper != null) {
        optionsPopup.addCheckbox(this, "Enable portrait orientation", settings.allowAndroidPortrait) {
            settings.allowAndroidPortrait = it
            // Note the following might close the options screen indirectly and delayed
            screen.game.platformSpecificHelper.allowPortrait(it)
        }
    }

    addFontFamilySelect(this, settings, optionsPopup.selectBoxMinWidth, onFontChange)

    addTranslationGeneration(this, optionsPopup)

    addSetUserId(this, settings, screen)
}

private fun addAutosaveTurnsSelectBox(table: Table, settings: GameSettings) {
    table.add("Turns between autosaves".toLabel()).left().fillX()

    val autosaveTurnsSelectBox = SelectBox<Int>(table.skin)
    val autosaveTurnsArray = Array<Int>()
    autosaveTurnsArray.addAll(1, 2, 5, 10)
    autosaveTurnsSelectBox.items = autosaveTurnsArray
    autosaveTurnsSelectBox.selected = settings.turnsBetweenAutosaves

    table.add(autosaveTurnsSelectBox).pad(10f).row()

    autosaveTurnsSelectBox.onChange {
        settings.turnsBetweenAutosaves = autosaveTurnsSelectBox.selected
        settings.save()
    }
}

private
fun addFontFamilySelect(table: Table, settings: GameSettings, selectBoxMinWidth: Float, onFontChange: () -> Unit) {
    table.add("Font family".toLabel()).left().fillX()
    val selectCell = table.add()
    table.row()

    fun loadFontSelect(fonts: Array<FontFamilyData>, selectCell: Cell<Actor>) {
        if (fonts.isEmpty) return

        val fontSelectBox = SelectBox<FontFamilyData>(table.skin)
        fontSelectBox.items = fonts

        // `FontFamilyData` implements kotlin equality contract such that _only_ the invariantName field is compared.
        // The Gdx SelectBox should honor that - but it doesn't, as it is a _kotlin_ thing to implement
        // `==` by calling `equals`, and there's precompiled _Java_ `==` in the widget code.
        // `setSelected` first calls a `contains` which can switch between using `==` and `equals` (set to `equals`)
        // but just one step later (where it re-checks whether the new selection is equal to the old one)
        // it does a hard `==`. Also, setSelection copies its argument to the selection var, it doesn't pull a match from `items`.
        // Therefore, _selecting_ an item in a `SelectBox` by an instance of `FontFamilyData` where only the `invariantName` is valid won't work properly.
        //
        // This is why it's _not_ `fontSelectBox.selected = FontFamilyData(settings.fontFamily)`
        val fontToSelect = settings.fontFamily
        fontSelectBox.selected = fonts.firstOrNull { it.invariantName == fontToSelect } // will default to first entry if `null` is passed

        selectCell.setActor(fontSelectBox).minWidth(selectBoxMinWidth).pad(10f)

        fontSelectBox.onChange {
            settings.fontFamily = fontSelectBox.selected.invariantName
            Fonts.resetFont(settings.fontFamily)
            onFontChange()
        }
    }

    launchCrashHandling("Add Font Select") {
        // This is a heavy operation and causes ANRs
        val fonts = Array<FontFamilyData>().apply {
            add(FontFamilyData.default)
            for (font in Fonts.getAvailableFontFamilyNames())
                add(font)
        }
        postCrashHandlingRunnable { loadFontSelect(fonts, selectCell) }
    }
}

private fun addMaxZoomSlider(table: Table, settings: GameSettings) {
    table.add("Max zoom out".tr()).left().fillX()
    val maxZoomSlider = UncivSlider(
        2f, 6f, 1f,
        initial = settings.maxWorldZoomOut
    ) {
        settings.maxWorldZoomOut = it
        settings.save()
    }
    table.add(maxZoomSlider).pad(5f).row()
}

private fun addTranslationGeneration(table: Table, optionsPopup: OptionsPopup) {
    if (Gdx.app.type != Application.ApplicationType.Desktop) return

    val generateTranslationsButton = "Generate translation files".toTextButton()

    val generateAction: () -> Unit = {
        optionsPopup.tabs.selectPage("Advanced")
        generateTranslationsButton.setText("Working...".tr())
        launchCrashHandling("WriteTranslations") {
            val result = TranslationFileWriter.writeNewTranslationFiles()
            postCrashHandlingRunnable {
                // notify about completion
                generateTranslationsButton.setText(result.tr())
                generateTranslationsButton.disable()
            }
        }
    }

    generateTranslationsButton.onClick(generateAction)
    optionsPopup.keyPressDispatcher[Input.Keys.F12] = generateAction
    generateTranslationsButton.addTooltip("F12", 18f)
    table.add(generateTranslationsButton).colspan(2).row()
}

private fun addSetUserId(table: Table, settings: GameSettings, screen: BaseScreen) {
    val idSetLabel = "".toLabel()
    val takeUserIdFromClipboardButton = "Take user ID from clipboard".toTextButton()
        .onClick {
            try {
                val clipboardContents = Gdx.app.clipboard.contents.trim()
                UUID.fromString(clipboardContents)
                YesNoPopup(
                    "Doing this will reset your current user ID to the clipboard contents - are you sure?",
                    {
                        settings.userId = clipboardContents
                        settings.save()
                        idSetLabel.setFontColor(Color.WHITE).setText("ID successfully set!".tr())
                    },
                    screen
                ).open(true)
                idSetLabel.isVisible = true
            } catch (ex: Exception) {
                idSetLabel.isVisible = true
                idSetLabel.setFontColor(Color.RED).setText("Invalid ID!".tr())
            }
        }
    table.add(takeUserIdFromClipboardButton).pad(5f).colspan(2).row()
    table.add(idSetLabel).colspan(2).row()
}