package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameSaver
import com.unciv.logic.MissingModsException
import com.unciv.logic.UncivShowableException
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.pickerscreens.Github
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivDateFormat.formatDate
import java.util.*
import java.util.concurrent.CancellationException
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class LoadGameScreen(previousScreen:BaseScreen) : PickerScreen(disableScroll = true) {
    lateinit var selectedSave: String
    private val copySavedGameToClipboardButton = "Copy saved game to clipboard".toTextButton()
    private val saveTable = Table()
    private val deleteSaveButton = "Delete save".toTextButton()
    private val errorLabel = "".toLabel(Color.RED)
    private val loadMissingModsButton = "Download missing mods".toTextButton()
    private val showAutosavesCheckbox = CheckBox("Show autosaves".tr(), skin)
    private var missingModsToLoad = ""

    init {
        setDefaultCloseAction(previousScreen)

        resetWindowState()
        topTable.add(ScrollPane(saveTable))

        val rightSideTable = getRightSideTable()

        topTable.add(rightSideTable)

        rightSideButton.onClick {
            val loadingPopup = Popup( this)
            loadingPopup.addGoodSizedLabel("Loading...")
            loadingPopup.open()
            launchCrashHandling("Load Game") {
                try {
                    // This is what can lead to ANRs - reading the file and setting the transients, that's why this is in another thread
                    val loadedGame = game.gameSaver.loadGameByName(selectedSave)
                    postCrashHandlingRunnable { UncivGame.Current.loadGame(loadedGame) }
                } catch (ex: Exception) {
                    postCrashHandlingRunnable {
                        loadingPopup.close()
                        val cantLoadGamePopup = Popup(this@LoadGameScreen)
                        cantLoadGamePopup.addGoodSizedLabel("It looks like your saved game can't be loaded!").row()
                        if (ex is UncivShowableException && ex.localizedMessage != null) {
                            // thrown exceptions are our own tests and can be shown to the user
                            cantLoadGamePopup.addGoodSizedLabel(ex.localizedMessage).row()
                            cantLoadGamePopup.addCloseButton()
                            cantLoadGamePopup.open()
                        } else {
                            cantLoadGamePopup.addGoodSizedLabel("If you could copy your game data (\"Copy saved game to clipboard\" - ").row()
                            cantLoadGamePopup.addGoodSizedLabel("  paste into an email to yairm210@hotmail.com)").row()
                            cantLoadGamePopup.addGoodSizedLabel("I could maybe help you figure out what went wrong, since this isn't supposed to happen!").row()
                            cantLoadGamePopup.addCloseButton()
                            cantLoadGamePopup.open()
                            ex.printStackTrace()
                        }
                    }
                }
            }
        }

    }

    private fun getRightSideTable(): Table {
        val rightSideTable = Table()
        rightSideTable.defaults().pad(10f)

        val loadFromClipboardButton = "Load copied data".toTextButton()
        loadFromClipboardButton.onClick {
            try {
                val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                val loadedGame = GameSaver.gameInfoFromString(clipboardContentsString)
                UncivGame.Current.loadGame(loadedGame)
            } catch (ex: Exception) {
                handleLoadGameException("Could not load game from clipboard!", ex)
            }
        }
        rightSideTable.add(loadFromClipboardButton).row()
        if (game.gameSaver.canLoadFromCustomSaveLocation()) {
            val loadFromCustomLocation = "Load from custom location".toTextButton()
            loadFromCustomLocation.onClick {
                game.gameSaver.loadGameFromCustomLocation { gameInfo, exception ->
                    if (gameInfo != null) {
                        postCrashHandlingRunnable {
                            game.loadGame(gameInfo)
                        }
                    } else if (exception !is CancellationException)
                        handleLoadGameException("Could not load game from custom location!", exception)
                }
            }
            rightSideTable.add(loadFromCustomLocation).row()
        }
        rightSideTable.add(errorLabel).row()

        loadMissingModsButton.onClick {
            loadMissingMods()
        }
        loadMissingModsButton.isVisible = false
        rightSideTable.add(loadMissingModsButton).row()

        deleteSaveButton.onClick {
            game.gameSaver.deleteSave(selectedSave)
            resetWindowState()
        }
        deleteSaveButton.disable()
        rightSideTable.add(deleteSaveButton).row()

        copySavedGameToClipboardButton.disable()
        copySavedGameToClipboardButton.onClick {
            val gameText = game.gameSaver.getSave(selectedSave).readString()
            val gzippedGameText = Gzip.zip(gameText)
            Gdx.app.clipboard.contents = gzippedGameText
        }
        rightSideTable.add(copySavedGameToClipboardButton).row()

        showAutosavesCheckbox.isChecked = false
        showAutosavesCheckbox.onChange {
            updateLoadableGames(showAutosavesCheckbox.isChecked)
        }
        rightSideTable.add(showAutosavesCheckbox).row()
        return rightSideTable
    }

    private fun handleLoadGameException(primaryText: String, ex: Exception?) {
        var errorText = primaryText.tr()
        if (ex is UncivShowableException) errorText += "\n${ex.message}"
        errorLabel.setText(errorText)
        ex?.printStackTrace()
        if (ex is MissingModsException) {
            loadMissingModsButton.isVisible = true
            missingModsToLoad = ex.missingMods
        }
    }

    private fun loadMissingMods() {
        loadMissingModsButton.isEnabled = false
        descriptionLabel.setText("Loading...".tr())
        launchCrashHandling("DownloadMods", runAsDaemon = false) {
            try {
                val mods = missingModsToLoad.replace(' ', '-').lowercase().splitToSequence(",-")
                for (modName in mods) {
                    val repos = Github.tryGetGithubReposWithTopic(10, 1, modName)
                        ?: throw UncivShowableException("Could not download mod list.".tr())
                    val repo = repos.items.firstOrNull { it.name.lowercase() == modName }
                        ?: throw UncivShowableException("Could not find a mod named \"[$modName]\".".tr())
                    val modFolder = Github.downloadAndExtract(
                        repo.html_url, repo.default_branch,
                        Gdx.files.local("mods")
                    )
                        ?: throw Exception() // downloadAndExtract returns null for 404 errors and the like -> display something!
                    Github.rewriteModOptions(repo, modFolder)
                }
                postCrashHandlingRunnable {
                    RulesetCache.loadRulesets()
                    missingModsToLoad = ""
                    loadMissingModsButton.isVisible = false
                    errorLabel.setText("")
                    ToastPopup("Missing mods are downloaded successfully.", this@LoadGameScreen)
                }
            } catch (ex: Exception) {
                handleLoadGameException("Could not load the missing mods!", ex)
            } finally {
                loadMissingModsButton.isEnabled = true
                descriptionLabel.setText("")
            }

        }
    }

    private fun resetWindowState() {
        updateLoadableGames(showAutosavesCheckbox.isChecked)
        deleteSaveButton.disable()
        copySavedGameToClipboardButton.disable()
        rightSideButton.setText("Load game".tr())
        rightSideButton.disable()
        descriptionLabel.setText("")
    }

    private fun updateLoadableGames(showAutosaves: Boolean) {
        saveTable.clear()

        val loadImage = ImageGetter.getImage("OtherIcons/Load")
        loadImage.setSize(50f, 50f) // So the origin sets correctly
        loadImage.setOrigin(Align.center)
        loadImage.addAction(Actions.rotateBy(360f, 2f))
        saveTable.add(loadImage).size(50f)

        // Apparently, even just getting the list of saves can cause ANRs -
        // not sure how many saves these guys had but Google Play reports this to have happened hundreds of times
        launchCrashHandling("GetSaves") {
            // .toList() because otherwise the lastModified will only be checked inside the postRunnable
            val saves = game.gameSaver.getSaves(autoSaves = showAutosaves).sortedByDescending { it.lastModified() }.toList()

            postCrashHandlingRunnable {
                saveTable.clear()
                for (save in saves) {
                    val textButton = TextButton(save.name(), skin)
                    textButton.onClick { onSaveSelected(save) }
                    saveTable.add(textButton).pad(5f).row()
                }
            }
        }
    }

    private fun onSaveSelected(save: FileHandle) {
        selectedSave = save.name()
        copySavedGameToClipboardButton.enable()

        rightSideButton.setText("Load [${save.name()}]".tr())
        rightSideButton.enable()
        deleteSaveButton.enable()
        deleteSaveButton.color = Color.RED
        descriptionLabel.setText("Loading...".tr())


        val savedAt = Date(save.lastModified())
        var textToSet = save.name() + "\n${"Saved at".tr()}: " + savedAt.formatDate()
        launchCrashHandling("LoadMetaData") { // Even loading the game to get its metadata can take a long time on older phones
            try {
                val game = game.gameSaver.loadGamePreviewFromFile(save)
                val playerCivNames = game.civilizations.filter { it.isPlayerCivilization() }.joinToString { it.civName.tr() }
                textToSet += "\n" + playerCivNames +
                        ", " + game.difficulty.tr() + ", ${Fonts.turn}" + game.turns
                textToSet += "\n${"Base ruleset:".tr()} " + game.gameParameters.baseRuleset
                if (game.gameParameters.mods.isNotEmpty())
                    textToSet += "\n${"Mods:".tr()} " + game.gameParameters.mods.joinToString()
            } catch (ex: Exception) {
                textToSet += "\n${"Could not load game".tr()}!"
            }

            postCrashHandlingRunnable {
                descriptionLabel.setText(textToSet)
            }
        }
    }

}
