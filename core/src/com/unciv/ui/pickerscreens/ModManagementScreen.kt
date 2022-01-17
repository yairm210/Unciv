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
import com.unciv.ui.pickerscreens.ModManagementOptions.SortType
import com.unciv.ui.utils.UncivDateFormat.formatDate
import com.unciv.ui.utils.UncivDateFormat.parseDate
import com.unciv.ui.worldscreen.mainmenu.Github
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.thread
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
): PickerScreen(disableScroll = true) {

    private val modTable = Table().apply { defaults().pad(10f) }
    private val scrollInstalledMods = AutoScrollPane(modTable)
    private val downloadTable = Table().apply { defaults().pad(10f) }
    private val scrollOnlineMods = AutoScrollPane(downloadTable)
    private val modActionTable = Table().apply { defaults().pad(10f) }
    private val optionsManager = ModManagementOptions(this)

    val amountPerPage = 30

    private var lastSelectedButton: Button? = null
    private var lastSyncMarkedButton: Button? = null
    private var selectedModName = ""
    private var selectedAuthor = ""

    private val deprecationLabel: WrappableLabel
    private val deprecationCell: Cell<WrappableLabel>
    private val modDescriptionLabel: WrappableLabel

    private var installedHeaderLabel: Label? = null
    private var onlineHeaderLabel: Label? = null
    private var installedExpanderTab: ExpanderTab? = null
    private var onlineExpanderTab: ExpanderTab? = null

    // keep running count of mods fetched from online search for comparison to total count as reported by GitHub
    private var downloadModCount = 0

    // Enable re-sorting and syncing entries in 'installed' and 'repo search' ScrollPanes
    private val installedModInfo = previousInstalledMods ?: HashMap(10) // HashMap<String, ModUIData> inferred
    private val onlineModInfo = previousOnlineMods ?: HashMap(90) // HashMap<String, ModUIData> inferred

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

        val labelWidth = max(stage.width / 2f - 60f,60f)
        deprecationLabel = WrappableLabel(deprecationText, labelWidth, Color.FIREBRICK)
        deprecationLabel.wrap = true
        modDescriptionLabel = WrappableLabel("", labelWidth)
        modDescriptionLabel.wrap = true

        // Replace the PickerScreen's descriptionLabel
        val labelWrapper = Table()
        labelWrapper.defaults().top().left().growX()
        val labelScroll = descriptionLabel.parent as ScrollPane
        descriptionLabel.remove()
        deprecationCell = labelWrapper.add(deprecationLabel).padBottom(10f)
        deprecationLabel.remove()
        labelWrapper.row()
        labelWrapper.add(modDescriptionLabel).row()
        labelScroll.actor = labelWrapper

        refreshInstalledModTable()

        if (isNarrowerThan4to3()) initPortrait()
        else initLandscape()

        keyPressDispatcher[KeyCharAndCode.RETURN] = optionsManager.filterAction

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
        runningSearchThread = crashHandlingThread(name="GitHubSearch") {
            val repoSearch: Github.RepoSearch
            try {
                repoSearch = Github.tryGetGithubReposWithTopic(amountPerPage, pageNum)!!
            } catch (ex: Exception) {
                postCrashHandlingRunnable {
                    ToastPopup("Could not download mod list", this)
                }
                runningSearchThread = null
                return@crashHandlingThread
            }

            postCrashHandlingRunnable {
                // clear and remove last cell if it is the "..." indicator
                val lastCell = downloadTable.cells.lastOrNull()
                if (lastCell != null && lastCell.actor is Label && (lastCell.actor as Label).text.toString() == "...") {
                    lastCell.setActor<Actor>(null)
                    downloadTable.cells.removeValue(lastCell, true)
                }

                for (repo in repoSearch.items) {
                    if (stopBackgroundTasks) return@postCrashHandlingRunnable
                    repo.name = repo.name.replace('-', ' ')

                    // Mods we have manually decided to remove for instability are removed here
                    // If at some later point these mods are updated, we should definitely remove
                    // this piece of code. This is a band-aid, not a full solution.
                    if (repo.html_url in modsToHideAsUrl) continue

                    val existingMod = RulesetCache.values.firstOrNull { it.name == repo.name }
                    val isUpdated = existingMod?.modOptions?.let {
                        it.lastUpdated != "" && it.lastUpdated != repo.updated_at
                    } == true

                    if (existingMod != null) {
                        if (isUpdated) {
                            installedModInfo[repo.name]?.state?.isUpdated = true
                        }
                        if (existingMod.modOptions.author.isEmpty()) {
                            rewriteModOptions(repo, Gdx.files.local("mods").child(repo.name))
                            existingMod.modOptions.author = repo.owner.login
                            existingMod.modOptions.modSize = repo.size
                        }
                    }

                    val mod = ModUIData(repo, isUpdated)
                    onlineModInfo[repo.name] = mod
                    mod.button.onClick { onlineButtonAction(repo, mod.button) }

                    val cell = downloadTable.add(mod.button)
                    downloadTable.row()
                    if (onlineScrollCurrentY < 0f) onlineScrollCurrentY = cell.padTop
                    mod.y = onlineScrollCurrentY
                    mod.height = cell.prefHeight
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
                        downloadTable.cells.removeValue(it, true)
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
        syncSelected(name, button, installedModInfo, scrollInstalledMods)
    }
    private fun syncInstalledSelected(name: String, button: Button) {
        syncSelected(name, button, onlineModInfo, scrollOnlineMods)
    }
    private fun syncSelected(name: String, button: Button, index: HashMap<String, ModUIData>, scroll: ScrollPane) {
        // manage selection color for user selection
        lastSelectedButton?.color = Color.WHITE
        button.color = Color.BLUE
        lastSelectedButton = button
        if (lastSelectedButton != lastSyncMarkedButton)
            lastSyncMarkedButton?.color = Color.WHITE
        lastSyncMarkedButton = null
        // look for sync-able same mod in other list
        val pos = index[name] ?: return
        // scroll into view
        scroll.scrollY = (pos.y + (pos.height - scroll.height) / 2).coerceIn(0f, scroll.maxY)
        // and color it so it's easier to find. ROYAL and SLATE too dark.
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
            popup.addGoodSizedLabel("Please enter the mod repository -or- archive zip url:").row()
            val textArea = TextArea("https://github.com/...", skin)
            popup.add(textArea).width(stage.width / 2).row()
            val actualDownloadButton = "Download".toTextButton()
            actualDownloadButton.onClick {
                actualDownloadButton.setText("Downloading...".tr())
                actualDownloadButton.disable()
                downloadMod(Github.Repo().parseUrl(textArea.text)) { popup.close() }
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
        val label = if (installedModInfo[repo.name]?.state?.isUpdated == true)
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
        crashHandlingThread(name="DownloadMod") { // to avoid ANRs - we've learnt our lesson from previous download-related actions
            try {
                val modFolder = Github.downloadAndExtract(repo.html_url, repo.default_branch,
                    Gdx.files.local("mods"))
                    ?: throw Exception()    // downloadAndExtract returns null for 404 errors and the like -> display something!
                rewriteModOptions(repo, modFolder)
                postCrashHandlingRunnable {
                    ToastPopup("[${repo.name}] Downloaded!", this)
                    RulesetCache.loadRulesets()
                    RulesetCache[repo.name]?.let { 
                        installedModInfo[repo.name] = ModUIData(it)
                    }
                    refreshInstalledModTable()
                    showModDescription(repo.name)
                    unMarkUpdatedMod(repo.name)
                }
            } catch (ex: Exception) {
                postCrashHandlingRunnable {
                    ToastPopup("Could not download [${repo.name}]", this)
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
        installedModInfo[name]?.state?.isUpdated = false
        onlineModInfo[name]?.state?.isUpdated = false
        val button = (onlineModInfo[name]?.button as? TextButton)
        button?.setText(name)
        if (optionsManager.sortInstalled == SortType.Status)
            refreshInstalledModTable()
        if (optionsManager.sortOnline == SortType.Status)
            refreshOnlineModTable()
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
        installedModInfo[mod.name]?.state?.isVisual = isVisual

        val visualCheckBox = "Permanent audiovisual mod".toCheckBox(isVisual) {
            checked ->
            if (checked)
                visualMods.add(mod.name)
            else
                visualMods.remove(mod.name)
            game.settings.save()
            ImageGetter.setNewRuleset(ImageGetter.ruleset)
            refreshModActions(mod)
            if (optionsManager.sortInstalled == SortType.Status)
                refreshInstalledModTable()
        }
        modActionTable.add(visualCheckBox).row()
    }

    /** Rebuild the left-hand column containing all installed mods */
    internal fun refreshInstalledModTable() {
        // pre-init if not already done - important: keep the ModUIData instances later on or
        // at least the button references otherwise sync will not work
        if (installedModInfo.isEmpty()) {
            for (mod in RulesetCache.values.asSequence().filter { it.name != "" }) {
                ModUIData(mod).run {
                    state.isVisual = mod.name in game.settings.visualMods
                    installedModInfo[mod.name] = this
                }
            }
        }

        val newHeaderText = optionsManager.getInstalledHeader()
        installedHeaderLabel?.setText(newHeaderText)
        installedExpanderTab?.setText(newHeaderText)

        modTable.clear()
        var currentY = -1f
        val filter = optionsManager.getFilterText()
        for (mod in installedModInfo.values.sortedWith(optionsManager.sortInstalled.comparator)) {
            if (!mod.matchesFilter(filter)) continue
            // Prevent building up listeners. The virgin Button has one: for mouseover styling.
            // The captures for our listener shouldn't need updating, so assign only once
            if (mod.button.listeners.none { it.javaClass.`package`.name.startsWith("com.unciv") })
                mod.button.onClick { installedButtonAction(mod) }
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
        refreshModActions(mod.ruleset!!)
        rightSideButton.setText("Delete [${mod.name}]".tr())
        rightSideButton.isEnabled = true
        showModDescription(mod.name)
        removeRightSideClickListeners()
        rightSideButton.onClick {
            rightSideButton.isEnabled = false
            YesNoPopup(
                question = "Are you SURE you want to delete this mod?",
                action = {
                    deleteMod(mod.name)
                    rightSideButton.setText("[${mod.name}] was deleted.".tr())
                },
                screen = this,
                restoreDefault = { rightSideButton.isEnabled = true }
            ).open()
        }
    }

    /** Delete a Mod, refresh ruleset cache and update installed mod table */
    private fun deleteMod(modName: String) {
        val modFileHandle = Gdx.files.local("mods").child(modName)
        if (modFileHandle.isDirectory) modFileHandle.deleteDirectory()
        else modFileHandle.delete()     // This should never happen
        RulesetCache.loadRulesets()
        installedModInfo.remove(modName)
        refreshInstalledModTable()
    }

    internal fun refreshOnlineModTable() {
        if (runningSearchThread != null) return  // cowardice: prevent concurrent modification, avoid a manager layer

        val newHeaderText = optionsManager.getOnlineHeader()
        onlineHeaderLabel?.setText(newHeaderText)
        onlineExpanderTab?.setText(newHeaderText)

        downloadTable.clear()
        onlineScrollCurrentY = -1f

        val filter = optionsManager.getFilterText()
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
        val online = onlineModInfo[modName]?.description ?: ""
        val installed = installedModInfo[modName]?.description ?: ""
        val separator = if (online.isEmpty() || installed.isEmpty()) "" else "\n"
        deprecationCell.setActor(if (modName in modsToHideNames) deprecationLabel else null)
        modDescriptionLabel.setText(online + separator + installed)
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(ModManagementScreen(installedModInfo, onlineModInfo))
            dispose()  // interrupt background loader - sorry, the resized new screen won't continue
        }
    }

    companion object {
        val modsToHideAsUrl = run {
            val blockedModsFile = Gdx.files.internal("jsons/ManuallyBlockedMods.json")
            JsonParser().getFromJson(Array<String>::class.java, blockedModsFile)
        }
        val modsToHideNames = run {
            val regex = Regex(""".*/([^/]+)/?$""")
            modsToHideAsUrl.map { url ->
                regex.replace(url) { it.groups[1]!!.value }.replace('-', ' ')
            }
        }
        const val deprecationText = "Deprecated until update conforms to current requirements"
    }
}
