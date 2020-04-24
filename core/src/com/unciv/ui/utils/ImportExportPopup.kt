package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.unciv.logic.GameSaver
import com.unciv.models.translations.tr

class ImportExportPopup (screen: CameraStageBaseScreen) : Popup(screen) {
    private val resultLabel = Label("", skin)

    private val configCheckBox = CheckBox("Settings".tr(), skin)
    private val autosaveCheckBox = CheckBox("Autosave".tr(), skin)
    private val savesCheckBox = CheckBox("Saves".tr(), skin)
    private val mapsCheckBox = CheckBox("Maps".tr(), skin)

    private var runningParams = ImportExportParameters(false)

    init {
        defaults().pad(10f)

        val parameters = ImportExportParameters()       // could be saved in settings
        configCheckBox.isChecked = parameters.config
        autosaveCheckBox.isChecked = parameters.autosave
        savesCheckBox.isChecked = parameters.saves
        mapsCheckBox.isChecked = parameters.maps

        addGoodSizedLabel("Import / Export", 24).colspan(2).row()
        add(configCheckBox).colspan(2).row()
        add(autosaveCheckBox).colspan(2).row()
        add(savesCheckBox).colspan(2).row()
        add(mapsCheckBox).colspan(2).row()

        addButton("Export") {
            screen.game.importExport?.requestExport(getParams()) { _: Boolean, msg: String ->
                resultLabel.setText(msg)
            }
        }
        addButton("Import") {
            screen.game.importExport?.requestImport(getParams()) { success: Boolean, msg: String ->
                resultLabel.setText(msg)
                if (success && runningParams.config ) {
                    screen.game.settings = GameSaver.getGeneralSettings()
                }
            }
        }

        resultLabel.setAlignment(Align.center)
        add(resultLabel).colspan(2).pad(5f).growX().row()

        addButton("Close") {
            close()
        }.colspan(2)

        pack()
        open(true)
    }

    private fun getParams(): ImportExportParameters {
        runningParams = ImportExportParameters(configCheckBox.isChecked, savesCheckBox.isChecked, autosaveCheckBox.isChecked, mapsCheckBox.isChecked)
        return runningParams
    }

    // game.importExport!!.requestExport(ImportExportParameters())
}