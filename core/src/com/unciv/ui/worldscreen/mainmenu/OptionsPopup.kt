package com.unciv.ui.worldscreen.mainmenu

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Ruleset.RulesetError
import com.unciv.models.ruleset.Ruleset.RulesetErrorSeverity
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.TranslationFileWriter
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.utils.*
import com.unciv.ui.utils.LanguageTable.Companion.addLanguageTables
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.worldscreen.WorldScreen
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.floor
import com.badlogic.gdx.utils.Array as GdxArray

/**
 * The Options (Settings) Popup
 * @param previousScreen The caller - note if this is a [WorldScreen] or [MainMenuScreen] they will be rebuilt when major options change.
 */
//region Fields
class OptionsPopup(val previousScreen: BaseScreen) : Popup(previousScreen) {
    private val settings = previousScreen.game.settings
    private val tabs: TabbedPager
    private val resolutionArray = com.badlogic.gdx.utils.Array(arrayOf("750x500", "900x600", "1050x700", "1200x800", "1500x1000"))
    private var modCheckFirstRun = true   // marker for automatic first run on selecting the page
    private var modCheckCheckBox: CheckBox? = null
    private val modCheckResultTable = Table()
    private val selectBoxMinWidth: Float

    //endregion

    init {
        settings.addCompletedTutorialTask("Open the options table")

        innerTable.pad(0f)
        val tabMaxWidth: Float
        val tabMinWidth: Float
        val tabMaxHeight: Float
        previousScreen.run {
            selectBoxMinWidth = if (stage.width < 600f) 200f else 240f
            tabMaxWidth = if (isPortrait()) stage.width - 10f else 0.8f * stage.width
            tabMinWidth = 0.6f * stage.width
            tabMaxHeight = (if (isPortrait()) 0.7f else 0.8f) * stage.height
        }
        tabs = TabbedPager(tabMinWidth, tabMaxWidth, 0f, tabMaxHeight,
            headerFontSize = 21, backgroundColor = Color.CLEAR, capacity = 8)
        add(tabs).pad(0f).grow().row()

        tabs.addPage("About", getAboutTab(), ImageGetter.getExternalImage("Icon.png"), 24f)
        tabs.addPage("Display", getDisplayTab(), ImageGetter.getImage("UnitPromotionIcons/Scouting"), 24f)
        tabs.addPage("Gameplay", getGamePlayTab(), ImageGetter.getImage("OtherIcons/Options"), 24f)
        tabs.addPage("Language", getLanguageTab(), ImageGetter.getImage("FlagIcons/${settings.language}"), 24f)
        tabs.addPage("Sound", getSoundTab(), ImageGetter.getImage("OtherIcons/Speaker"), 24f)
        // at the moment the notification service only exists on Android
        if (Gdx.app.type == Application.ApplicationType.Android)
            tabs.addPage("Multiplayer", getMultiplayerTab(), ImageGetter.getImage("OtherIcons/Multiplayer"), 24f)
        tabs.addPage("Advanced", getAdvancedTab(), ImageGetter.getImage("OtherIcons/Settings"), 24f)
        if (RulesetCache.size > 1) {
            tabs.addPage("Locate mod errors", getModCheckTab(), ImageGetter.getImage("OtherIcons/Mods"), 24f) { _, _ ->
                if (modCheckFirstRun) runModChecker()
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT))) {
            tabs.addPage("Debug", getDebugTab(), ImageGetter.getImage("OtherIcons/SecretOptions"), 24f, secret = true)
        }

        addCloseButton {
            previousScreen.game.musicController.onChange(null)
            previousScreen.game.limitOrientationsHelper?.allowPortrait(settings.allowAndroidPortrait)
            if (previousScreen is WorldScreen)
                previousScreen.enableNextTurnButtonAfterOptions()
        }.padBottom(10f)

        pack() // Needed to show the background.
        center(previousScreen.stage)
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        if (!visible) return
        tabs.askForPassword(secretHashCode = 2747985)
        if (tabs.activePage < 0) tabs.selectPage(2)
    }

    /** Reload this Popup after major changes (resolution, tileset, language) */
    private fun reloadWorldAndOptions() {
        settings.save()
        if (previousScreen is WorldScreen) {
            previousScreen.game.worldScreen = WorldScreen(previousScreen.gameInfo, previousScreen.viewingCiv)
            previousScreen.game.setWorldScreen()
        } else if (previousScreen is MainMenuScreen) {
            previousScreen.game.setScreen(MainMenuScreen())
        }
        (previousScreen.game.screen as BaseScreen).openOptionsPopup()
    }

    //region Page builders

    private fun getAboutTab(): Table {
        defaults().pad(5f)
        val version = previousScreen.game.version
        val versionAnchor = version.replace(".","")
        val lines = sequence {
            yield(FormattedLine(extraImage = "banner", imageSize = 240f, centered = true))
            yield(FormattedLine())
            yield(FormattedLine("{Version}: $version", link = "https://github.com/yairm210/Unciv/blob/master/changelog.md#$versionAnchor"))
            yield(FormattedLine("See online Readme", link = "https://github.com/yairm210/Unciv/blob/master/README.md#unciv---foss-civ-v-for-androiddesktop"))
            yield(FormattedLine("Visit repository", link = "https://github.com/yairm210/Unciv"))
        }
        return MarkupRenderer.render(lines.toList()).pad(20f)
    }

    private fun getLanguageTab() = Table(BaseScreen.skin).apply {
        val languageTables = this.addLanguageTables(tabs.prefWidth * 0.9f - 10f)

        var chosenLanguage = settings.language
        fun selectLanguage() {
            settings.language = chosenLanguage
            settings.updateLocaleFromLanguage()
            previousScreen.game.translations.tryReadTranslationForCurrentLanguage()
            reloadWorldAndOptions()
        }
        fun updateSelection() {
            languageTables.forEach { it.update(chosenLanguage) }
            if (chosenLanguage != settings.language)
                selectLanguage()
        }
        updateSelection()

        languageTables.forEach {
            it.onClick {
                chosenLanguage = it.language
                updateSelection()
            }
        }
    }

    private fun getDisplayTab() = Table(BaseScreen.skin).apply {
        pad(10f)
        defaults().pad(2.5f)

        addYesNoRow("Show unit movement arrows", settings.showUnitMovements, true) { settings.showUnitMovements = it }
        addYesNoRow("Show tile yields", settings.showTileYields, true) { settings.showTileYields = it } // JN
        addYesNoRow("Show worked tiles", settings.showWorkedTiles, true) { settings.showWorkedTiles = it }
        addYesNoRow("Show resources and improvements", settings.showResourcesAndImprovements, true) { settings.showResourcesAndImprovements = it }
        addYesNoRow("Show tutorials", settings.showTutorials, true) { settings.showTutorials = it }
        addMinimapSizeSlider()

        addYesNoRow("Show pixel units", settings.showPixelUnits, true) { settings.showPixelUnits = it }
        addYesNoRow("Show pixel improvements", settings.showPixelImprovements, true) { settings.showPixelImprovements = it }

        addResolutionSelectBox()

        addTileSetSelectBox()

        addYesNoRow("Continuous rendering", settings.continuousRendering) {
            settings.continuousRendering = it
            Gdx.graphics.isContinuousRendering = it
        }

        val continuousRenderingDescription = "When disabled, saves battery life but certain animations will be suspended"
        val continuousRenderingLabel = WrappableLabel(continuousRenderingDescription,
                tabs.prefWidth, Color.ORANGE.cpy().lerp(Color.WHITE, 0.7f), 14)
        continuousRenderingLabel.wrap = true
        add(continuousRenderingLabel).colspan(2).padTop(10f).row()
    }

    private fun getGamePlayTab() = Table(BaseScreen.skin).apply {
        pad(10f)
        defaults().pad(5f)
        addYesNoRow("Check for idle units", settings.checkForDueUnits, true) { settings.checkForDueUnits = it }
        addYesNoRow("Move units with a single tap", settings.singleTapMove) { settings.singleTapMove = it }
        addYesNoRow("Auto-assign city production", settings.autoAssignCityProduction, true) {
            settings.autoAssignCityProduction = it
            if (it && previousScreen is WorldScreen &&
                previousScreen.viewingCiv.isCurrentPlayer() && previousScreen.viewingCiv.playerType == PlayerType.Human) {
                previousScreen.gameInfo.currentPlayerCiv.cities.forEach { city ->
                    city.cityConstructions.chooseNextConstruction()
                }
            }
        }
        addYesNoRow("Auto-build roads", settings.autoBuildingRoads) { settings.autoBuildingRoads = it }
        addYesNoRow("Automated workers replace improvements", settings.automatedWorkersReplaceImprovements) { settings.automatedWorkersReplaceImprovements = it }
        addYesNoRow("Order trade offers by amount", settings.orderTradeOffersByAmount) { settings.orderTradeOffersByAmount = it }
    }

    private fun getSoundTab() = Table(BaseScreen.skin).apply {
        pad(10f)
        defaults().pad(5f)

        addSoundEffectsVolumeSlider()

        if (previousScreen.game.musicController.isMusicAvailable()) {
            addMusicVolumeSlider()
            addMusicPauseSlider()
            addMusicCurrentlyPlaying()
        } else {
            addDownloadMusic()
        }
    }

    private fun getMultiplayerTab(): Table = Table(BaseScreen.skin).apply {
        pad(10f)
        defaults().pad(5f)

        addYesNoRow("Enable out-of-game turn notifications", settings.multiplayerTurnCheckerEnabled) {
            settings.multiplayerTurnCheckerEnabled = it
            settings.save()
            tabs.replacePage("Multiplayer", getMultiplayerTab())
        }

        if (settings.multiplayerTurnCheckerEnabled) {
            addMultiplayerTurnCheckerDelayBox()

            addYesNoRow("Show persistent notification for turn notifier service", settings.multiplayerTurnCheckerPersistentNotificationEnabled)
                { settings.multiplayerTurnCheckerPersistentNotificationEnabled = it }
        }
    }

    private fun getAdvancedTab() = Table(BaseScreen.skin).apply {
        pad(10f)
        defaults().pad(5f)

        addAutosaveTurnsSelectBox()

        addYesNoRow("{Show experimental world wrap for maps}\n{HIGHLY EXPERIMENTAL - YOU HAVE BEEN WARNED!}",
            settings.showExperimentalWorldWrap) {
            settings.showExperimentalWorldWrap = it
        }

        if (previousScreen.game.limitOrientationsHelper != null) {
            addYesNoRow("Enable portrait orientation", settings.allowAndroidPortrait) {
                settings.allowAndroidPortrait = it
                // Note the following might close the options screen indirectly and delayed
                previousScreen.game.limitOrientationsHelper.allowPortrait(it)
            }
        }

        addTranslationGeneration()

        addSetUserId()
    }

    private fun getModCheckTab() = Table(BaseScreen.skin).apply {
        defaults().pad(10f).align(Align.top)
        val reloadModsButton = "Reload mods".toTextButton().onClick {
            runModChecker(modCheckCheckBox!!.isChecked)
        }
        add(reloadModsButton).row()
        modCheckCheckBox = "Check extension mods based on vanilla".toCheckBox {
            runModChecker(it)
        }
        add(modCheckCheckBox).row()

        add(modCheckResultTable)
    }

    private fun runModChecker(complex: Boolean = false) {
        
        modCheckFirstRun = false
        if (modCheckCheckBox == null) return

        modCheckResultTable.clear()

        val rulesetErrors = RulesetCache.loadRulesets()
        if (rulesetErrors.isNotEmpty()) {
            val errorTable = Table().apply { defaults().pad(2f) }
            for (rulesetError in rulesetErrors)
                errorTable.add(rulesetError.toLabel()).width(stage.width / 2).row()
            modCheckResultTable.add(errorTable)
        }

        modCheckResultTable.add("Checking mods for errors...".toLabel()).row()
        modCheckCheckBox!!.disable()

        crashHandlingThread(name="ModChecker") {
            for (mod in RulesetCache.values.sortedBy { it.name }) {
                val modLinks =
                        if (!complex) mod.checkModLinks(forOptionsPopup = true)
                        else RulesetCache.checkCombinedModLinks(linkedSetOf(mod.name))
                modLinks.sortByDescending { it.errorSeverityToReport }
                val noProblem = !modLinks.isNotOK()
                if (modLinks.isNotEmpty()) modLinks += RulesetError("", RulesetErrorSeverity.OK)
                if (noProblem) modLinks += RulesetError("No problems found.".tr(), RulesetErrorSeverity.OK)

                postCrashHandlingRunnable {
                    // When the options popup is already closed before this postRunnable is run,
                    // Don't add the labels, as otherwise the game will crash
                    if (stage == null) return@postCrashHandlingRunnable
                    // Don't just render text, since that will make all the conditionals in the mod replacement messages move to the end, which makes it unreadable
                    // Don't use .toLabel() either, since that activates translations as well, which is what we're trying to avoid,
                    // Instead, some manual work needs to be put in.

                    val expanderTab = ExpanderTab(mod.name, startsOutOpened = false){
                        it.defaults().align(Align.left)
                        if (!noProblem && mod.folderLocation != null) {
                            val replaceableUniques = getDeprecatedReplaceableUniques(mod)
                            if (replaceableUniques.isNotEmpty())
                                it.add("Autoupdate mod uniques".toTextButton()
                                    .onClick { autoUpdateUniques(mod, replaceableUniques) }).pad(10f).row()
                        }
                        for (line in modLinks) {
                            val label = Label(line.text, BaseScreen.skin)
                                .apply { color = line.errorSeverityToReport.color }
                            label.wrap = true
                            it.add(label).width(stage.width / 2).row()
                        }
                        if (!noProblem)
                            it.add("Copy to clipboard".toTextButton().onClick {
                                Gdx.app.clipboard.contents = modLinks
                                    .joinToString("\n") { line -> line.text }
                            }).row()
                    }

                    val loadingLabel = modCheckResultTable.children.last()
                    modCheckResultTable.removeActor(loadingLabel)
                    modCheckResultTable.add(expanderTab).row()
                    modCheckResultTable.add(loadingLabel).row()
                }
            }

            // done with all mods!
            postCrashHandlingRunnable {
                modCheckResultTable.removeActor(modCheckResultTable.children.last())
                modCheckCheckBox!!.enable()
            }
        }
    }

    private fun getDeprecatedReplaceableUniques(mod:Ruleset): HashMap<String, String> {

        val objectsToCheck = sequenceOf(
            mod.units,
            mod.tileImprovements,
            mod.unitPromotions,
            mod.buildings,
            mod.policies,
            mod.nations,
            mod.beliefs,
            mod.technologies,
        )
        val allDeprecatedUniques = HashSet<String>()
        val deprecatedUniquesToReplacementText = HashMap<String, String>()

        val deprecatedUniques = objectsToCheck
            .flatMap { it.values }
            .flatMap { it.uniqueObjects }
            .filter { it.getDeprecationAnnotation() != null }


        for (deprecatedUnique in deprecatedUniques) {
            if (allDeprecatedUniques.contains(deprecatedUnique.text)) continue
            allDeprecatedUniques.add(deprecatedUnique.text)

            // note that this replacement does not contain conditionals attached to the original!


            var uniqueReplacementText = deprecatedUnique.getReplacementText(mod)
            while (Unique(uniqueReplacementText).getDeprecationAnnotation() != null)
                uniqueReplacementText = Unique(uniqueReplacementText).getReplacementText(mod)

            for (conditional in deprecatedUnique.conditionals)
                uniqueReplacementText += " <${conditional.text}>"
            val replacementUnique = Unique(uniqueReplacementText)

            val modInvariantErrors = mod.checkUnique(
                replacementUnique,
                false,
                "",
                UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant,
                deprecatedUnique.sourceObjectType!!
            )
            for (error in modInvariantErrors)
                println(error.text + " - " + error.errorSeverityToReport)
            if (modInvariantErrors.isNotEmpty()) continue // errors means no autoreplace

            if (mod.modOptions.isBaseRuleset) {
                val modSpecificErrors = mod.checkUnique(
                    replacementUnique,
                    false,
                    "",
                    UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant,
                    deprecatedUnique.sourceObjectType
                )
                for (error in modSpecificErrors)
                    println(error.text + " - " + error.errorSeverityToReport)
                if (modSpecificErrors.isNotEmpty()) continue
            }

            deprecatedUniquesToReplacementText[deprecatedUnique.text] = uniqueReplacementText
            println("Replace \"${deprecatedUnique.text}\" with \"$uniqueReplacementText\"")
        }
        return deprecatedUniquesToReplacementText
    }

    private fun autoUpdateUniques(mod: Ruleset, replaceableUniques: HashMap<String, String>, ) {

        val filesToReplace = listOf(
            "Units.json",
            "TileImprovements.json",
            "UnitPromotions.json",
            "Buildings.json",
            "Policies.json",
            "Nations.json",
            "Beliefs.json",
            "Techs.json",
        )

        val jsonFolder = mod.folderLocation!!.child("jsons")
        for (fileName in filesToReplace) {
            val file = jsonFolder.child(fileName)
            if (!file.exists() || file.isDirectory) continue
            var newFileText = file.readString()
            for ((original, replacement) in replaceableUniques) {
                newFileText = newFileText.replace("\"$original\"", "\"$replacement\"")
            }
            file.writeString(newFileText, false)
        }
        val toastText = "Uniques updated!"
        ToastPopup(toastText, screen).open(true)
        runModChecker()
    }

    private fun getDebugTab() = Table(BaseScreen.skin).apply {
        pad(10f)
        defaults().pad(5f)

        val game = UncivGame.Current
        add("Supercharged".toCheckBox(game.superchargedForDebug) {
            game.superchargedForDebug = it
        }).row()
        add("View entire map".toCheckBox(game.viewEntireMapForDebug) {
            game.viewEntireMapForDebug = it
        }).row()
        if (game.isGameInfoInitialized()) {
            add("God mode (current game)".toCheckBox(game.gameInfo.gameParameters.godMode) {
                game.gameInfo.gameParameters.godMode = it
            }).row()
        }
        add("Save maps compressed".toCheckBox(MapSaver.saveZipped) {
            MapSaver.saveZipped = it
        }).row()
        add("Gdx Scene2D debug".toCheckBox(BaseScreen.enableSceneDebug) {
            BaseScreen.enableSceneDebug = it
        }).row()
        val unlockTechsButton = "Unlock all techs".toTextButton()
        unlockTechsButton.onClick {
            if (!game.isGameInfoInitialized())
                return@onClick
            for (tech in game.gameInfo.ruleSet.technologies.keys) {
                if (tech !in game.gameInfo.getCurrentPlayerCivilization().tech.techsResearched) {
                    game.gameInfo.getCurrentPlayerCivilization().tech.addTechnology(tech)
                    game.gameInfo.getCurrentPlayerCivilization().popupAlerts.removeLastOrNull()
                }
            }
            game.gameInfo.getCurrentPlayerCivilization().updateSightAndResources()
            game.worldScreen.shouldUpdate = true
        }
        add(unlockTechsButton).row()
        val giveResourcesButton = "Give all strategic resources".toTextButton()
        giveResourcesButton.onClick {
            if (!game.isGameInfoInitialized())
                return@onClick
            val ownedTiles = game.gameInfo.tileMap.values.asSequence().filter { it.getOwner() == game.gameInfo.getCurrentPlayerCivilization() }
            val resourceTypes = game.gameInfo.ruleSet.tileResources.values.asSequence().filter { it.resourceType == ResourceType.Strategic }
            for ((tile, resource) in ownedTiles zip resourceTypes) {
                tile.resource = resource.name
                tile.resourceAmount = 999
                tile.improvement = resource.improvement
            }
            game.gameInfo.getCurrentPlayerCivilization().updateSightAndResources()
            game.worldScreen.shouldUpdate = true
        }
        add(giveResourcesButton).row()
    }

    //endregion
    //region Row builders

    private fun Table.addMinimapSizeSlider() {
        add("Show minimap".toLabel()).left().fillX()

        // The meaning of the values needs a formula to be synchronized between here and
        // [Minimap.init]. It goes off-10%-11%..29%-30%-35%-40%-45%-50% - and the percentages
        // correspond roughly to the minimap's proportion relative to screen dimensions.
        val offTranslated = "off".tr()  // translate only once and cache in closure
        val getTipText: (Float)->String = {
            when (it) {
                0f -> offTranslated
                in 0.99f..21.01f -> "%.0f".format(it+9) + "%"
                else -> "%.0f".format(it * 5 - 75) + "%"
            }
        }
        val minimapSlider = UncivSlider(0f, 25f, 1f,
            initial = if (settings.showMinimap) settings.minimapSize.toFloat() else 0f,
            getTipText = getTipText
        ) {
            val size = it.toInt()
            if (size == 0) settings.showMinimap = false
            else {
                settings.showMinimap = true
                settings.minimapSize = size
            }
            settings.save()
            if (previousScreen is WorldScreen)
                previousScreen.shouldUpdate = true
        }
        add(minimapSlider).pad(10f).row()
    }

    private fun Table.addResolutionSelectBox() {
        add("Resolution".toLabel()).left().fillX()

        val resolutionSelectBox = SelectBox<String>(skin)
        resolutionSelectBox.items = resolutionArray
        resolutionSelectBox.selected = settings.resolution
        add(resolutionSelectBox).minWidth(selectBoxMinWidth).pad(10f).row()

        resolutionSelectBox.onChange {
            settings.resolution = resolutionSelectBox.selected
            reloadWorldAndOptions()
        }
    }

    private fun Table.addTileSetSelectBox() {
        add("Tileset".toLabel()).left().fillX()

        val tileSetSelectBox = SelectBox<String>(skin)
        val tileSetArray = GdxArray<String>()
        val tileSets = ImageGetter.getAvailableTilesets()
        for (tileset in tileSets) tileSetArray.add(tileset)
        tileSetSelectBox.items = tileSetArray
        tileSetSelectBox.selected = settings.tileSet
        add(tileSetSelectBox).minWidth(selectBoxMinWidth).pad(10f).row()

        tileSetSelectBox.onChange {
            settings.tileSet = tileSetSelectBox.selected
            // ImageGetter ruleset should be correct no matter what screen we're on
            TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
            reloadWorldAndOptions()
        }
    }

    private fun Table.addSoundEffectsVolumeSlider() {
        add("Sound effects volume".tr()).left().fillX()

        val soundEffectsVolumeSlider = UncivSlider(0f, 1.0f, 0.05f,
            initial = settings.soundEffectsVolume,
            getTipText = UncivSlider::formatPercent
        ) {
            settings.soundEffectsVolume = it
            settings.save()
        }
        add(soundEffectsVolumeSlider).pad(5f).row()
    }

    private fun Table.addMusicVolumeSlider() {
        add("Music volume".tr()).left().fillX()

        val musicVolumeSlider = UncivSlider(0f, 1.0f, 0.05f,
            initial = settings.musicVolume,
            sound = UncivSound.Silent,
            getTipText = UncivSlider::formatPercent
        ) {
            settings.musicVolume = it
            settings.save()

            val music = previousScreen.game.musicController
            music.setVolume(it)
            if (!music.isPlaying())
                music.chooseTrack(flags = MusicTrackChooserFlags.setPlayDefault)
        }
        add(musicVolumeSlider).pad(5f).row()
    }

    private fun Table.addMusicPauseSlider() {
        val music = previousScreen.game.musicController

        // map to/from 0-1-2..10-12-14..30-35-40..60-75-90-105-120
        fun posToLength(pos: Float): Float = when (pos) {
            in 0f..10f -> pos
            in 11f..20f -> pos * 2f - 10f
            in 21f..26f -> pos * 5f - 70f
            else -> pos * 15f - 330f
        }
        fun lengthToPos(length: Float): Float = floor(when (length) {
            in 0f..10f -> length
            in 11f..30f -> (length + 10f) / 2f
            in 31f..60f -> (length + 10f) / 5f
            else -> (length + 330f) / 15f
        })
        val getTipText: (Float)->String = {
            "%.0f".format(posToLength(it))
        }

        add("Pause between tracks".tr()).left().fillX()

        val pauseLengthSlider = UncivSlider(0f, 30f, 1f,
            initial = lengthToPos(music.silenceLength),
            sound = UncivSound.Silent,
            getTipText = getTipText
        ) {
            music.silenceLength = posToLength(it)
            settings.pauseBetweenTracks = music.silenceLength.toInt()
        }
        add(pauseLengthSlider).pad(5f).row()
    }

    private fun Table.addMusicCurrentlyPlaying() {
        val label = WrappableLabel("", this.width - 10f, Color(-0x2f5001), 16)
        label.wrap = true
        add(label).padTop(20f).colspan(2).fillX().row()
        previousScreen.game.musicController.onChange {
            postCrashHandlingRunnable {
                label.setText("Currently playing: [$it]".tr())
            }
        }
        label.onClick { previousScreen.game.musicController.chooseTrack(flags = MusicTrackChooserFlags.setNextTurn) }
    }

    private fun Table.addDownloadMusic() {
        val downloadMusicButton = "Download music".toTextButton()
        add(downloadMusicButton).colspan(2).row()
        val errorTable = Table()
        add(errorTable).colspan(2).row()

        downloadMusicButton.onClick {
            downloadMusicButton.disable()
            errorTable.clear()
            errorTable.add("Downloading...".toLabel())

            // So the whole game doesn't get stuck while downloading the file
            crashHandlingThread(name = "Music") {
                try {
                    previousScreen.game.musicController.downloadDefaultFile()
                    postCrashHandlingRunnable {
                        tabs.replacePage("Sound", getSoundTab())
                        previousScreen.game.musicController.chooseTrack(flags = MusicTrackChooserFlags.setPlayDefault)
                    }
                } catch (ex: Exception) {
                    postCrashHandlingRunnable {
                        errorTable.clear()
                        errorTable.add("Could not download music!".toLabel(Color.RED))
                    }
                }
            }
        }
    }

    private fun Table.addMultiplayerTurnCheckerDelayBox() {
        add("Time between turn checks out-of-game (in minutes)".toLabel()).left().fillX()

        val checkDelaySelectBox = SelectBox<Int>(skin)
        val possibleDelaysArray = GdxArray<Int>()
        possibleDelaysArray.addAll(1, 2, 5, 15)
        checkDelaySelectBox.items = possibleDelaysArray
        checkDelaySelectBox.selected = settings.multiplayerTurnCheckerDelayInMinutes

        add(checkDelaySelectBox).pad(10f).row()

        checkDelaySelectBox.onChange {
            settings.multiplayerTurnCheckerDelayInMinutes = checkDelaySelectBox.selected
            settings.save()
        }
    }

    private fun Table.addSetUserId() {
        val idSetLabel = "".toLabel()
        val takeUserIdFromClipboardButton = "Take user ID from clipboard".toTextButton()
            .onClick {
                try {
                    val clipboardContents = Gdx.app.clipboard.contents.trim()
                    UUID.fromString(clipboardContents)
                    YesNoPopup("Doing this will reset your current user ID to the clipboard contents - are you sure?",
                        {
                            settings.userId = clipboardContents
                            settings.save()
                            idSetLabel.setFontColor(Color.WHITE).setText("ID successfully set!".tr())
                        }, previousScreen).open(true)
                    idSetLabel.isVisible = true
                } catch (ex: Exception) {
                    idSetLabel.isVisible = true
                    idSetLabel.setFontColor(Color.RED).setText("Invalid ID!".tr())
                }
            }
        add(takeUserIdFromClipboardButton).pad(5f).colspan(2).row()
        add(idSetLabel).colspan(2).row()
    }

    private fun Table.addAutosaveTurnsSelectBox() {
        add("Turns between autosaves".toLabel()).left().fillX()

        val autosaveTurnsSelectBox = SelectBox<Int>(skin)
        val autosaveTurnsArray = GdxArray<Int>()
        autosaveTurnsArray.addAll(1, 2, 5, 10)
        autosaveTurnsSelectBox.items = autosaveTurnsArray
        autosaveTurnsSelectBox.selected = settings.turnsBetweenAutosaves

        add(autosaveTurnsSelectBox).pad(10f).row()

        autosaveTurnsSelectBox.onChange {
            settings.turnsBetweenAutosaves = autosaveTurnsSelectBox.selected
            settings.save()
        }
    }

    private fun Table.addTranslationGeneration() {
        if (Gdx.app.type == Application.ApplicationType.Desktop) {
            val generateTranslationsButton = "Generate translation files".toTextButton()
            val generateAction = {
                val result = TranslationFileWriter.writeNewTranslationFiles()
                // notify about completion
                generateTranslationsButton.setText(result.tr())
                generateTranslationsButton.disable()
            }
            generateTranslationsButton.onClick(generateAction)
            keyPressDispatcher[Input.Keys.F12] = generateAction
            generateTranslationsButton.addTooltip("F12",18f)
            add(generateTranslationsButton).colspan(2).row()
        }
    }


    private fun Table.addYesNoRow(text: String, initialValue: Boolean, updateWorld: Boolean = false, action: ((Boolean) -> Unit)) {
        val wrapWidth = tabs.prefWidth - 60f
        add(WrappableLabel(text, wrapWidth).apply { wrap = true })
            .left().fillX()
            .maxWidth(wrapWidth)
        val button = YesNoButton(initialValue, BaseScreen.skin) {
            action(it)
            settings.save()
            if (updateWorld && previousScreen is WorldScreen)
                previousScreen.shouldUpdate = true
        }
        add(button).row()
    }

    //endregion

    /**
     *  This TextButton subclass helps to keep looks and behaviour of our Yes/No
     *  in one place, but it also helps keeping context for those action lambdas.
     *
     *  Usage: YesNoButton(someSetting: Boolean, skin) { someSetting = it; sideEffects() }
     */
    private class YesNoButton(
        initialValue: Boolean,
        skin: Skin,
        action: (Boolean) -> Unit
    ) : TextButton (initialValue.toYesNo(), skin ) {

        var value = initialValue
            private set(value) {
                field = value
                setText(value.toYesNo())
            }

        init {
            color = ImageGetter.getBlue()
            onClick {
                value = !value
                action.invoke(value)
            }
        }

        companion object {
            fun Boolean.toYesNo(): String = (if (this) Constants.yes else Constants.no).tr()
        }
    }
}
