package com.unciv.ui.pickerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Json
import com.unciv.JsonParser
import com.unciv.MainMenuScreen
import com.unciv.models.ruleset.ModOptions
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivDateFormat.formatDate
import com.unciv.ui.utils.UncivDateFormat.parseDate
import com.unciv.ui.worldscreen.mainmenu.Github
import java.util.*
import kotlin.concurrent.thread

/**
 * The Mod Management Screen - called only from [MainMenuScreen]
 */
// All picker screens auto-wrap the top table in a ScrollPane.
// Since we want the different parts to scroll separately, we disable the default ScrollPane, which would scroll everything at once.
class ModManagementScreen: PickerScreen(disableScroll = true) {

    private val modTable = Table().apply { defaults().pad(10f) }
    private val scrollInstalledMods = ScrollPane(modTable)
    private val downloadTable = Table().apply { defaults().pad(10f) }
    private val scrollOnlineMods = ScrollPane(downloadTable)
    private val modActionTable = Table().apply { defaults().pad(10f) }

    val amountPerPage = 30

    private var lastSelectedButton: Button? = null
    private var lastSyncMarkedButton: Button? = null
    private var selectedModName = ""
    private var selectedAuthor = ""

    // keep running count of mods fetched from online search for comparison to total count as reported by GitHub
    private var downloadModCount = 0

    // Description data from installed mods and online search
    private val modDescriptionsInstalled: HashMap<String, String> = hashMapOf()
    private val modDescriptionsOnline: HashMap<String, String> = hashMapOf()
    private fun showModDescription(modName: String) {
        val online = modDescriptionsOnline[modName] ?: ""
        val installed = modDescriptionsInstalled[modName] ?: ""
        val separator = if(online.isEmpty() || installed.isEmpty()) "" else "\n"
        descriptionLabel.setText(online + separator + installed)
    }

    // Enable syncing entries in 'installed' and 'repo search ScrollPanes
    private class ScrollToEntry(val y: Float, val height: Float, val button: Button)
    private val installedScrollIndex = HashMap<String,ScrollToEntry>(30)
    private val onlineScrollIndex = HashMap<String,ScrollToEntry>(30)
    private var onlineScrollCurrentY = -1f


    // cleanup - background processing needs to be stopped on exit and memory freed
    private var runningSearchThread: Thread? = null
    private var stopBackgroundTasks = false
    override fun dispose() {
        // make sure the worker threads will not continue trying their time-intensive job
        runningSearchThread?.interrupt()
        stopBackgroundTasks = true
        super.dispose()
    }

    /** Helper class keeps references to decoration images of installed mods to enable dynamic visibility
     * (actually we do not use isVisible but refill a container selectively which allows the aggregate height to adapt and the set to center vertically)
     * @param container the table containing the indicators (one per mod, narrow, arranges up to three indicators vertically)
     * @param visualImage   image indicating _enabled as permanent visual mod_
     * @param updatedImage  image indicating _online mod has been updated_
     */
    private class ModStateImages (
            val container: Table,
            isVisual: Boolean = false,
            isUpdated: Boolean = false,
            val visualImage: Image = ImageGetter.getImage("OtherIcons/Visual"),
            val updatedImage: Image = ImageGetter.getImage("OtherIcons/Mods")
        ) {
        // mad but it's really initializing with the primary constructor parameter and not calling update()
        var isVisual: Boolean = isVisual 
            set(value) { if(field!=value) { field = value; update() } }
        var isUpdated: Boolean = isUpdated
            set(value) { if(field!=value) { field = value; update() } }
        private val spacer = Table().apply { width = 20f; height = 0f }
        fun update() {
            container.run {
                clear()
                if (isVisual) add(visualImage).row()
                if (isUpdated) add(updatedImage).row()
                if (!isVisual && !isUpdated) add(spacer)
                pack()
            }
        }
    }
    private val modStateImages = HashMap<String,ModStateImages>(30)


    init {
        //setDefaultCloseAction(screen) // this would initialize the new MainMenuScreen immediately
        val closeAction = {
            val tileSets = ImageGetter.getAvailableTilesets()
            if (game.settings.tileSet !in tileSets) {
                game.settings.tileSet = tileSets.first()
            }
            game.setScreen(MainMenuScreen())
            dispose()
        }
        closeButton.onClick(closeAction)
        onBackButtonClicked(closeAction)

        refreshInstalledModTable()

        // Header row
        topTable.add().expandX()                // empty cols left and right for separator
        topTable.add("Current mods".toLabel()).pad(5f).minWidth(200f).padLeft(25f)
            // 30 = 5 default pad + 20 to compensate for 'permanent visual mod' decoration icon
        topTable.add("Downloadable mods".toLabel()).pad(5f)
        topTable.add("".toLabel()).minWidth(200f)  // placeholder for "Mod actions"
        topTable.add().expandX()
        topTable.row()

        // horizontal separator looking like the SplitPane handle
        val separator = Table(skin)
        separator.background = skin.get("default-vertical", SplitPane.SplitPaneStyle::class.java).handle
        topTable.add(separator).minHeight(3f).fillX().colspan(5).row()

        // main row containing the three 'blocks' installed, online and information
        topTable.add()      // skip empty first column
        topTable.add(scrollInstalledMods)

        reloadOnlineMods()
        topTable.add(scrollOnlineMods)

        topTable.add(modActionTable)
    }

    private fun reloadOnlineMods() {
        onlineScrollCurrentY = -1f
        downloadTable.clear()
        onlineScrollIndex.clear()
        downloadTable.add(getDownloadFromUrlButton()).padBottom(15f).row()
        downloadTable.add("...".toLabel()).row()
        tryDownloadPage(1)
    }

    /** background worker: querying GitHub for Mods (repos with 'unciv-mod' in its topics)
     *
     *  calls itself for the next page of search results
     */
    private fun tryDownloadPage(pageNum: Int) {
        runningSearchThread = thread(name="GitHubSearch") {
            val repoSearch: Github.RepoSearch
            try {
                repoSearch = Github.tryGetGithubReposWithTopic(amountPerPage, pageNum)!!
            } catch (ex: Exception) {
                Gdx.app.postRunnable {
                    ToastPopup("Could not download mod list", this)
                }
                runningSearchThread = null
                return@thread
            }

            Gdx.app.postRunnable {
                // clear and hide last cell if it is the "..." indicator
                val lastCell = downloadTable.cells.lastOrNull()
                if (lastCell != null && lastCell.actor is Label && (lastCell.actor as Label).text.toString() == "...") {
                    lastCell.setActor<Actor>(null)
                    lastCell.pad(0f)
                }

                for (repo in repoSearch.items) {
                    if (stopBackgroundTasks) return@postRunnable
                    repo.name = repo.name.replace('-', ' ')

                    modDescriptionsOnline[repo.name] =
                            (repo.description ?: "-{No description provided}-".tr()) +
                            "\n" + "[${repo.stargazers_count}]✯".tr()

                    var downloadButtonText = repo.name
                    val existingMod = RulesetCache.values.firstOrNull { it.name == repo.name }
                    if (existingMod != null) {
                        if (existingMod.modOptions.lastUpdated != "" && existingMod.modOptions.lastUpdated != repo.updated_at) {
                            downloadButtonText += " - {Updated}"
                            modStateImages[repo.name]?.isUpdated = true
                        }
                        if (existingMod.modOptions.author.isEmpty()) {
                            rewriteModOptions(repo, Gdx.files.local("mods").child(repo.name))
                            existingMod.modOptions.author = repo.owner.login
                            existingMod.modOptions.modSize = repo.size
                        }
                    }
                    val downloadButton = downloadButtonText.toTextButton()
                    downloadButton.onClick { onlineButtonAction(repo, downloadButton) }

                    val cell = downloadTable.add(downloadButton)
                    downloadTable.row()
                    if (onlineScrollCurrentY < 0f) onlineScrollCurrentY = cell.padTop
                    onlineScrollIndex[repo.name] = ScrollToEntry(onlineScrollCurrentY, cell.prefHeight, downloadButton)
                    onlineScrollCurrentY += cell.padBottom + cell.prefHeight + cell.padTop
                    downloadModCount++
                }

                // Now the tasks after the 'page' of search results has been fully processed
                if (repoSearch.items.size < amountPerPage) {
                    // The search has reached the last page!
                    // Check: due to time passing between github calls it is not impossible we get a mod twice
                    val checkedMods: MutableSet<String> = mutableSetOf()
                    val duplicates: MutableList<Cell<Actor>> = mutableListOf()
                    downloadTable.cells.forEach {
                        cell->
                        cell.actor?.name?.apply {
                            if (checkedMods.contains(this)) {
                                duplicates.add(cell)
                            } else checkedMods.add(this)
                        }
                    }
                    duplicates.forEach {
                        it.setActor(null)
                        it.pad(0f)  // the cell itself cannot be removed so stop it occupying height
                    }
                    downloadModCount -= duplicates.size
                    // Check: It is also not impossible we missed a mod - just inform user
                    if (repoSearch.total_count > downloadModCount || repoSearch.incomplete_results) {
                        val retryLabel = "Online query result is incomplete".toLabel(Color.RED)
                        retryLabel.touchable = Touchable.enabled
                        retryLabel.onClick { reloadOnlineMods() }
                        downloadTable.add(retryLabel)
                    }
                } else {
                    // the page was full so there may be more pages.
                    // indicate that search will be continued
                    downloadTable.add("...".toLabel()).row()
                }

                downloadTable.pack()
                // Shouldn't actor.parent.actor = actor be a no-op? No, it has side effects we need.
                // See [commit for #3317](https://github.com/yairm210/Unciv/commit/315a55f972b8defe22e76d4a2d811c6e6b607e57)
                (downloadTable.parent as ScrollPane).actor = downloadTable

                // continue search unless last page was reached
                if (repoSearch.items.size >= amountPerPage && !stopBackgroundTasks)
                    tryDownloadPage(pageNum + 1)
            }
            runningSearchThread = null
        }
    }

    private fun syncOnlineSelected(name: String, button: Button) {
        syncSelected(name, button, installedScrollIndex, scrollInstalledMods)
    }
    private fun syncInstalledSelected(name: String, button: Button) {
        syncSelected(name, button, onlineScrollIndex, scrollOnlineMods)
    }
    private fun syncSelected(name: String, button: Button, index: HashMap<String, ScrollToEntry>, scroll: ScrollPane) {
        // manage selection color for user selection
        lastSelectedButton?.color = Color.WHITE
        button.color = Color.BLUE
        lastSelectedButton = button
        if (lastSelectedButton == lastSyncMarkedButton) lastSyncMarkedButton = null
        // look for sync-able same mod in other list
        val pos = index[name] ?: return
        // scroll into view
        scroll.scrollY = (pos.y + (pos.height - scroll.height) / 2).coerceIn(0f, scroll.maxY)
        // and color it so it's easier to find. ROYAL and SLATE too dark.
        lastSyncMarkedButton?.color = Color.WHITE
        pos.button.color = Color.valueOf("7499ab")  // about halfway between royal and sky
        lastSyncMarkedButton = pos.button
    }

    /** Recreate the information part of the right-hand column
     * @param repo: the repository instance as received from the GitHub api
     */
    private fun addModInfoToActionTable(repo: Github.Repo) {
        addModInfoToActionTable(repo.name, repo.html_url, repo.updated_at, repo.owner.login, repo.size)
    }
    /** Recreate the information part of the right-hand column
     * @param modName: The mod name (name from the RuleSet)
     * @param modOptions: The ModOptions as enriched by us with GitHub metadata when originally downloaded
     */
    private fun addModInfoToActionTable(modName: String, modOptions: ModOptions) {
        addModInfoToActionTable(modName, modOptions.modUrl, modOptions.lastUpdated, modOptions.author, modOptions.modSize)
    }
    private fun addModInfoToActionTable(modName: String, repoUrl: String, updatedAt: String, author: String, modSize: Int) {
        // remember selected mod - for now needed only to display a background-fetched image while the user is watching
        selectedModName = modName
        selectedAuthor = author

        // Display metadata
        if (author.isNotEmpty())
            modActionTable.add("Author: [$author]".toLabel()).row()
        if (modSize > 0)
            modActionTable.add("Size: [$modSize] kB".toLabel()).padBottom(15f).row()

        // offer link to open the repo itself in a browser
        if (repoUrl != "") {
            modActionTable.add("Open Github page".toTextButton().onClick {
                Gdx.net.openURI(repoUrl)
            }).row()
        }

        // display "updated" date
        if (updatedAt.isNotEmpty()) {
            val date = updatedAt.parseDate()
            val updateString = "{Updated}: " + date.formatDate()
            modActionTable.add(updateString.toLabel()).row()
        }
    }

    /** Create the special "Download from URL" button */
    private fun getDownloadFromUrlButton(): TextButton {
        val downloadButton = "Download mod from URL".toTextButton()
        downloadButton.onClick {
            val popup = Popup(this)
            val textArea = TextArea("https://github.com/...", skin)
            popup.add(textArea).width(stage.width / 2).row()
            val actualDownloadButton = "Download".toTextButton()
            actualDownloadButton.onClick {
                actualDownloadButton.setText("Downloading...".tr())
                actualDownloadButton.disable()
                downloadMod(Github.Repo().apply { html_url = textArea.text; default_branch = "master" }) { popup.close() }
            }
            popup.add(actualDownloadButton).row()
            popup.addCloseButton()
            popup.open()
        }
        return downloadButton
    }

    /** Used as onClick handler for the online Mod list buttons */
    private fun onlineButtonAction(repo: Github.Repo, button: Button) {
        syncOnlineSelected(repo.name, button)
        showModDescription(repo.name)
        removeRightSideClickListeners()
        rightSideButton.enable()
        val label = if (modStateImages[repo.name]?.isUpdated == true)
            "Update [${repo.name}]"
        else "Download [${repo.name}]"
        rightSideButton.setText(label.tr())
        rightSideButton.onClick {
            rightSideButton.setText("Downloading...".tr())
            rightSideButton.disable()
            downloadMod(repo) {
                rightSideButton.setText("Downloaded!".tr())
            }
        }

        modActionTable.clear()
        addModInfoToActionTable(repo)
    }

    /** Download and install a mod in the background, called from the right-bottom button */
    private fun downloadMod(repo: Github.Repo, postAction: () -> Unit = {}) {
        thread(name="DownloadMod") { // to avoid ANRs - we've learnt our lesson from previous download-related actions
            try {
                val modFolder = Github.downloadAndExtract(repo.html_url, repo.default_branch,
                    Gdx.files.local("mods"))
                    ?: return@thread
                rewriteModOptions(repo, modFolder)
                Gdx.app.postRunnable {
                    ToastPopup("Downloaded!", this)
                    RulesetCache.loadRulesets()
                    refreshInstalledModTable()
                    showModDescription(repo.name)
                    unMarkUpdatedMod(repo.name)
                }
            } catch (ex: Exception) {
                Gdx.app.postRunnable {
                    ToastPopup("Could not download mod", this)
                }
            } finally {
                postAction()
            }
        }
    }

    /** Rewrite modOptions file for a mod we just installed to include metadata we got from the GitHub api
     *
     *  (called on background thread)
     */
    private fun rewriteModOptions(repo: Github.Repo, modFolder: FileHandle) {
        val modOptionsFile = modFolder.child("jsons/ModOptions.json")
        val modOptions = if (modOptionsFile.exists()) JsonParser().getFromJson(ModOptions::class.java, modOptionsFile) else ModOptions()
        modOptions.modUrl = repo.html_url
        modOptions.lastUpdated = repo.updated_at
        modOptions.author = repo.owner.login
        modOptions.modSize = repo.size
        Json().toJson(modOptions, modOptionsFile)
    }

    /** Remove the visual indicators for an 'updated' mod after re-downloading it.
     *  (" - Updated" on the button text in the online mod list and the icon beside the installed mod's button)
     *  It should be up to date now (unless the repo's date is in the future relative to system time)
     *
     *  (called under postRunnable posted by background thread)
     */
    private fun unMarkUpdatedMod(name: String) {
        modStateImages[name]?.isUpdated = false
        val button = (onlineScrollIndex[name]?.button as? TextButton) ?: return
        button.setText(name)
    }

    /** Rebuild the right-hand column for clicks on installed mods
     *  Display single mod metadata, offer additional actions (delete is elsewhere)
    */
    private fun refreshModActions(mod: Ruleset) {
        modActionTable.clear()
        // show mod information first
        addModInfoToActionTable(mod.name, mod.modOptions)

        // offer 'permanent visual mod' toggle
        val visualMods = game.settings.visualMods
        val isVisual = visualMods.contains(mod.name)
        modStateImages[mod.name]?.isVisual = isVisual
        if (!isVisual) {
            modActionTable.add("Enable as permanent visual mod".toTextButton().onClick {
                visualMods.add(mod.name)
                game.settings.save()
                ImageGetter.setNewRuleset(ImageGetter.ruleset)
                refreshModActions(mod)
            })
        } else {
            modActionTable.add("Disable as permanent visual mod".toTextButton().onClick {
                visualMods.remove(mod.name)
                game.settings.save()
                ImageGetter.setNewRuleset(ImageGetter.ruleset)
                refreshModActions(mod)
            })
        }
        modActionTable.row()
    }

    /** Rebuild the left-hand column containing all installed mods */
    private fun refreshInstalledModTable() {
        modTable.clear()
        installedScrollIndex.clear()

        var currentY = -1f
        val currentMods = RulesetCache.values.asSequence().filter { it.name != "" }.sortedBy { it.name }
        for (mod in currentMods) {
            val summary = mod.getSummary()
            modDescriptionsInstalled[mod.name] = "Installed".tr() +
                    (if (summary.isEmpty()) "" else ": $summary")

            var imageMgr = modStateImages[mod.name]
            val decorationTable = 
                if (imageMgr != null) imageMgr.container 
                else {
                    val table = Table().apply { defaults().size(20f).align(Align.topLeft) }
                    imageMgr = ModStateImages(table, isVisual = mod.name in game.settings.visualMods)
                    modStateImages[mod.name] = imageMgr
                    table
                }
            imageMgr.update()     // rebuilds decorationTable content

            val button = mod.name.toTextButton()
            button.onClick {
                syncInstalledSelected(mod.name, button)
                refreshModActions(mod)
                rightSideButton.setText("Delete [${mod.name}]".tr())
                rightSideButton.isEnabled = true
                showModDescription(mod.name)
                removeRightSideClickListeners()
                rightSideButton.onClick {
                    rightSideButton.isEnabled = false
                    YesNoPopup(
                        question = "Are you SURE you want to delete this mod?",
                        action = { 
                                    deleteMod(mod)
                                    rightSideButton.setText("[${mod.name}] was deleted.".tr())
                                 },
                        screen = this,
                        restoreDefault = { rightSideButton.isEnabled = true }
                    ).open()
                }
            }

            val decoratedButton = Table()
            decoratedButton.add(button)
            decoratedButton.add(decorationTable).align(Align.center+Align.left)
            val cell = modTable.add(decoratedButton)
            modTable.row()
            if (currentY < 0f) currentY = cell.padTop
            installedScrollIndex[mod.name] = ScrollToEntry(currentY, cell.prefHeight, button)
            currentY += cell.padBottom + cell.prefHeight + cell.padTop
        }
    }

    /** Delete a Mod, refresh ruleset cache and update installed mod table */
    private fun deleteMod(mod: Ruleset) {
        val modFileHandle = Gdx.files.local("mods").child(mod.name)
        if (modFileHandle.isDirectory) modFileHandle.deleteDirectory()
        else modFileHandle.delete()     // This should never happen
        RulesetCache.loadRulesets()
        modStateImages.remove(mod.name)
        refreshInstalledModTable()
    }
}
