package com.unciv.ui.pickerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextArea
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.MainMenuScreen
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.mainmenu.Github
import kotlin.concurrent.thread

class ModManagementScreen: PickerScreen() {

    val modTable = Table().apply { defaults().pad(10f) }
    val downloadTable = Table().apply { defaults().pad(10f) }
    val modActionTable = Table().apply { defaults().pad(10f) }

    init {
        setDefaultCloseAction(MainMenuScreen())
        refreshModTable()

        topTable.add("Current mods".toLabel())
        topTable.add("Downloadable mods".toLabel())
//        topTable.add("Mod actions")
        topTable.row()


        topTable.add(ScrollPane(modTable)).height(scrollPane.height*0.8f).pad(10f)

        downloadTable.add(getDownloadButton()).row()

        thread {
            val repoList: ArrayList<Github.Repo>
            try {
                repoList = Github.tryGetGithubReposWithTopic()
            } catch (ex: Exception) {
                Gdx.app.postRunnable {
                    ToastPopup("Could not download mod list", this)
                }
                return@thread
            }

            Gdx.app.postRunnable {
                for (repo in repoList) {
                    if (repo.default_branch != "master") continue
                    repo.name = repo.name.replace('-', ' ')
                    val downloadButton = repo.name.toTextButton()
                    downloadButton.onClick {
                        descriptionLabel.setText(repo.description + "\n" + "[${repo.stargazers_count}]✯".tr())
                        removeRightSideClickListeners()
                        rightSideButton.enable()
                        rightSideButton.setText("Download [${repo.name}]".tr())
                        rightSideButton.onClick {
                            rightSideButton.setText("Downloading...".tr())
                            rightSideButton.disable()
                            downloadMod(repo.svn_url) {
                                rightSideButton.setText("Downloaded!".tr())
                            }
                        }
                    }
                    downloadTable.add(downloadButton).row()
                }
                downloadTable.pack()
                (downloadTable.parent as ScrollPane).actor = downloadTable
            }
        }

        topTable.add(ScrollPane(downloadTable)).height(scrollPane.height*0.8f)//.size(downloadTable.width, topTable.height)

        topTable.add(modActionTable)
    }

    fun getDownloadButton(): TextButton {
        val downloadButton = "Download mod from URL".toTextButton()
        downloadButton.onClick {
            val popup = Popup(this)
            val textArea = TextArea("https://github.com/...",skin)
            popup.add(textArea).width(stage.width/2).row()
            val actualDownloadButton = "Download".toTextButton()
            actualDownloadButton.onClick {
                actualDownloadButton.setText("Downloading...".tr())
                actualDownloadButton.disable()
                downloadMod(textArea.text) { popup.close() }
            }
            popup.add(actualDownloadButton).row()
            popup.addCloseButton()
            popup.open()
        }
        return downloadButton
    }

    fun downloadMod(gitRepoUrl:String, postAction:()->Unit={}){
        thread { // to avoid ANRs - we've learnt our lesson from previous download-related actions
            try {
                Github.downloadAndExtract("$gitRepoUrl/archive/master.zip",
                        Gdx.files.local("mods"))
                Gdx.app.postRunnable {
                    ToastPopup("Downloaded!", this)
                    RulesetCache.loadRulesets()
                    refreshModTable()
                }
            } catch (ex:Exception){
                Gdx.app.postRunnable {
                    ToastPopup("Could not download mod", this)
                }
            }
            finally {
                postAction()
            }
        }
    }

    fun refreshModTable(){
        modTable.clear()
        val currentMods = RulesetCache.values.filter { it.name != "" }
        for (mod in currentMods) {
            val button = mod.name.toTextButton().onClick {
//                modActionTable.add("Last updated: ${mod.getSummary()}")

                rightSideButton.setText("Delete [${mod.name}]".tr())
                rightSideButton.enable()
                descriptionLabel.setText(mod.getSummary())
                removeRightSideClickListeners()
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
        refreshModTable()
    }
}