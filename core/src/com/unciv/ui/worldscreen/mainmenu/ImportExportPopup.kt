package com.unciv.ui.worldscreen.mainmenu

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameSaver
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.Popup
import com.unciv.ui.utils.center
import com.unciv.ui.worldscreen.WorldScreen
import java.io.File
import java.lang.Exception
import kotlin.concurrent.thread

class ImportExportPopup(screen: CameraStageBaseScreen) : Popup(screen) {
    // Copy from/to local <-> external (local is not user-accessible under /data/user/)
    private val settings = UncivGame.Current.settings
    private val folderNameEditor = TextField(settings.importExportFolder, skin)
    private val resultLabel = Label("", skin)
    private var imported = false

    init {
        addGoodSizedLabel("Import / Export", 24).row()
        addGoodSizedLabel("Folder in internal storage:").row()
        add(folderNameEditor).growX().row()

        addButton("Import") {
            val path = folderNameEditor.text
            val file = Gdx.files.external(path)
            if (!checkNameAllowed(folderNameEditor.text)) {
                resultLabel.setText(disallowedMessage.tr())
            } else if (!(file.exists() && file.isDirectory)) {
                resultLabel.setText("Folder name must specify an existing directory.".tr())
            } else if (!copyList.any { entry: Map.Entry<String, Boolean> -> file.child(entry.key).exists() && file.child(entry.key).isDirectory == entry.value }) {
                resultLabel.setText("Specified Folder contains no Unciv files.".tr())
            } else {
                copy (file, Gdx.files.local("")) {
                    resultLabel.setText(it)
                    imported = true
                }
            }
        }

        addButton("Export") {
            val path = folderNameEditor.text
            val file = Gdx.files.external(path)
            if (!checkNameAllowed(folderNameEditor.text)) {
                resultLabel.setText(disallowedMessage.tr())
            } else if (file.exists() && !file.isDirectory) {
                resultLabel.setText("Folder name cannot specify an existing file - only directories.".tr())
            } else {
                copy (Gdx.files.local(""), file) {
                    resultLabel.setText(it.tr())
                }
            }
        }

        resultLabel.setAlignment(Align.center)
        add(resultLabel).pad(5f).growX().row()

        addButton("Close") {
            if (checkNameAllowed(folderNameEditor.text))
                settings.importExportFolder = folderNameEditor.text
            if (imported) {
                UncivGame.Current.settings = GameSaver().getGeneralSettings()
                UncivGame.Current.worldScreen = WorldScreen(UncivGame.Current.worldScreen.viewingCiv)
                UncivGame.Current.setWorldScreen()
            }
            close()
        }

        pack()
        center(UncivGame.Current.worldScreen.stage)
    }

    companion object {
        private val disallowedFolders = hashSetOf(
                "Android",          // very bad
                "Alarms",           // mildly bad
                "Notifications",
                "Ringtones",
                "DCIM",
                "Movies",
                "Pictures")
        private const val disallowedMessage = "Folder name not acceptable."
        private val copyList = hashMapOf<String,Boolean> (
                //Pair(GameSaver.settingsFile, false),
                //Pair(GameSaver.saveFilesFolder, true),
                Pair("GameSettings.json", false),
                Pair("SaveFiles", true),
                Pair("maps", true)
            )   // Exclude Multiplayer and mods?

        private fun checkNameAllowed(path: String): Boolean {
            if (path.isEmpty() || path[0] == '.') return false
            val firstLevel = path.replaceAfter(File.separatorChar,"")
            return (firstLevel !in disallowedFolders)
        }

        private fun copy (from: FileHandle, to: FileHandle, done: (String)->Unit) {
            done.invoke("Working...")
            thread(name = "ImportExport", isDaemon = true) {
                to.mkdirs()
                try {
                    copyList.forEach { entry ->
                        if (entry.value) to.child(entry.key).mkdirs()
                        from.child(entry.key).copyTo(to)
                    }
                    done.invoke("Done.")
                } catch (ex: Exception) {
                    done.invoke("Error: [${ex.localizedMessage}]")
                }
            }
        }
    }
}