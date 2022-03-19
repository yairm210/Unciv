package com.unciv.models.metadata

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameSaver
import java.text.Collator
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
    var userId = ""
    var multiplayerTurnCheckerEnabled = true
    var multiplayerTurnCheckerPersistentNotificationEnabled = true
    var multiplayerTurnCheckerDelayInMinutes = 5
    var orderTradeOffersByAmount = true
    var windowState = WindowState()
    var isFreshlyCreated = false
    var visualMods = HashSet<String>()
    
    
    var multiplayerServer = Constants.dropboxMultiplayerServer
    

    var showExperimentalWorldWrap = false // We're keeping this as a config due to ANR problems on Android phones for people who don't know what they're doing :/

    var lastOverviewPage: String = "Cities"

    var allowAndroidPortrait = false    // Opt-in to allow Unciv to follow a screen rotation to portrait

    /** Saves the last successful new game's setup */
    var lastGameSetup: GameSetupInfo? = null

    init {
        // 26 = Android Oreo. Versions below may display permanent icon in notification bar.
        if (Gdx.app?.type == Application.ApplicationType.Android && Gdx.app.version < 26) {
            multiplayerTurnCheckerPersistentNotificationEnabled = false
        }
    }

    fun save() {
        if (!isFreshlyCreated && Gdx.app?.type == Application.ApplicationType.Desktop) {
            windowState = WindowState(Gdx.graphics.width, Gdx.graphics.height)
        }
        GameSaver.setGeneralSettings(this)
    }

    fun addCompletedTutorialTask(tutorialTask: String) {
        if (tutorialTasksCompleted.add(tutorialTask))
            save()
    }

    fun updateLocaleFromLanguage() {
        val bannedCharacters = listOf(' ', '_', '-', '(', ')') // Things not to have in enum names
        val languageName = language.filterNot { it in bannedCharacters }
        try {
            val code = LocaleCode.valueOf(languageName)
            locale = Locale(code.language, code.country)
        } catch (e: Exception) {
            locale = Locale.getDefault()
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
