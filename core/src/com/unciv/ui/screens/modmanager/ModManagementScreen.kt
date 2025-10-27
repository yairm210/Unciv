package com.unciv.ui.screens.modmanager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.SerializationException
import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.logic.github.Github
import com.unciv.logic.github.Github.repoNameToFolderName
import com.unciv.logic.github.GithubAPI
import com.unciv.logic.github.GithubAPI.downloadAndExtract
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.clearActivationActions
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.widgets.LoadingImage
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.widgets.WrappableLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.modmanager.ModManagementOptions.SortType
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.io.IOException
import kotlin.math.max

/**
 * The Mod Management Screen - constructor for internal use by [resize]
 * @param previousInstalledMods - cached installed mod list.
 * @param previousOnlineMods - cached online mod list, if supplied and not empty, it will be displayed as is and no online query will be run.
 */
// All picker screens auto-wrap the top table in a ScrollPane.
// Since we want the different parts to scroll separately, we disable the default ScrollPane, which would scroll everything at once.
class ModManagementScreen private constructor(
    previousInstalledMods: HashMap<String, ModUIData>?,
    previousOnlineMods: HashMap<String, ModUIData>?
): PickerScreen(disableScroll = true), RecreateOnResize {
    /** The Mod Management Screen - called only from [MainMenuScreen] */
    constructor() : this(null, null)

    companion object {
        // Tweakable constants
        /** For preview.png */
        const val maxAllowedPreviewImageSize = 200f
        /** Github queries use this limit */
        const val amountPerPage = 100

        fun cleanModName(modName: String): String = modName.replace("   ", " - ")
    }

    // Since we're `RecreateOnResize`, preserve the portrait/landscape mode for our lifetime
    private val isPortrait: Boolean

    // Will hold a LoadingImage until the online query is done, then it is freed/nulled
    private var loading: LoadingImage? = null
    // Holds the Cell in portrait mode which initially gets the loading image and later the options widget
    private var optionsCell: Cell<Actor?>? = null

    // Left column (in landscape, portrait stacks them within expanders)
    private val installedModsTable = Table().apply { defaults().pad(10f) }
    private val scrollInstalledMods = AutoScrollPane(installedModsTable)
    // Center column
    private val onlineModsTable = Table().apply { defaults().pad(10f) }
    private val scrollOnlineMods = AutoScrollPane(onlineModsTable)
    // Right column
    private val modActionTable = ModInfoAndActionPane()
    private val scrollActionTable = AutoScrollPane(modActionTable)
    // Manager providing the Widget floating top right in landscape mode, stacked expander in portrait
    private val optionsManager = ModManagementOptions(this)

    private var lastSelectedButton: ModDecoratedButton? = null
    private var lastSyncMarkedButton: ModDecoratedButton? = null
    private var selectedMod: GithubAPI.Repo? = null

    private val modDescriptionLabel: WrappableLabel

    private var installedHeaderLabel: Label? = null
    private var onlineHeaderLabel: Label? = null
    private var installedExpanderTab: ExpanderTab? = null
    private var onlineExpanderTab: ExpanderTab? = null

    // Enable re-sorting and syncing entries in 'installed' and 'repo search' ScrollPanes
    // Keep metadata and buttons in separate pools
    private val installedModInfo = previousInstalledMods ?: HashMap(RulesetCache.size)
    private val onlineModInfo = previousOnlineMods ?: game.files.loadModCache().associateByTo(HashMap()) { it.name }
    private val modButtons: HashMap<ModUIData, ModDecoratedButton> = HashMap(100)

    // cleanup - background processing needs to be stopped on exit and memory freed
    private var runningSearchJob: Job? = null
    // This is only set for cleanup, not when the user stops the query (by clicking the loading icon)
    // Therefore, finding `runningSearchJob?.isActive == false && !stopBackgroundTasks` means stopped by user
    private var stopBackgroundTasks = false

    override fun dispose() {
        // make sure the worker threads will not continue trying their time-intensive job
        runningSearchJob?.cancel()
        stopBackgroundTasks = true
        super.dispose()
    }


    init {
        pickerPane.bottomTable.background = skinStrings.getUiBackground("ModManagementScreen/BottomTable", tintColor = skinStrings.skinConfig.clearColor)
        pickerPane.topTable.background = skinStrings.getUiBackground("ModManagementScreen/TopTable", tintColor = skinStrings.skinConfig.clearColor)
        topTable.top()  // So short lists won't vertically center everything including headers

        //setDefaultCloseAction() // we're adding the tileSet check
        rightSideButton.isVisible = false
        closeButton.onActivation {
            val tileSets = ImageGetter.getAvailableTilesets()
            if (game.settings.tileSet !in tileSets) {
                game.settings.tileSet = tileSets.first()
            }
            val screen = game.popScreen()

            // We want to immediately display/hide Scenario button based on changes
            if (screen is MainMenuScreen)
                screen.game.replaceCurrentScreen(MainMenuScreen())
        }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)

        val labelWidth = max(stage.width / 2f - 60f,60f)
        modDescriptionLabel = WrappableLabel("", labelWidth)
        modDescriptionLabel.wrap = true

        // Replace the PickerScreen's descriptionLabel
        val labelWrapper = Table()
        labelWrapper.defaults().top().left().growX()
        descriptionLabel.remove()
        labelWrapper.row()
        labelWrapper.add(modDescriptionLabel).row()
        descriptionScroll.actor = labelWrapper

        isPortrait = isNarrowerThan4to3()
        if (isPortrait) initPortrait()
        else initLandscape()
        showLoadingImage()

        if (installedModInfo.isEmpty())
            refreshInstalledModInfo()

        refreshInstalledModTable()

        refreshOnlineModTable() // Refresh table - chances are we have cached data...
        reloadOnlineMods() //... and still try to get fresh data from online
    }

    private fun initPortrait() {
        topTable.defaults().top().pad(0f)

        optionsCell = topTable.add().top().growX()
        topTable.row()

        installedExpanderTab = ExpanderTab(optionsManager.getInstalledHeader(), expanderWidth = stage.width) {
            it.add(scrollInstalledMods).growX().maxHeight(stage.height / 2)
        }
        topTable.add(installedExpanderTab).top().growX().row()

        onlineExpanderTab = ExpanderTab(optionsManager.getOnlineHeader(), expanderWidth = stage.width) {
            it.add(scrollOnlineMods).growX().maxHeight(stage.height / 2)
        }
        topTable.add(onlineExpanderTab).top().padTop(10f).growX().row()

        topTable.add().expandY().row() // keep action / info on the bottom if there's room to spare

        topTable.add(ExpanderTab("Mod info and options", expanderWidth = stage.width) {
            it.add(scrollActionTable).growX().maxHeight(stage.height / 2)
        }).bottom().padTop(10f).growX().row()
    }

    private fun initLandscape() {
        // Header row
        topTable.add().expandX()                // empty cols left and right for separator
        installedHeaderLabel = optionsManager.getInstalledHeader().toLabel()
        installedHeaderLabel!!.onClick {
            optionsManager.installedHeaderClicked()
        }
        topTable.add(installedHeaderLabel).pad(15f).minWidth(200f).padLeft(25f)
        onlineHeaderLabel = optionsManager.getOnlineHeader().toLabel()
        onlineHeaderLabel!!.onClick {
            optionsManager.onlineHeaderClicked()
        }
        topTable.add(onlineHeaderLabel).pad(15f)
        topTable.add("".toLabel()).minWidth(200f)  // placeholder for "Mod actions"
        topTable.add().expandX().row()

        // horizontal separator looking like the SplitPane handle
        topTable.addSeparator(Color.CLEAR, 5, 3f)

        // main row containing the three 'blocks' installed, online and information
        topTable.add().expandX()      // skip empty first column
        topTable.add(scrollInstalledMods)
        topTable.add(scrollOnlineMods)
        topTable.add(scrollActionTable)
        topTable.add().expandX().row()

    }

    private fun showLoadingImage() {
        val loadingStyle = LoadingImage.Style(circleColor = Color.DARK_GRAY, loadingColor = Color.FIREBRICK)
        // Size should fit where the Search and Filter expander will go, which is 48f high and set topRight with 2f margin.
        // When changing this, please also change the setPosition in landscape mode
        val loading = LoadingImage(40f, loadingStyle)
        this.loading = loading

        if (isPortrait) {
            optionsCell!!.pad(4f)
            optionsCell!!.setActor(loading)
        } else {// mute complaints - we _know_ it's not null
            optionsManager.expander.remove()
            loading.setPosition(stage.width - 6f, stage.height - 6f, Align.topRight)
            stage.addActor(loading)
        }

        loading.show()  // Now that it's on stage, start animation
        replaceLoadingWithOptions()

        // Allow clicking the loading icon to stop the query
        loading.onClick {
            if (runningSearchJob?.isActive != true) return@onClick
            runningSearchJob?.cancel()
            markOnlineQueryIncomplete()
        }
    }

    private fun replaceLoadingWithOptions() {
        val actorToRemove = loading ?: return
        loading = null
        actorToRemove.remove()  // This is able to remove from a Cell or (the floating version) from the parent's children
        actorToRemove.dispose()

        if (isPortrait) {
            optionsCell!!.pad(0f)
            optionsCell!!.setActor(optionsManager.expander)
        } else {
            stage.addActor(optionsManager.expander)
            optionsManager.expanderChangeEvent = {
                optionsManager.expander.pack()
                optionsManager.expander.setPosition(stage.width - 2f, stage.height - 2f, Align.topRight)
            }
            optionsManager.expanderChangeEvent?.invoke()
        }
    }

    private fun reloadOnlineMods() = tryDownloadPage(1)

    /** background worker: querying GitHub for Mods (repos with 'unciv-mod' in its topics)
     *
     *  calls itself for the next page of search results
     */
    private fun tryDownloadPage(pageNum: Int) {
        runningSearchJob = Concurrency.run("GitHubSearch") {
            val repoSearch: GithubAPI.RepoSearch?
            try {
                repoSearch = Github.tryGetGithubReposWithTopic(pageNum, amountPerPage)
            } catch (ex: Exception) {
                Log.error("Could not download mod list", ex)
                launchOnGLThread {
                    ToastPopup("Could not download mod list", this@ModManagementScreen)
                }
                try {
                    // If it's too large Android won't let you copy, hence the guardrails
                    Gdx.app.clipboard.contents = ex.stackTraceToString()
                } catch (_:Exception) {}

                runningSearchJob = null
                return@run
            }

            if (!isActive || repoSearch == null) {
                return@run
            }

            launchOnGLThread { addModInfoFromRepoSearch(repoSearch, pageNum) }
            runningSearchJob = null
        }
    }

    private fun addModInfoFromRepoSearch(repoSearch: GithubAPI.RepoSearch, pageNum: Int) {
        for (repo in repoSearch.items) {
            if (stopBackgroundTasks) return
            repo.name = repo.name.repoNameToFolderName()

            val installedMod = RulesetCache.values.firstOrNull { it.name == repo.name }
            val isUpdatedVersionOfInstalledMod = installedMod?.modOptions?.let {
                it.lastUpdated != "" && it.lastUpdated != repo.pushed_at
            } == true

            if (installedMod != null) {

                if (isUpdatedVersionOfInstalledMod) {
                    val modInfo = installedModInfo[repo.name]!!
                    modInfo.hasUpdate = true
                    modButtons[modInfo]?.updateIndicators()
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
            modButtons.remove(mod) // Remove *cached* mod button since we have NEW DATA
            onlineModsTable.add(getCachedModButton(mod)).row()
        }

        Concurrency.run("Cache mod list"){
            game.files.saveModCache(onlineModInfo.values.toList())
        }

        // Now the tasks after the 'page' of search results has been fully processed
        // The search has reached the last page!
        if (repoSearch.items.size < amountPerPage) {
            // Check: It is also not impossible we missed a mod - just inform user
            if (repoSearch.incomplete_results) {
                markOnlineQueryIncomplete()
            }
        }

        onlineModsTable.pack()
        // Shouldn't actor.parent.actor = actor be a no-op? No, it has side effects we need.
        // See [commit for #3317](https://github.com/yairm210/Unciv/commit/315a55f972b8defe22e76d4a2d811c6e6b607e57)
        scrollOnlineMods.actor = onlineModsTable

        // continue search unless last page was reached
        if (repoSearch.items.size >= amountPerPage && !stopBackgroundTasks)
            tryDownloadPage(pageNum + 1)
    }

    private fun markOnlineQueryIncomplete() {
        val retryLabel = "Online query result is incomplete".toLabel(Color.RED)
        retryLabel.touchable = Touchable.enabled
        retryLabel.onClick {
            showLoadingImage()
            reloadOnlineMods()
        }
        onlineModsTable.add(retryLabel)
    }

    private fun syncOnlineSelected(modName: String, button: ModDecoratedButton) {
        syncSelected(modName, button, installedModInfo, scrollInstalledMods)
    }
    private fun syncInstalledSelected(modName: String, button: ModDecoratedButton) {
        syncSelected(modName, button, onlineModInfo, scrollOnlineMods)
    }
    private fun syncSelected(modName: String, button: ModDecoratedButton, modNameToData: HashMap<String, ModUIData>, scroll: ScrollPane) {
        // manage selection color for user selection
        lastSelectedButton?.color = Color.WHITE
        button.color = Color.BLUE
        lastSelectedButton = button
        if (lastSelectedButton != lastSyncMarkedButton)
            lastSyncMarkedButton?.color = Color.WHITE
        lastSyncMarkedButton = null
        // look for sync-able same mod in other list
        val buttonInOtherList = modButtons[modNameToData[modName]] ?: return
        // scroll into view - we know the containing Tables all have cell default padding 10f
        scroll.scrollTo(0f, buttonInOtherList.y - 10f, scroll.actor.width, buttonInOtherList.height + 20f, true, false)
        // and color it so it's easier to find. ROYAL and SLATE too dark.
        buttonInOtherList.color = Color.valueOf("7499ab")  // about halfway between royal and sky
        lastSyncMarkedButton = buttonInOtherList
    }


    /** Create the special "Download from URL" button */
    private fun getDownloadFromUrlButton(): TextButton {
        val downloadButton = "Download mod from URL".toTextButton()
        downloadButton.onClick {
            val popup = Popup(this)
            popup.addGoodSizedLabel("Please enter the mod repository -or- archive zip -or- branch -or- release url:").row()
            val textField = UncivTextField("").apply { maxLength = 666 }
            popup.add(textField).width(stage.width / 2).row()
            val pasteLinkButton = "Paste from clipboard".toTextButton()
            pasteLinkButton.onClick {
                textField.text = Gdx.app.clipboard.contents
            }
            popup.add(pasteLinkButton).row()
            val actualDownloadButton = "Download".toTextButton()
            actualDownloadButton.onClick {
                actualDownloadButton.setText("Downloading...".tr())
                actualDownloadButton.disable()
                Concurrency.run {
                    val repo = GithubAPI.Repo.parseUrl(textField.text)
                    if (repo == null) {
                        Concurrency.runOnGLThread {
                            ToastPopup("«RED»{Invalid link!}«»", this@ModManagementScreen)
                            actualDownloadButton.setText("Download".tr())
                            actualDownloadButton.enable()
                        }
                    } else
                        downloadMod(repo, {
                            actualDownloadButton.setText("{Downloading...} ${it}%".tr())
                        }) { popup.close() }
                }
            }
            popup.add(actualDownloadButton).row()
            popup.addCloseButton()
            popup.open()
        }
        return downloadButton
    }

    /** Used as onClick handler for the online Mod list buttons */
    private fun onlineButtonAction(repo: GithubAPI.Repo, button: ModDecoratedButton) {
        syncOnlineSelected(repo.name, button)
        showModDescription(repo.name)

        if (!repo.hasUpdatedSize) {
            // Setting this later would mean a failed query is repeated on the next mod click,
            // and click-spamming would launch several github queries.
            repo.hasUpdatedSize = true
            Concurrency.run("GitHubParser") {
                try {
                    val repoSize = Github.getRepoSize(repo)
                    if (repoSize > -1) {
                        launchOnGLThread {
                            repo.size = repoSize
                            if (selectedMod == repo)
                                modActionTable.updateSize(repoSize)
                        }
                    }
                } catch (ignore: IOException) {
                    /* Parsing of mod size failed, do nothing */
                }
            }
        }

        rightSideButton.isVisible = true
        rightSideButton.enable()
        val label = if (installedModInfo[repo.name]?.hasUpdate == true) "Update [${cleanModName(repo.name)}]"
            else "Download [${cleanModName(repo.name)}]"
        rightSideButton.setText(label.tr())
        rightSideButton.clearActivationActions(ActivationTypes.Tap)
        rightSideButton.onClick {
            rightSideButton.setText("Downloading...".tr())
            rightSideButton.disable()

            downloadMod(repo,{
                rightSideButton.setText("{Downloading...} ${it}%".tr())
            }) {
                rightSideButton.setText("Downloaded!".tr())
            }
        }

        selectedMod = repo
        modActionTable.update(repo)
    }

    /** Download and install a mod in the background, called both from the right-bottom button and the URL entry popup */
    private fun downloadMod(repo: GithubAPI.Repo, updateProgressPercent: ((Int)->Unit)? = null, postAction: () -> Unit = {}) {
        Concurrency.run("DownloadMod") { // to avoid ANRs - we've learnt our lesson from previous download-related actions
            try {
                val modFolder =
                    repo.downloadAndExtract(
                        UncivGame.Current.files.getModsFolder(),
                        updateProgressPercent
                    )
                        ?: throw Exception("Exception during GitHub download")    // downloadAndExtract returns null for 404 errors and the like -> display something!
                Github.rewriteModOptions(repo, modFolder)
                launchOnGLThread {
                    val repoName = modFolder.name()  // repo.name still has the replaced "-"'s
                    ToastPopup("[$repoName] Downloaded!", this@ModManagementScreen)
                    reloadCachesAfterModChange()

                    updateInstalledModUIData(repoName)
                    refreshInstalledModTable()
                    lastSelectedButton?.let { syncOnlineSelected(repoName, it) }
                    showModDescription(repoName)
                    unMarkUpdatedMod(repoName)
                    postAction()
                }
            } catch (ex: UncivShowableException) {
                Log.error("Could not download $repo", ex)
                launchOnGLThread {
                    ToastPopup(ex.message, this@ModManagementScreen)
                    postAction()
                }
            } catch (ex: Exception) {
                Log.error("Could not download $repo", ex)
                launchOnGLThread {
                    ToastPopup("Could not download [${repo.name}]", this@ModManagementScreen)
                    postAction()
                }
            }
        }
    }

    /** Our data on the Mod needs refreshing description after download or update */
    private fun updateInstalledModUIData(modName: String) {
        val ruleset = RulesetCache[modName]
            ?: return  // Bail if download was not actually successful?
        // When someone marks a Mod as 'permanent audiovisual', then deletes it, then redownloads, that
        // 'permanent audiovisual' will still be valid - re-evaluate here or remove the setting in the delete code.
        val isVisual = game.settings.visualMods.contains(modName)
        val newModUIData = ModUIData(ruleset, isVisual)
        installedModInfo[modName] = newModUIData
        // The ModUIData in the actual button is now out of sync, but can be indexed using the new instance
        modButtons[newModUIData]?.run {
            updateUIData(newModUIData)
            // The listeners have also captured a now outdated ModUIData
            setModButtonOnClick(this, newModUIData)
            // Simulate click to update the ModInfoAndActionPane
            installedButtonAction(newModUIData, this)
        }
    }

    /** Remove the visual indicators for an 'updated' mod after re-downloading it.
     *  (" - Updated" on the button text in the online mod list and the icon beside the installed mod's button)
     *  It should be up to date now (unless the repo's date is in the future relative to system time)
     *
     *  (called under postRunnable posted by background thread)
     */
    private fun unMarkUpdatedMod(name: String) {
        installedModInfo[name]?.run {
            hasUpdate = false
            modButtons[this]?.updateIndicators()
        }
        onlineModInfo[name]?.run {
            hasUpdate = false
            modButtons[this]?.setText(cleanModName(name))
        }
        if (optionsManager.sortInstalled == SortType.Status)
            refreshInstalledModTable()
        if (optionsManager.sortOnline == SortType.Status)
            refreshOnlineModTable()
    }

    /** Rebuild the right-hand column for clicks on installed mods
     *  Display single mod metadata, offer additional actions (delete is elsewhere)
    */
    private fun refreshInstalledModActions(mod: Ruleset) {
        selectedMod = null
        // show mod information first
        modActionTable.update(mod)

        val modInfo = installedModInfo[mod.name]!!

        // offer 'permanent visual mod' toggle
        val isVisualMod = game.settings.visualMods.contains(mod.name)
        if (modInfo.isVisual != isVisualMod) {
            modInfo.isVisual = isVisualMod
            modButtons[modInfo]?.updateIndicators()
        }

        modActionTable.addVisualCheckBox(isVisualMod) { checked ->
            if (checked)
                game.settings.visualMods.add(mod.name)
            else
                game.settings.visualMods.remove(mod.name)
            game.settings.save()
            ImageGetter.reloadImages()
            refreshInstalledModActions(mod)
            if (optionsManager.sortInstalled == SortType.Status)
                refreshInstalledModTable()
        }

        modActionTable.addUpdateModButton(modInfo) {
            val repo = onlineModInfo[mod.name]!!.repo!!
            downloadMod(repo) { refreshInstalledModActions(mod) }
        }
    }

    /** Rebuild the metadata on installed mods */
    private fun refreshInstalledModInfo() {
        installedModInfo.clear()
        for (mod in RulesetCache.values.asSequence().filter { it.name != "" }) {
            installedModInfo[mod.name] = ModUIData(mod, mod.name in game.settings.visualMods)
        }
    }

    private fun getCachedModButton(mod: ModUIData) = modButtons.getOrPut(mod) {
        val newButton = ModDecoratedButton(mod)
        setModButtonOnClick(newButton, mod)
        newButton
    }
    private fun setModButtonOnClick(button: ModDecoratedButton, mod: ModUIData) {
        if (mod.isInstalled) button.onClick { installedButtonAction(mod, button) }
        else button.onClick { onlineButtonAction(mod.repo!!, button) }
    }

    /** Rebuild the left-hand column containing all installed mods */
    internal fun refreshInstalledModTable() {
        val newHeaderText = optionsManager.getInstalledHeader()
        installedHeaderLabel?.setText(newHeaderText)
        installedExpanderTab?.setText(newHeaderText)

        installedModsTable.clear()
        val filter = optionsManager.getFilter()
        for (mod in installedModInfo.values.sortedWith(optionsManager.sortInstalled.comparator)) {
            if (!mod.matchesFilter(filter)) continue
            installedModsTable.add(getCachedModButton(mod)).row()
        }
    }

    private fun installedButtonAction(mod: ModUIData, button: ModDecoratedButton) {
        rightSideButton.isVisible = true

        syncInstalledSelected(mod.name, button)
        refreshInstalledModActions(mod.ruleset!!)
        val deleteText = "Delete [${cleanModName(mod.name)}]"
        rightSideButton.setText(deleteText.tr())
        // Don't let the player think he can delete Vanilla and G&K rulesets
        rightSideButton.isEnabled = mod.ruleset.folderLocation!=null
        showModDescription(mod.name)
        rightSideButton.clearActivationActions(ActivationTypes.Tap)  // clearListeners would also kill mouseover styling
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
                rightSideButton.setText("[${cleanModName(mod.name)}] was deleted.".tr())
            }.open()
        }
    }

    /** Delete a Mod, refresh ruleset cache and update installed mod table */
    private fun deleteMod(mod: Ruleset) {
        mod.folderLocation!!.deleteDirectory()
        reloadCachesAfterModChange()
        installedModInfo.remove(mod.name)
        unMarkUpdatedMod(mod.name)
        refreshInstalledModTable()
    }

    private fun reloadCachesAfterModChange() {
        RulesetCache.loadRulesets()
        TileSetCache.loadTileSetConfigs()
        ImageGetter.reloadImages()
        UncivGame.Current.translations.tryReadTranslationForCurrentLanguage()
    }

    internal fun refreshOnlineModTable() {
//        if (runningSearchJob != null) {
//            ToastPopup("Sorting and filtering needs to wait until the online query finishes", this)
//            return  // cowardice: prevent concurrent modification, avoid a manager layer
//        }

        val newHeaderText = optionsManager.getOnlineHeader()
        onlineHeaderLabel?.setText(newHeaderText)
        onlineExpanderTab?.setText(newHeaderText)

        onlineModsTable.clear()
        onlineModsTable.add(getDownloadFromUrlButton()).row()

        val filter = optionsManager.getFilter()
        // Important: sortedMods holds references to the original values, so the referenced buttons stay valid.
        // We update y and height here, we do not replace the ModUIData instances do the referenced buttons stay valid.
        val sortedMods = onlineModInfo.values.asSequence().sortedWith(optionsManager.sortOnline.comparator)
        for (mod in sortedMods) {
            if (!mod.matchesFilter(filter)) continue
            onlineModsTable.add(getCachedModButton(mod)).row()
        }

        onlineModsTable.pack()
        scrollOnlineMods.actor = onlineModsTable
    }

    /** Updates the description label at the bottom of the screen */
    private fun showModDescription(modName: String) {
        val onlineModDescription = onlineModInfo[modName]?.description ?: "" // shows github info
        val installedModDescription = installedModInfo[modName]?.description ?: "" // shows ruleset info
        val separator = if (onlineModDescription.isEmpty() || installedModDescription.isEmpty()) "" else "\n"
        modDescriptionLabel.setText(onlineModDescription + separator + installedModDescription)
    }

    override fun recreate(): BaseScreen = ModManagementScreen(installedModInfo, onlineModInfo)

}
