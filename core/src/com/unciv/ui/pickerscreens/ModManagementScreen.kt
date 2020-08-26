package com.unciv.ui.pickerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextArea
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.unciv.MainMenuScreen
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.mainmenu.Zip
import kotlin.concurrent.thread

class ModManagementScreen: PickerScreen() {

    val modTable = Table().apply { defaults().pad(10f) }
    val downloadTable = Table()

    init {
        setDefaultCloseAction(MainMenuScreen())
        refresh()

        topTable.add(modTable).pad(10f)


        downloadTable.add(getDownloadButton())
        topTable.add(downloadTable)
    }

    fun getDownloadButton(): TextButton {
        val downloadButton = "Download mod".toTextButton()
        downloadButton.onClick {
            val popup = Popup(this)
            val textArea = TextArea("https://github.com/yairm210/Unciv-IV-mod",skin)
            popup.add(textArea).width(stage.width/2).row()
            val downloadButton = "Download".toTextButton()
            downloadButton.onClick {
                thread { // to avoid ANRs - we've learnt our lesson from previous download-related actions
                    try {
                        downloadButton.setText("Downloading...")
                        downloadButton.disable()
                        Zip.downloadAndExtract(textArea.text+"/archive/master.zip",
                                Gdx.files.local("mods"))
                        Gdx.app.postRunnable {
                            RulesetCache.loadRulesets()
                            refresh()
                            popup.close()
                        }
                    } catch (ex:Exception){
                        Gdx.app.postRunnable {
                            ResponsePopup("Could not download mod", this)
                            popup.close()
                        }
                    }
                }
            }
            popup.add(downloadButton).row()
            popup.addCloseButton()
            popup.open()
        }
        return downloadButton
    }

    fun refresh(){
        modTable.clear()
        val currentMods = RulesetCache.values.filter { it.name != "" }
        for (mod in currentMods) {
            val button = mod.name.toTextButton().onClick {
                rightSideButton.setText("Delete [${mod.name}]".tr())
                rightSideButton.enable()
                descriptionLabel.setText(mod.getSummary())
                rightSideButton.listeners.filter { it != rightSideButton.clickListener }
                        .forEach { rightSideButton.removeListener(it) }
                rightSideButton.onClick {
                    YesNoPopup("Are you SURE you want to delete this mod?",
                            { deleteMod(mod) }, this).open()
                }
            }
            modTable.add(button).row()
        }
    }

    fun deleteMod(mod: Ruleset){
        val modFileHandle = Gdx.files.local("mods").child(mod.name)
        if(modFileHandle.isDirectory) modFileHandle.deleteDirectory()
        else modFileHandle.delete()
        RulesetCache.loadRulesets()
        refresh()
    }
}