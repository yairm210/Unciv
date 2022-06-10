package com.unciv.models.metadata

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.FriendList
import com.unciv.ui.utils.Fonts
import java.text.Collator
import java.time.Duration
import java.util.*
import kotlin.collections.HashSet

data class WindowState (val width: Int = 900, val height: Int = 600)

class GameSettings {
    var showWorkedTiles: Boolean = false
    var showResourcesAndImprovements: Boolean = true
    var showTileYields: Boolean = false
    var showUnitMovements: Boolean = false

    var checkForDueUnits: Boolean = true
    var singleTapMove: Boolean = false
    var language: String = "English"
    @Transient
    var locale: Locale? = null
    var resolution: String = "900x600" // Auto-detecting resolution was a BAD IDEA since it needs to be based on DPI AND resolution.
    var tutorialsShown = HashSet<String>()
    var tutorialTasksCompleted = HashSet<String>()
    var hasCrashedRecently = false

    var soundEffectsVolume = 0.5f
    var musicVolume = 0.5f
    var pauseBetweenTracks = 10

    var turnsBetweenAutosaves = 1
    var tileSet: String = "FantasyHex"
    var showTutorials: Boolean = true
    var autoAssignCityProduction: Boolean = true
    var autoBuildingRoads: Boolean = true
    var automatedWorkersReplaceImprovements = true

    var showMinimap: Boolean = true
    var minimapSize: Int = 6    // default corresponds to 15% screen space
    var showPixelUnits: Boolean = true
    var showPixelImprovements: Boolean = true
    var continuousRendering = false
    var orderTradeOffersByAmount = true
    var confirmNextTurn = false
    var windowState = WindowState()
    var isFreshlyCreated = false
    var visualMods = HashSet<String>()
    var useDemographics: Boolean = false
    var showZoomButtons: Boolean = false

    var notificationsLogMaxTurns = 5

    var multiplayer = GameSettingsMultiplayer()

    var showExperimentalWorldWrap = false // We're keeping this as a config due to ANR problems on Android phones for people who don't know what they're doing :/

    var lastOverviewPage: String = "Cities"

    var allowAndroidPortrait = false    // Opt-in to allow Unciv to follow a screen rotation to portrait

    /** Saves the last successful new game's setup */
    var lastGameSetup: GameSetupInfo? = null

    var fontFamily: String = Fonts.DEFAULT_FONT_FAMILY

    /** Maximum zoom-out of the map - performance heavy */
    var maxWorldZoomOut = 2f

    /** used to migrate from older versions of the settings */
    var version: Int? = null

    init {
        // 26 = Android Oreo. Versions below may display permanent icon in notification bar.
        if (Gdx.app?.type == Application.ApplicationType.Android && Gdx.app.version < 26) {
            multiplayer.turnCheckerPersistentNotificationEnabled = false
        }
    }

    fun save() {
        if (!isFreshlyCreated && Gdx.app?.type == Application.ApplicationType.Desktop) {
            windowState = WindowState(Gdx.graphics.width, Gdx.graphics.height)
        }
        UncivGame.Current.gameSaver.setGeneralSettings(this)
    }

    fun addCompletedTutorialTask(tutorialTask: String) {
        if (tutorialTasksCompleted.add(tutorialTask))
            save()
    }

    fun updateLocaleFromLanguage() {
        val bannedCharacters = listOf(' ', '_', '-', '(', ')') // Things not to have in enum names
        val languageName = language.filterNot { it in bannedCharacters }
        locale = try {
            val code = LocaleCode.valueOf(languageName)
            Locale(code.language, code.country)
        } catch (e: Exception) {
            Locale.getDefault()
        }
    }

    fun getCurrentLocale(): Locale {
        if (locale == null)
            updateLocaleFromLanguage()
        return locale!!
    }

    fun getCollatorFromLocale(): Collator {
        return Collator.getInstance(getCurrentLocale())
    }
}

enum class LocaleCode(var language: String, var country: String) {
    Arabic("ar", "IQ"),
    BrazilianPortuguese("pt", "BR"),
    Bulgarian("bg", "BG"),
    Catalan("ca", "ES"),
    Croatian("hr", "HR"),
    Czech("cs", "CZ"),
    Danish("da", "DK"),
    Dutch("nl", "NL"),
    English("en", "US"),
    Estonian("et", "EE"),
    Finnish("fi", "FI"),
    French("fr", "FR"),
    German("de", "DE"),
    Greek("el", "GR"),
    Hindi("hi", "IN"),
    Hungarian("hu", "HU"),
    Indonesian("in", "ID"),
    Italian("it", "IT"),
    Japanese("ja", "JP"),
    Korean("ko", "KR"),
    Latvian("lv", "LV"),
    Lithuanian("lt", "LT"),
    Malay("ms", "MY"),
    Norwegian("no", "NO"),
    NorwegianNynorsk("nn", "NO"),
    PersianPinglishDIN("fa", "IR"), // These might just fall back to default
    PersianPinglishUN("fa", "IR"),
    Polish("pl", "PL"),
    Portuguese("pt", "PT"),
    Romanian("ro", "RO"),
    Russian("ru", "RU"),
    Serbian("sr", "RS"),
    SimplifiedChinese("zh", "CN"),
    Slovak("sk", "SK"),
    Spanish("es", "ES"),
    Swedish("sv", "SE"),
    Thai("th", "TH"),
    TraditionalChinese("zh", "TW"),
    Turkish("tr", "TR"),
    Ukrainian("uk", "UA"),
    Vietnamese("vi", "VN"),
}

class GameSettingsMultiplayer {
    var userId = ""
    var server = Constants.dropboxMultiplayerServer
    var friendList: MutableList<FriendList.Friend> = mutableListOf()
    var turnCheckerEnabled = true
    var turnCheckerPersistentNotificationEnabled = true
    var turnCheckerDelay = Duration.ofMinutes(5)
    var statusButtonInSinglePlayer = false
    var currentGameRefreshDelay = Duration.ofSeconds(10)
    var allGameRefreshDelay = Duration.ofMinutes(5)
    var hideDropboxWarning = false
}
