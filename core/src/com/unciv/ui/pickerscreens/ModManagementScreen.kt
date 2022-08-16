package com.unciv.ui.pickerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.SerializationException
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.models.ruleset.ModOptions
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.pickerscreens.ModManagementOptions.SortType
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.AutoScrollPane
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ExpanderTab
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.RecreateOnResize
import com.unciv.ui.utils.UncivTextField
import com.unciv.ui.utils.WrappableLabel
import com.unciv.ui.utils.extensions.UncivDateFormat.formatDate
import com.unciv.ui.utils.extensions.UncivDateFormat.parseDate
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toCheckBox
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlin.math.max

/**
 * The Mod Management Screen - called only from [MainMenuScreen]
 * @param previousOnlineMods - cached online mod list, if supplied and not empty, it will be displayed as is and no online query will be run. Used for resize.
 */
// All picker screens auto-wrap the top table in a ScrollPane.
// Since we want the different parts to scroll separately, we disable the default ScrollPane, which would scroll everything at once.
class ModManagementScreen(
    previousInstalledMods: HashMap<String, ModUIData>? = null,
    previousOnlineMods: HashMap<String, ModUIData>? = null
): PickerScreen(disableScroll = true), RecreateOnResize {

    private val modTable = Table().apply { defaults().pad(10f) }
    private val scrollInstalledMods = AutoScrollPane(modTable)
    private val downloadTable = Table().apply { defaults().pad(10f) }
    private val scrollOnlineMods = AutoScrollPane(downloadTable)
    private val modActionTable = Table().apply { defaults().pad(10f) }
    private val optionsManager = ModManagementOptions(this)

    val amountPerPage = 100

    private var lastSelectedButton: Button? = null
    private var lastSyncMarkedButton: Button? = null
    private var selectedModName = ""
    private var selectedAuthor = ""

    private val modDescriptionLabel: WrappableLabel

    private var installedHeaderLabel: Label? = null
    private var onlineHeaderLabel: Label? = null
    private var installedExpanderTab: ExpanderTab? = null
    private var onlineExpanderTab: ExpanderTab? = null


    // Enable re-sorting and syncing entries in 'installed' and 'repo search' ScrollPanes
    private val installedModInfo = previousInstalledMods ?: HashMap(10) // HashMap<String, ModUIData> inferred
    private val onlineModInfo = previousOnlineMods ?: HashMap(90) // HashMap<String, ModUIData> inferred

    private var onlineScrollCurrentY = -1f

    // cleanup - background processing needs to be stopped on exit and memory freed
    private var runningSearchJob: Job? = null
    private var stopBackgroundTasks = false
    override fun dispose() {
        // make sure the worker threads will not continue trying their time-intensive job
        runningSearchJob?.cancel()
        stopBackgroundTasks = true
        super.dispose()
    }


    init {
        //setDefaultCloseAction(screen) // this would initialize the new MainMenuScreen immediately
        rightSideButton.isVisible = false
        closeButton.onActivation {
            val tileSets = ImageGetter.getAvailableTilesets()
            if (game.settings.tileSet !in tileSets) {
                game.settings.tileSet = tileSets.first()
            }
            game.popScreen()
        }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)

        val labelWidth = max(stage.width / 2f - 60f,60f)
        modDescriptionLabel = WrappableLabel("", labelWidth)
        modDescriptionLabel.wrap = true

        // Replace the PickerScreen's descriptionLabel
        val labelWrapper = Table()
        labelWrapper.defaults().top().left().growX()
        val labelScroll = descriptionLabel.parent as ScrollPane
        descriptionLabel.remove()
        labelWrapper.row()
        labelWrapper.add(modDescriptionLabel).row()
        labelScroll.actor = labelWrapper

        refreshInstalledModTable()

        if (isNarrowerThan4to3()) initPortrait()
        else initLandscape()

        if (onlineModInfo.isEmpty())
            reloadOnlineMods()
        else
            refreshOnlineModTable()
    }

    private fun initPortrait() {
        topTable.defaults().top().pad(0f)

        topTable.add(optionsManager.expander).top().growX().row()

        installedExpanderTab = ExpanderTab(optionsManager.getInstalledHeader(), expanderWidth = stage.width) {
            it.add(scrollInstalledMods).growX()
        }
        topTable.add(installedExpanderTab).top().growX().row()

        onlineExpanderTab = ExpanderTab(optionsManager.getOnlineHeader(), expanderWidth = stage.width) {
            it.add(scrollOnlineMods).growX()
        }
        topTable.add(onlineExpanderTab).top().padTop(10f).growX().row()

        topTable.add().expandY().row() // helps with top() being ignored

        topTable.add(ExpanderTab("Mod info and options", expanderWidth = stage.width) {
            it.add(modActionTable).growX()
        }).bottom().padTop(10f).growX().row()
    }

    private fun initLandscape() {
        // Header row
        topTable.add().expandX()                // empty cols left and right for separator
        installedHeaderLabel = optionsManager.getInstalledHeader().toLabel()
        installedHeaderLabel!!.onClick {
            optionsManager.installedHeaderClicked()
        }
        topTable.add(installedHeaderLabel).pad(5f).minWidth(200f).padLeft(25f)
            // 30 = 5 default pad + 20 to compensate for 'permanent visual mod' decoration icon
        onlineHeaderLabel = optionsManager.getOnlineHeader().toLabel()
        onlineHeaderLabel!!.onClick {
            optionsManager.onlineHeaderClicked()
        }
        topTable.add(onlineHeaderLabel).pad(5f)
        topTable.add("".toLabel()).minWidth(200f)  // placeholder for "Mod actions"
        topTable.add().expandX()
        topTable.row()

        // horizontal separator looking like the SplitPane handle
        topTable.addSeparator(Color.CLEAR, 5, 3f)

        // main row containing the three 'blocks' installed, online and information
        topTable.add()      // skip empty first column
        topTable.add(scrollInstalledMods)
        topTable.add(scrollOnlineMods)
        topTable.add(modActionTable)
        topTable.add().row()
        topTable.add().expandY()  // So short lists won't vertically center everything including headers

        stage.addActor(optionsManager.expander)
        optionsManager.expanderChangeEvent = {
            optionsManager.expander.pack()
            optionsManager.expander.setPosition(stage.width - 2f, stage.height - 2f, Align.topRight)
        }
        optionsManager.expanderChangeEvent?.invoke()
    }

    private fun reloadOnlineMods() {
        onlineScrollCurrentY = -1f
        downloadTable.clear()
        onlineModInfo.clear()
        downloadTable.add(getDownloadFromUrlButton()).padBottom(15f).row()
        downloadTable.add("...".toLabel()).row()
        tryDownloadPage(1)
    }

    /** background worker: querying GitHub for Mods (repos with 'unciv-mod' in its topics)
     *
     *  calls itself for the next page of search results
     */
    private fun tryDownloadPage(pageNum: Int) {
        runningSearchJob = Concurrency.run("GitHubSearch") {
            val repoSearch: Github.RepoSearch
            try {
                repoSearch = Github.tryGetGithubReposWithTopic(amountPerPage, pageNum)!!
            } catch (ex: Exception) {
                launchOnGLThread {
                    ToastPopup("Could not download mod list", this@ModManagementScreen)
                }
                runningSearchJob = null
                return@run
            }

            if (!isActive) {
                return@run
            }

            launchOnGLThread { addModInfoFromRepoSearch(repoSearch, pageNum) }
            runningSearchJob = null
        }
    }

    private fun addModInfoFromRepoSearch(repoSearch: Github.RepoSearch, pageNum: Int){
        // clear and remove last cell if it is the "..." indicator
        val lastCell = downloadTable.cells.lastOrNull()
        if (lastCell != null && lastCell.actor is Label && (lastCell.actor as Label).text.toString() == "...") {
            lastCell.setActor<Actor>(null)
            downloadTable.cells.removeValue(lastCell, true)
        }

        for (repo in repoSearch.items) {
            if (stopBackgroundTasks) return
            repo.name = repo.name.replace('-', ' ')

            if (onlineModInfo.containsKey(repo.name))
                continue // we already got this mod in a previous download, since one has been added in between

            // Mods we have manually decided to remove for instability are removed here
            // If at some later point these mods are updated, we should definitely remove
            // this piece of code. This is a band-aid, not a full solution.
            if (repo.html_url in modsToHideAsUrl) continue

            val installedMod = RulesetCache.values.firstOrNull { it.name == repo.name }
            val isUpdatedVersionOfInstalledMod = installedMod?.modOptions?.let {
                it.lastUpdated != "" && it.lastUpdated != repo.pushed_at
            } == true

            if (installedMod != null) {

                if (isUpdatedVersionOfInstalledMod) {
                    installedModInfo[repo.name]!!.state.hasUpdate = true
                }

                if (installedMod.modOptions.author.isEmpty()) {
                    try {
                        Github.rewriteModOptions(repo, installedMod.folderLocation!!)
                    } catch (ex: SerializationException) {
                        Log.error("Error while adding mod info from repo search:", ex)
                        return
                    } catch (ex: Exception) {
                        Log.error("Error while adding mod info from repo search:", ex)
                        return
                    }
                    installedMod.modOptions.author = repo.owner.login
                    installedMod.modOptions.modSize = repo.size
                    installedMod.modOptions.topics = repo.topics
                }
            }

            val mod = ModUIData(repo, isUpdatedVersionOfInstalledMod)
            onlineModInfo[repo.name] = mod
            mod.button.onClick { onlineButtonAction(repo, mod.button) }

            val cell = downloadTable.add(mod.button)
            downloadTable.row()
            if (onlineScrollCurrentY < 0f) onlineScrollCurrentY = cell.padTop
            mod.y = onlineScrollCurrentY
            mod.height = cell.prefHeight
            onlineScrollCurrentY += cell.padBottom + cell.prefHeight + cell.padTop
        }

        // Now the tasks after the 'page' of search results has been fully processed
        // The search has reached the last page!
        if (repoSearch.items.size < amountPerPage) {
            // Check: It is also not impossible we missed a mod - just inform user
            if (repoSearch.incomplete_results) {
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

    private fun syncOnlineSelected(modName: String, button: Button) {
        syncSelected(modName, button, installedModInfo, scrollInstalledMods)
    }
    private fun syncInstalledSelected(modName: String, button: Button) {
        syncSelected(modName, button, onlineModInfo, scrollOnlineMods)
    }
    private fun syncSelected(modName: String, button: Button, modNameToData: HashMap<String, ModUIData>, scroll: ScrollPane) {
        // manage selection color for user selection
        lastSelectedButton?.color = Color.WHITE
        button.color = Color.BLUE
        lastSelectedButton = button
        if (lastSelectedButton != lastSyncMarkedButton)
            lastSyncMarkedButton?.color = Color.WHITE
        lastSyncMarkedButton = null
        // look for sync-able same mod in other list
        val modUIDataInOtherList = modNameToData[modName] ?: return
        // scroll into view
        scroll.scrollY = (modUIDataInOtherList.y + (modUIDataInOtherList.height - scroll.height) / 2).coerceIn(0f, scroll.maxY)
        // and color it so it's easier to find. ROYAL and SLATE too dark.
        modUIDataInOtherList.button.color = Color.valueOf("7499ab")  // about halfway between royal and sky
        lastSyncMarkedButton = modUIDataInOtherList.button
    }

    /** Recreate the information part of the right-hand column
     * @param repo: the repository instance as received from the GitHub api
     */
    private fun addModInfoToActionTable(repo: Github.Repo) {
        addModInfoToActionTable(repo.name, repo.html_url, repo.pushed_at, repo.owner.login, repo.size)
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
            popup.addGoodSizedLabel("Please enter the mod repository -or- archive zip url:").row()
            val textField = UncivTextField.create("")
            popup.add(textField).width(stage.width / 2).row()
            val actualDownloadButton = "Download".toTextButton()
            actualDownloadButton.onClick {
                actualDownloadButton.setText("Downloading...".tr())
                actualDownloadButton.disable()
                downloadMod(Github.Repo().parseUrl(textField.text)) { popup.close() }
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
        rightSideButton.isVisible = true
        rightSideButton.clearListeners()
        rightSideButton.enable()
        val label = if (installedModInfo[repo.name]?.state?.hasUpdate == true)
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

    /** Download and install a mod in the background, called both from the right-bottom button and the URL entry popup */
    private fun downloadMod(repo: Github.Repo, postAction: () -> Unit = {}) {
        Concurrency.run("DownloadMod") { // to avoid ANRs - we've learnt our lesson from previous download-related actions
            try {
                val modFolder = Github.downloadAndExtract(repo.html_url, repo.default_branch,
                    Gdx.files.local("mods"))
                    ?: throw Exception()    // downloadAndExtract returns null for 404 errors and the like -> display something!
                Github.rewriteModOptions(repo, modFolder)
                launchOnGLThread {
                    ToastPopup("[${repo.name}] Downloaded!", this@ModManagementScreen)
                    RulesetCache.loadRulesets()
                    UncivGame.Current.translations.tryReadTranslationForCurrentLanguage()
                    RulesetCache[repo.name]?.let {
                        installedModInfo[repo.name] = ModUIData(it)
                    }
                    refreshInstalledModTable()
                    showModDescription(repo.name)
                    unMarkUpdatedMod(repo.name)
                    postAction()
                }
            } catch (ex: Exception) {
                launchOnGLThread {
                    ToastPopup("Could not download [${repo.name}]", this@ModManagementScreen)
                    postAction()
                }
            }
        }
    }

    /** Remove the visual indicators for an 'updated' mod after re-downloading it.
     *  (" - Updated" on the button text in the online mod list and the icon beside the installed mod's button)
     *  It should be up to date now (unless the repo's date is in the future relative to system time)
     *
     *  (called under postRunnable posted by background thread)
     */
    private fun unMarkUpdatedMod(name: String) {
        installedModInfo[name]?.state?.hasUpdate = false
        onlineModInfo[name]?.state?.hasUpdate = false
        val button = onlineModInfo[name]?.button
        button?.setText(name)
        if (optionsManager.sortInstalled == SortType.Status)
            refreshInstalledModTable()
        if (optionsManager.sortOnline == SortType.Status)
            refreshOnlineModTable()
    }

    /** Rebuild the right-hand column for clicks on installed mods
     *  Display single mod metadata, offer additional actions (delete is elsewhere)
    */
    private fun refreshInstalledModActions(mod: Ruleset) {
        modActionTable.clear()
        // show mod information first
        addModInfoToActionTable(mod.name, mod.modOptions)

        // offer 'permanent visual mod' toggle
        val visualMods = game.settings.visualMods
        val isVisualMod = visualMods.contains(mod.name)
        installedModInfo[mod.name]!!.state.isVisual = isVisualMod

        val visualCheckBox = "Permanent audiovisual mod".toCheckBox(isVisualMod) { checked ->
            if (checked)
                visualMods.add(mod.name)
            else
                visualMods.remove(mod.name)
            game.settings.save()
            ImageGetter.setNewRuleset(ImageGetter.ruleset)
            refreshInstalledModActions(mod)
            if (optionsManager.sortInstalled == SortType.Status)
                refreshInstalledModTable()
        }
        modActionTable.add(visualCheckBox).row()

        if (installedModInfo[mod.name]!!.state.hasUpdate) {
            val updateModTextbutton = "Update [${mod.name}]".toTextButton()
            updateModTextbutton.onClick {
                updateModTextbutton.setText("Downloading...".tr())
                val repo = onlineModInfo[mod.name]!!.repo!!
                downloadMod(repo) { refreshInstalledModActions(mod) }
            }
            modActionTable.add(updateModTextbutton)
        }
    }

    /** Rebuild the left-hand column containing all installed mods */
    internal fun refreshInstalledModTable() {
        // pre-init if not already done - important: keep the ModUIData instances later on or
        // at least the button references otherwise sync will not work
        if (installedModInfo.isEmpty()) {
            for (mod in RulesetCache.values.asSequence().filter { it.name != "" }) {
                val modUIData = ModUIData(mod)
                modUIData.state.isVisual = mod.name in game.settings.visualMods
                installedModInfo[mod.name] = modUIData
            }
        }

        val newHeaderText = optionsManager.getInstalledHeader()
        installedHeaderLabel?.setText(newHeaderText)
        installedExpanderTab?.setText(newHeaderText)

        modTable.clear()
        var currentY = -1f
        val filter = optionsManager.getFilter()
        for (mod in installedModInfo.values.sortedWith(optionsManager.sortInstalled.comparator)) {
            if (!mod.matchesFilter(filter)) continue
            // Prevent building up listeners. The virgin Button has one: for mouseover styling.
            // The captures for our listener shouldn't need updating, so assign only once
            if (mod.button.listeners.none { it.javaClass.`package`.name.startsWith("com.unciv") })
                mod.button.onClick {
                    rightSideButton.isVisible = true
                    installedButtonAction(mod)
                }
            val decoratedButton = Table()
            decoratedButton.add(mod.button)
            decoratedButton.add(mod.state.container).align(Align.center+Align.left)
            val cell = modTable.add(decoratedButton)
            modTable.row()
            if (currentY < 0f) currentY = cell.padTop
            mod.y = currentY
            mod.height = cell.prefHeight
            currentY += cell.padBottom + cell.prefHeight + cell.padTop
        }
    }

    private fun installedButtonAction(mod: ModUIData) {
        syncInstalledSelected(mod.name, mod.button)
        refreshInstalledModActions(mod.ruleset!!)
        val deleteText = "Delete [${mod.name}]"
        rightSideButton.setText(deleteText.tr())
        // Don't let the player think he can delete Vanilla and G&K rulesets
        rightSideButton.isEnabled = mod.ruleset.folderLocation!=null
        showModDescription(mod.name)
        rightSideButton.clearListeners()
        rightSideButton.onClick {
            rightSideButton.isEnabled = false
            ConfirmPopup(
                screen = this,
                question = "Are you SURE you want to delete this mod?",
                confirmText = deleteText,
                restoreDefault = { rightSideButton.isEnabled = true }
            ) {
                deleteMod(mod.ruleset)
                modActionTable.clear()
                rightSideButton.setText("[${mod.name}] was deleted.".tr())
            }.open()
        }
    }

    /** Delete a Mod, refresh ruleset cache and update installed mod table */
    private fun deleteMod(mod: Ruleset) {
        mod.folderLocation!!.deleteDirectory()
        RulesetCache.loadRulesets()
        installedModInfo.remove(mod.name)
        refreshInstalledModTable()
    }

    internal fun refreshOnlineModTable() {
        if (runningSearchJob != null) return  // cowardice: prevent concurrent modification, avoid a manager layer

        val newHeaderText = optionsManager.getOnlineHeader()
        onlineHeaderLabel?.setText(newHeaderText)
        onlineExpanderTab?.setText(newHeaderText)

        downloadTable.clear()
        downloadTable.add(getDownloadFromUrlButton()).row()
        onlineScrollCurrentY = -1f

        val filter = optionsManager.getFilter()
        // Important: sortedMods holds references to the original values, so the referenced buttons stay valid.
        // We update y and height here, we do not replace the ModUIData instances do the referenced buttons stay valid.
        val sortedMods = onlineModInfo.values.asSequence().sortedWith(optionsManager.sortOnline.comparator)
        for (mod in sortedMods) {
            if (!mod.matchesFilter(filter)) continue
            val cell = downloadTable.add(mod.button)
            downloadTable.row()
            if (onlineScrollCurrentY < 0f) onlineScrollCurrentY = cell.padTop
            mod.y = onlineScrollCurrentY
            mod.height = cell.prefHeight
            onlineScrollCurrentY += cell.padBottom + cell.prefHeight + cell.padTop
        }

        downloadTable.pack()
        (downloadTable.parent as ScrollPane).actor = downloadTable
    }

    private fun showModDescription(modName: String) {
        val onlineModDescription = onlineModInfo[modName]?.description ?: "" // shows github info
        val installedModDescription = installedModInfo[modName]?.description ?: "" // shows ruleset info
        val separator = if (onlineModDescription.isEmpty() || installedModDescription.isEmpty()) "" else "\n"
        modDescriptionLabel.setText(onlineModDescription + separator + installedModDescription)
    }

    override fun recreate(): BaseScreen = ModManagementScreen(installedModInfo, onlineModInfo)

    companion object {
        val modsToHideAsUrl by lazy {
            val blockedModsFile = Gdx.files.internal("jsons/ManuallyBlockedMods.json")
            json().fromJsonFile(Array<String>::class.java, blockedModsFile)
        }
    }
}
