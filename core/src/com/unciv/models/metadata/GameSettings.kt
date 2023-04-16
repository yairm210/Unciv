package com.unciv.models.metadata

import com.badlogic.gdx.Application.ApplicationType
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Base64Coder
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.FriendList
import com.unciv.models.UncivSound
import com.unciv.ui.components.FontFamilyData
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.KeyboardBindings
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.utils.Display
import com.unciv.utils.ScreenOrientation
import java.text.Collator
import java.time.Duration
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty0

data class WindowState (val width: Int = 900, val height: Int = 600)

enum class ScreenSize(
    @Suppress("unused")  // Actual width determined by screen aspect ratio, this as comment only
    val virtualWidth: Float,
    val virtualHeight: Float
) {
    Tiny(750f,500f),
    Small(900f,600f),
    Medium(1050f,700f),
    Large(1200f,800f),
    Huge(1500f,1000f)
}

class GameSettings {

    /** Allows panning the map by moving the pointer to the screen edges */
    var mapAutoScroll: Boolean = false
    /** How fast the map pans using keyboard or with [mapAutoScroll] and mouse */
    var mapPanningSpeed: Float = 6f

    var showWorkedTiles: Boolean = false
    var showResourcesAndImprovements: Boolean = true
    var showTileYields: Boolean = false
    var showUnitMovements: Boolean = false
    var showSettlersSuggestedCityLocations: Boolean = true

    var checkForDueUnits: Boolean = true
    var autoUnitCycle: Boolean = true
    var singleTapMove: Boolean = false
    var language: String = Constants.english
    @Transient
    var locale: Locale? = null
    var screenSize:ScreenSize = ScreenSize.Small
    var screenMode: Int = 0
    var tutorialsShown = HashSet<String>()
    var tutorialTasksCompleted = HashSet<String>()

    var soundEffectsVolume = 0.5f
    var citySoundsVolume = 0.5f
    var musicVolume = 0.5f
    var pauseBetweenTracks = 10

    var turnsBetweenAutosaves = 1
    var tileSet: String = Constants.defaultTileset
    var unitSet: String? = Constants.defaultUnitset
    var skin: String = Constants.defaultSkin
    var showTutorials: Boolean = true
    var autoAssignCityProduction: Boolean = true
    var autoBuildingRoads: Boolean = true
    var automatedWorkersReplaceImprovements = true
    var automatedUnitsMoveOnTurnStart: Boolean = false

    var showMinimap: Boolean = true
    var minimapSize: Int = 6    // default corresponds to 15% screen space
    var unitIconOpacity = 1f // default corresponds to fully opaque
    val showPixelUnits: Boolean get() = unitSet != null
    var showPixelImprovements: Boolean = true
    var continuousRendering = false
    var experimentalRendering = false
    var orderTradeOffersByAmount = true
    var confirmNextTurn = false
    var windowState = WindowState()
    var isFreshlyCreated = false
    var visualMods = HashSet<String>()
    var useDemographics: Boolean = false
    var showZoomButtons: Boolean = false

    var notificationsLogMaxTurns = 5

    var showAutosaves: Boolean = false

    var androidCutout: Boolean = false

    var multiplayer = GameSettingsMultiplayer()

    var enableEspionageOption = false

    // This is a string not an enum so if tabs change it won't screw up the json serialization
    var lastOverviewPage = EmpireOverviewCategories.Cities.name

    /** Orientation for mobile platforms */
    var displayOrientation = ScreenOrientation.Landscape

    /** Saves the last successful new game's setup */
    var lastGameSetup: GameSetupInfo? = null

    var fontFamilyData: FontFamilyData = FontFamilyData.default
    var fontSizeMultiplier: Float = 1f

    var enableEasterEggs: Boolean = true

    /** Maximum zoom-out of the map - performance heavy */
    var maxWorldZoomOut = 2f

    var keyBindings = KeyboardBindings()

    /** NotificationScroll on Word Screen visibility control - mapped to NotificationsScroll.UserSetting enum */
    var notificationScroll: String = ""

    /** If on, selected notifications are drawn enlarged with wider padding */
    var enlargeSelectedNotification = true

    /** used to migrate from older versions of the settings */
    var version: Int? = null

    init {
        // 26 = Android Oreo. Versions below may display permanent icon in notification bar.
        if (Gdx.app?.type == ApplicationType.Android && Gdx.app.version < 26) {
            multiplayer.turnCheckerPersistentNotificationEnabled = false
        }
    }

    fun save() {
        refreshWindowSize()
        UncivGame.Current.files.setGeneralSettings(this)
    }
    fun refreshWindowSize() {
        if (isFreshlyCreated || Gdx.app.type != ApplicationType.Desktop) return
        if (!Display.hasUserSelectableSize(screenMode)) return
        windowState = WindowState(Gdx.graphics.width, Gdx.graphics.height)
    }

    fun addCompletedTutorialTask(tutorialTask: String): Boolean {
        if (!tutorialTasksCompleted.add(tutorialTask)) return false
        UncivGame.Current.isTutorialTaskCollapsed = false
        save()
        return true
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

    fun getFontSize(): Int {
        return (Fonts.ORIGINAL_FONT_SIZE * fontSizeMultiplier).toInt()
    }

    private fun getCurrentLocale(): Locale {
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
    Belarusian("be", "BY"),
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
    Afrikaans("af", "ZA")
}

class GameSettingsMultiplayer {
    var userId = ""
    var passwords = mutableMapOf<String, String>()
    @Suppress("unused")  // @GGuenni knows what he intended with this field
    var userName: String = ""
    var server = Constants.uncivXyzServer
    var friendList: MutableList<FriendList.Friend> = mutableListOf()
    var turnCheckerEnabled = true
    var turnCheckerPersistentNotificationEnabled = true
    var turnCheckerDelay: Duration = Duration.ofMinutes(5)
    var statusButtonInSinglePlayer = false
    var currentGameRefreshDelay: Duration = Duration.ofSeconds(10)
    var allGameRefreshDelay: Duration = Duration.ofMinutes(5)
    var currentGameTurnNotificationSound: UncivSound = UncivSound.Silent
    var otherGameTurnNotificationSound: UncivSound = UncivSound.Silent
    var hideDropboxWarning = false

    fun getAuthHeader(): String {
        val serverPassword = passwords[server] ?: ""
        val preEncodedAuthValue = "$userId:$serverPassword"
        return "Basic ${Base64Coder.encodeString(preEncodedAuthValue)}"
    }
}

@Suppress("SuspiciousCallableReferenceInLambda")  // By @Azzurite, safe as long as that warning below is followed
enum class GameSetting(
    val kClass: KClass<*>,
    private val propertyGetter: (GameSettings) -> KMutableProperty0<*>
) {
//     Uncomment these once they are refactored to send events on change
//     MULTIPLAYER_USER_ID(String::class, { it.multiplayer::userId }),
//     MULTIPLAYER_SERVER(String::class, { it.multiplayer::server }),
//     MULTIPLAYER_STATUSBUTTON_IN_SINGLEPLAYER(Boolean::class, { it.multiplayer::statusButtonInSinglePlayer }),
//     MULTIPLAYER_TURN_CHECKER_ENABLED(Boolean::class, { it.multiplayer::turnCheckerEnabled }),
//     MULTIPLAYER_TURN_CHECKER_PERSISTENT_NOTIFICATION_ENABLED(Boolean::class, { it.multiplayer::turnCheckerPersistentNotificationEnabled }),
//     MULTIPLAYER_HIDE_DROPBOX_WARNING(Boolean::class, { it.multiplayer::hideDropboxWarning }),
    MULTIPLAYER_TURN_CHECKER_DELAY(Duration::class, { it.multiplayer::turnCheckerDelay }),
    MULTIPLAYER_CURRENT_GAME_REFRESH_DELAY(Duration::class, { it.multiplayer::currentGameRefreshDelay }),
    MULTIPLAYER_ALL_GAME_REFRESH_DELAY(Duration::class, { it.multiplayer::allGameRefreshDelay }),
    MULTIPLAYER_CURRENT_GAME_TURN_NOTIFICATION_SOUND(UncivSound::class, { it.multiplayer::currentGameTurnNotificationSound }),
    MULTIPLAYER_OTHER_GAME_TURN_NOTIFICATION_SOUND(UncivSound::class, { it.multiplayer::otherGameTurnNotificationSound });

    /** **Warning:** It is the obligation of the caller to select the same type [T] that the [kClass] of this property has */
    fun <T> getProperty(settings: GameSettings): KMutableProperty0<T> {
        @Suppress("UNCHECKED_CAST")
        return propertyGetter(settings) as KMutableProperty0<T>
    }
}
