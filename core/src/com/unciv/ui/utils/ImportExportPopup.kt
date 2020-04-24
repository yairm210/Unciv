package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.logic.GameSaver
import com.unciv.models.translations.tr

class ImportExportPopup (screen: CameraStageBaseScreen) : Popup(screen) {
    private val resultLabel = Label("", skin)
    private val configCheckBox = CheckBox("Settings".tr(), skin)
    private val autosaveCheckBox = CheckBox("Autosave".tr(), skin)
    private val savesCheckBox = CheckBox("Saves".tr(), skin)
    private val mapsCheckBox = CheckBox("Maps".tr(), skin)
    private val modsCheckBox = CheckBox("Mods".tr(), skin)
    private val musicCheckBox = CheckBox("Music".tr(), skin)
    private val exportButton = ImportExportButton("OtherIcons/Up", "Export", skin)
    private val importButton = ImportExportButton("OtherIcons/Down", "Import", skin)

    private var parameters = ImportExportParameters.defaultedClone(screen.game.settings.importExportParameters)

    init {
        defaults().pad(10f)

        configCheckBox.isChecked = parameters.config
        autosaveCheckBox.isChecked = parameters.autosave
        savesCheckBox.isChecked = parameters.saves
        mapsCheckBox.isChecked = parameters.maps
        modsCheckBox.isChecked = parameters.mods
        musicCheckBox.isChecked = parameters.music

        configCheckBox.onChange (::onParamChange)
        autosaveCheckBox.onChange (::onParamChange)
        savesCheckBox.onChange (::onParamChange)
        mapsCheckBox.onChange (::onParamChange)
        modsCheckBox.onChange (::onParamChange)
        musicCheckBox.onChange (::onParamChange)
        onParamChange()

        add("Import / Export".toLabel(fontSize = 24)).minWidth(screen.stage.width * 0.25f).colspan(2).row()

        addSeparator()
        add(configCheckBox)
        add(autosaveCheckBox).row()
        add(savesCheckBox)
        add(mapsCheckBox).row()
        add(modsCheckBox)
        add(musicCheckBox).row()

        // Import".toTextButton().apply { color = ImageGetter.getBlue() }
        exportButton.onClick {
            enableButtons(false)
            screen.game.importExport?.requestExport(getParams()) { status:ImportExportStatus, msg: String ->
                resultLabel.setText(msg)
                if (status != ImportExportStatus.Progress)
                    onParamChange()
            }
        }
        add(exportButton)
        importButton.onClick {
            enableButtons(false)
            screen.game.importExport?.requestImport(getParams()) { status:ImportExportStatus, msg: String ->
                resultLabel.setText(msg)
                if (status == ImportExportStatus.Success && this.parameters.config ) {
                    screen.game.settings = GameSaver.getGeneralSettings()
                }
                if (status != ImportExportStatus.Progress)
                    onParamChange()
            }
        }
        add(importButton).row()

        resultLabel.setAlignment(Align.center)
        add(resultLabel).colspan(2).pad(5f).growX().row()
        addSeparator()

        addCloseButton {
            screen.game.settings.importExportParameters = getParams().nullIfDefault()
        }.colspan(2)

        pack()

        open(true)
    }

    private fun onParamChange() = enableButtons(getParams().any())

    private fun enableButtons(enable: Boolean) {
        exportButton.isEnabled = enable
        importButton.isEnabled = enable
    }

    private fun getParams(): ImportExportParameters {
        parameters = ImportExportParameters(
                configCheckBox.isChecked, savesCheckBox.isChecked, autosaveCheckBox.isChecked,
                mapsCheckBox.isChecked, modsCheckBox.isChecked, musicCheckBox.isChecked )
        return parameters
    }
}

private class ImportExportButton(imgPath: String, text: String, skin: Skin) : Button(skin) {
    init {
        color= ImageGetter.getBlue()
        defaults().pad(15f)
        add (ImageGetter.getImage(imgPath).surroundWithCircle(30f) ).size(30f)
        add (text.toLabel())
    }
}