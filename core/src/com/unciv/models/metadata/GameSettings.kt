package com.unciv.models.metadata

import com.badlogic.gdx.Application.ApplicationType
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Base64Coder
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.FriendList
import com.unciv.logic.multiplayer.chat.ChatWebSocket
import com.unciv.models.UncivSound
import com.unciv.models.metadata.GameSettings.WindowState.Companion.minimumHeight
import com.unciv.models.metadata.GameSettings.WindowState.Companion.minimumWidth
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyboardBindings
import com.unciv.ui.screens.worldscreen.NotificationsScroll
import com.unciv.utils.Display
import com.unciv.utils.ScreenOrientation
import java.awt.Rectangle
import yairm210.purity.annotations.Readonly
import java.text.Collator
import java.text.NumberFormat
import java.time.Duration
import java.util.Locale

/** Settings that apply across all games, stored in GameSettings.json */
class GameSettings {
    // This should group and order fields into regions by their Tab in OptionsPopup
    //// 4-slash comments correspond to "section" headers within an Options Tab

    //region 1: Display

    //// Screen
    var screenSize: ScreenSize = ScreenSize.Small
    /** Orientation for mobile platforms */
    var displayOrientation = ScreenOrientation.Landscape
    var screenMode = 0
    /** Allows panning the map by moving the pointer to the screen edges */
    var mapAutoScroll = false
    /** How fast the map pans using keyboard or with [mapAutoScroll] and mouse */
    var mapPanningSpeed = 6f

    //// Graphics
    var tileSet: String = Constants.defaultTileset
    var unitSet: String? = Constants.defaultUnitset
    var skin: String = Constants.defaultSkin

    //// UI
    /** NotificationScroll on Word Screen visibility control - mapped to [NotificationsScroll.UserSetting] enum */
    // Defaulting this to "" - and implement the fallback only in NotificationsScroll leads to Options popup and actual effect being in disagreement!
    var notificationScroll: String = NotificationsScroll.UserSetting.default().name
    var showMinimap = true
    var showTutorials = true
    // There have no UI other than the "Reset tutorials" button:
    var tutorialsShown = HashSet<String>()
    var tutorialTasksCompleted = HashSet<String>()

    enum class LongPressIndicatorSetting {
        Default, Off, On, Debug; // Debug only offered on Debug page of Options
        /** Android defaults to off - Desktop defaults to on.
         * Manually setting it once turns it into "On" or "Off" properly */
        fun toBoolean() = this == On || Gdx.app.type == ApplicationType.Desktop && this == Default
        companion object {
            fun of(bool: Boolean) = if (bool) On else Off
        }
    }
    var showLongPressIndicators = LongPressIndicatorSetting.Default

    var showZoomButtons = false
    var forbidPopupClickBehindToClose = false
    var useCirclesToIndicateMovableTiles = false
    /** Size of automatic display of UnitSet art in Civilopedia - 0 to disable */
    var pediaUnitArtSize = 0f

    //// Visual Hints
    var showUnitMovements = false
    var showSettlersSuggestedCityLocations = true
    var showTileYields = false
    var showWorkedTiles = false
    var showResourcesAndImprovements = true
    var showPixelImprovements = true
    var unitIconOpacity = 1f // default corresponds to fully opaque

    //// Performance
    var continuousRendering = false

    //// Experimental
    var unitMovementButtonAnimation = false
    var unitActionsTableAnimation = false

    //endregion

    //region 2: Gameplay
    var checkForDueUnits = true
    var checkForDueUnitsCycles = false
    var smallUnitButton = true
    var autoUnitCycle = true
    /** Cycle units by distance instead of queue */
    var alternateUnitCycleOrder = false
    var singleTapMove = false
    var longTapMove = true
    var orderTradeOffersByAmount = true
    var confirmNextTurn = false
    var notificationsLogMaxTurns = 5
    //endregion

    //region 3: Automation

    //// Automation
    var autoAssignCityProduction = false
    var autoBuildingRoads = true
    var automatedWorkersReplaceImprovements = true
    var stopAutomatedWorkersRemoveVegetation = false
    var automatedUnitsMoveOnTurnStart = false
    var automatedUnitsCanUpgrade = false
    var automatedUnitsChoosePromotions = false
    var citiesAutoBombardAtEndOfTurn = false

    //// Autoplay
    var autoPlay = GameSettingsAutoPlay()

    //endregion

    //region 4: Language
    var language: String = Constants.english
    @Transient
    var locale: Locale? = null
    //endregion

    //region 5: Sound
    var soundEffectsVolume = 0.5f
    var citySoundsVolume = 0.5f
    var musicVolume = 0.5f
    var voicesVolume = 0.5f
    var pauseBetweenTracks = 10
    //endregion

    //region 6: Multiplayer
    var multiplayer = GameSettingsMultiplayer()
    //endregion

    //region 7: Keyboard
    var keyBindings = KeyboardBindings()
    //endregion

    //region 8: Advanced
    var maxAutosavesStored = 10
    var turnsBetweenAutosaves = 1

    var androidCutout = false
    var androidHideSystemUi = true
    var fontFamilyData: FontFamilyData = FontFamilyData.default
    var fontSizeMultiplier: Float = 1f
    var longPressDelay = 1.1f
    var multiTapInterval = 0.25f

    /** Maximum zoom-out of the map - performance heavy */
    var maxWorldZoomOut = 2f
    var enableEasterEggs = true
    /** If on, selected notifications are drawn enlarged with wider padding */
    var enlargeSelectedNotification = true
    var useAStarPathfinding = false
    //endregion

    //region 9: Not in OptionsPopup

    // Controlled by dragging the minimap
    var minimapSize = 6    // default corresponds to 15% screen space

    // This was a checkbox setting long ago
    val showPixelUnits get() = unitSet != null

    // Preserves desktop window size between runs
    var windowState = WindowState()

    // Used by launcher to recognize a first-run
    var isFreshlyCreated = false

    // Controlled from ModManagementScreen
    var visualMods = HashSet<String>()

    // On load screen
    var showAutosaves = false

    /** Holds EmpireOverviewScreen per-page persistable states */
    val overview = OverviewPersistableData()

    /** Saves the last successful new game's setup */
    var lastGameSetup: GameSetupInfo? = null

    /** Whether the Nation Picker shows icons only or the horizontal "civBlocks" with leader/nation name */
    var nationPickerListMode = NationPickerListMode.List

    /** Don't close developer console after a successful command */
    var keepConsoleOpen = false
    /** Persist the history of successful developer console commands */
    val consoleCommandHistory = ArrayList<String>()

    //endregion


    /** used to migrate from older versions of the settings */
    var version: Int? = null

    init {
        // 26 = Android Oreo. Versions below may display permanent icon in notification bar.
        if (Gdx.app?.type == ApplicationType.Android && Gdx.app.version < 26) {
            multiplayer.turnCheckerPersistentNotificationEnabled = false
        }
    }

    //region <Methods>

    fun save() {
        if (Gdx.app == null) return // Simulation mode from ConsoleLauncher
        refreshWindowSize()
        UncivGame.Current.files.setGeneralSettings(this)
    }

    fun refreshWindowSize() {
        if (isFreshlyCreated || Gdx.app.type != ApplicationType.Desktop) return
        if (!Display.hasUserSelectableSize(screenMode)) return
        windowState = WindowState.current()
    }

    fun addCompletedTutorialTask(tutorialTask: String): Boolean {
        if (!tutorialTasksCompleted.add(tutorialTask)) return false
        UncivGame.Current.isTutorialTaskCollapsed = false
        save()
        return true
    }

    fun updateLocaleFromLanguage() {
        locale = LocaleCode.getLocale(language)
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

    @Readonly
    fun getCurrentNumberFormat(): NumberFormat {
        return LocaleCode.getNumberFormatFromLanguage(language)
    }

    //endregion
    //region <Nested classes>

    enum class PathfindingAlgorithm {
        ClassicPathfinding,
        AStarPathfinding
    }

    enum class NationPickerListMode { Icons, List }

    /**
     *  Knowledge on Window "state", limited.
     *  - Size: Saved
     *  - Iconified, Maximized: Not saved
     *  - Position / Multimonitor display choice: Not saved
     *
     *  Note: Useful on desktop only, on Android we do not explicitly support `Activity.isInMultiWindowMode` returning true.
     *  (On Android Display.hasUserSelectableSize will return false, and AndroidLauncher & co ignore it)
     *
     *  Open to future enhancement - but:
     *  retrieving a valid position from our upstream libraries while the window is maximized or iconified has proven tricky so far.
     */
    data class WindowState(val width: Int = 900, val height: Int = 600) {
        constructor(bounds: Rectangle) : this(bounds.width, bounds.height)

        companion object {
            /** Our choice of minimum window width */
            const val minimumWidth = 120
            /** Our choice of minimum window height */
            const val minimumHeight = 80

            fun current() = WindowState(Gdx.graphics.width, Gdx.graphics.height)
        }

        /**
         *  Constrains the dimensions of `this` [WindowState] to be within [minimumWidth] x [minimumHeight] to [maximumWidth] x [maximumHeight].
         *  @param maximumWidth defaults to unlimited
         *  @param maximumHeight defaults to unlimited
         *  @return `this` unchanged if it is within valid limits, otherwise a new WindowState that is.
         */
        fun coerceIn(maximumWidth: Int = Int.MAX_VALUE, maximumHeight: Int = Int.MAX_VALUE): WindowState {
            if (width in minimumWidth..maximumWidth && height in minimumHeight..maximumHeight)
                return this
            return WindowState(
                width.coerceIn(minimumWidth, maximumWidth),
                height.coerceIn(minimumHeight, maximumHeight)
            )
        }

        /**
         *  Constrains the dimensions of `this` [WindowState] to be within [minimumWidth] x [minimumHeight] to `maximumWidth` x `maximumHeight`.
         *  @param maximumWindowBounds provides maximum sizes
         *  @return `this` unchanged if it is within valid limits, otherwise a new WindowState that is.
         *  @see coerceIn
         */
        fun coerceIn(maximumWindowBounds: Rectangle) =
            coerceIn(maximumWindowBounds.width, maximumWindowBounds.height)
    }

    enum class ScreenSize(
        @Suppress("unused")  // Actual width determined by screen aspect ratio, this as comment only
        val virtualWidth: Float,
        val virtualHeight: Float
    ) {
        Micro(630f,420f),
        Tiny(750f,500f),
        Small(900f,600f),
        Medium(1050f,700f),
        Large(1200f,800f),
        Huge(1500f,1000f),
        FullHD(1920f, 1280f),
        QuadHD(2560f, 1707f);
        override fun toString() = name.tr() // Allow direct use in a SelectBox
    }

    class GameSettingsAutoPlay {
        var showAutoPlayButton = false
        var autoPlayUntilEnd = false
        var autoPlayMaxTurns = 10
        var fullAutoPlayAI = true
        var autoPlayMilitary = true
        var autoPlayCivilian = true
        var autoPlayEconomy = true
        var autoPlayTechnology = true
        var autoPlayPolicies = true
        var autoPlayReligion = true
        var autoPlayDiplomacy = true
    }

    //endregion
    //region Multiplayer-specific

    class GameSettingsMultiplayer {
        private var userId = ""
        fun getUserId() = userId
        fun setUserId(value: String) {
            if (userId.isNotEmpty() && userId != value) {
                ChatWebSocket.restart(force = true)
            }
            userId = value
        }

        /**
         * Never ever make it public. If you need a method make it.
         * But do remember to call [ChatWebSocket.restart] with `force = true` whenever required.
         */
        private val passwords = mutableMapOf<String, String>()
        @Readonly
        fun getPassword(serverUrl: String) = passwords[serverUrl]
        @Readonly
        fun getCurrentServerPassword() = passwords[server]
        fun setCurrentServerPassword(password: String) {
            val oldPassword = passwords[server]
            if (oldPassword != null && oldPassword != password) {
                ChatWebSocket.restart(force = true)
            }
            passwords[server] = password
        }

        @Suppress("unused")  // @GGuenni knows what he intended with this field
        var userName: String = ""

        private var server = Constants.uncivXyzServer
        fun getServer() = server
        fun setServer(value: String) {
            if (server != value) {
                server = value
                ChatWebSocket.restart(force = true)
            }
        }

        val friendList: MutableList<FriendList.Friend> = mutableListOf()
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

    //endregion
}
