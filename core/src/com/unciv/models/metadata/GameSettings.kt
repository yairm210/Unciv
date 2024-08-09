package com.unciv.models.metadata

import com.badlogic.gdx.Application.ApplicationType
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Base64Coder
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.FriendList
import com.unciv.models.UncivSound
import com.unciv.models.translations.Translations.Companion.getLocaleFromLanguage
import com.unciv.models.translations.Translations.Companion.getNumberFormatFromLanguage
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyboardBindings
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.worldscreen.NotificationsScroll
import com.unciv.utils.Display
import com.unciv.utils.ScreenOrientation
import java.text.Collator
import java.text.NumberFormat
import java.time.Duration
import java.util.EnumSet
import java.util.Locale
import kotlin.enums.EnumEntries
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty0

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
    var longTapMove: Boolean = true
    var language: String = Constants.english
    @Transient
    var locale: Locale? = null
    var screenSize: ScreenSize = ScreenSize.Small
    var screenMode: Int = 0
    var tutorialsShown = HashSet<String>()
    var tutorialTasksCompleted = HashSet<String>()

    var soundEffectsVolume = 0.5f
    var citySoundsVolume = 0.5f
    var musicVolume = 0.5f
    var voicesVolume = 0.5f
    var pauseBetweenTracks = 10

    var turnsBetweenAutosaves = 1
    var tileSet: String = Constants.defaultTileset
    var unitSet: String? = Constants.defaultUnitset
    var skin: String = Constants.defaultSkin
    var showTutorials: Boolean = true
    var autoAssignCityProduction: Boolean = true

    /** This set of construction names has two effects:
     *  * Matching constructions are no longer candidates for [autoAssignCityProduction]
     *  * Matching constructions are offered in a separate 'Disabled' category in CityScreen
     */
    var disabledAutoAssignConstructions = HashSet<String>()

    var autoBuildingRoads: Boolean = true
    var automatedWorkersReplaceImprovements = true
    var automatedUnitsMoveOnTurnStart: Boolean = false
    var automatedUnitsCanUpgrade: Boolean = false
    var automatedUnitsChoosePromotions: Boolean = false
    var citiesAutoBombardAtEndOfTurn: Boolean = false

    var showMinimap: Boolean = true
    var minimapSize: Int = 6    // default corresponds to 15% screen space
    var unitIconOpacity = 1f // default corresponds to fully opaque
    val showPixelUnits: Boolean get() = unitSet != null
    var showPixelImprovements: Boolean = true

    @Deprecated("Since 4.12.15", ReplaceWith("GameSettings.Animations.X in enabledAnimations"))
    var continuousRendering: Boolean? = null
    val enabledAnimations = EnabledAnimations()

    var orderTradeOffersByAmount = true
    var confirmNextTurn = false
    var windowState = WindowState()
    var isFreshlyCreated = false
    var visualMods = HashSet<String>()
    var useDemographics: Boolean = false
    var showZoomButtons: Boolean = false
    var forbidPopupClickBehindToClose: Boolean = false

    var notificationsLogMaxTurns = 5

    var showAutosaves: Boolean = false

    var androidCutout: Boolean = false
    var androidHideSystemUi = true

    var multiplayer = GameSettingsMultiplayer()

    var autoPlay = GameSettingsAutoPlay()

    // This is a string not an enum so if tabs change it won't screw up the json serialization
    //TODO remove line in a future update
    var lastOverviewPage = EmpireOverviewCategories.Cities.name
    /** Holds EmpireOverviewScreen per-page persistable states */
    val overview = OverviewPersistableData()

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

    /** NotificationScroll on Word Screen visibility control - mapped to [NotificationsScroll.UserSetting] enum */
    // Defaulting this to "" - and implement the fallback only in NotificationsScroll leads to Options popup and actual effect being in disagreement!
    var notificationScroll: String = NotificationsScroll.UserSetting.default().name

    /** If on, selected notifications are drawn enlarged with wider padding */
    var enlargeSelectedNotification = true

    /** Whether the Nation Picker shows icons only or the horizontal "civBlocks" with leader/nation name */
    var nationPickerListMode = NationPickerListMode.List

    /** Size of automatic display of UnitSet art in Civilopedia - 0 to disable */
    var pediaUnitArtSize = 0f

    /** Don't close developer console after a successful command */
    var keepConsoleOpen = false
    /** Persist the history of successful developer console commands */
    val consoleCommandHistory = ArrayList<String>()

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
        locale = getLocaleFromLanguage(language)
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

    fun getCurrentNumberFormat(): NumberFormat {
        return getNumberFormatFromLanguage(language)
    }

    //endregion
    //region <Nested classes>

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
        constructor(bounds: java.awt.Rectangle) : this(bounds.width, bounds.height)

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
        fun coerceIn(maximumWindowBounds: java.awt.Rectangle) =
            coerceIn(maximumWindowBounds.width, maximumWindowBounds.height)
    }

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

    enum class NationPickerListMode { Icons, List }

    /** Map Unciv language key to Java locale, for the purpose of getting a Collator for sorting.
     *  - Effect depends on the Java libraries and may not always conform to expectations.
     *    If in doubt, debug and see what Locale instance you get and compare its properties with `Locale.getDefault()`.
     *    (`Collator.getInstance(LocaleCode.*.run { Locale(language, country) }) to Collator.getInstance()`, drill to both `rules`, compare hashes - if equal and other properties equal, then Java doesn't know your Language))
     *  @property name same as translation file name with ' ', '_', '-', '(', ')' removed
     *  @property language ISO 639-1 code for the language
     *  @property country ISO 3166 code for the nation this is predominantly spoken in
     *  @property trueLanguage If set, used instead of language to trick Java into supplying a close-enough collator (a no-match would otherwise give us the default collator, not a collator for a partial match)
     */
    enum class LocaleCode(val language: String, val country: String, val trueLanguage: String? = null) {
        Afrikaans("af", "ZA"),
        Arabic("ar", "IQ"),
        Bangla("bn", "BD"),
        Belarusian("be", "BY"),
        Bosnian("bs", "BA"),
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
        Galician("gl", "ES"),
        German("de", "DE"),
        Greek("el", "GR"),
        Hindi("hi", "IN"),
        Hungarian("hu", "HU"),
        Indonesian("in", "ID"),
        Italian("it", "IT"),
        Japanese("ja", "JP"),
        Korean("ko", "KR"),
        Latin("la", "IT"),
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
        Rusyn("uk", "UA", "rus"), // No specific locale for rus exists, so use closest for collator
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
        Zulu("zu", "ZA")
    }

    enum class Animations(val label: String) {
        Expanders("Expander arrows"),
        Loading("Loading animation"),
        SelectedUnit("Selected unit flag"),
        HealthBar("Battle healthbar damage"),
        AttackSprite("Attack unit sprite"),
        FlashDamageRed("Flash damaged unit red"),
        DamageNumbers("Damage numbers"),
        WLTKFireworks("WLTK fireworks"),
        NukeExplosion("Nuke explosion"),
        MovementPath("Unit movement")
    }

    /** Wrapper for a `EnumSet<Animations>` set to allow Json serialization as json-array */
    // Would be nice to make this a `MutableSet<Animations> by set` but then Gdx ignores the Serializable interface, as it erroneously tests ClassReflection.isAssignableFrom(Collection.class) first
    class EnabledAnimations() : Json.Serializable {
        private val set: EnumSet<Animations> = EnumSet.noneOf(Animations::class.java)

        constructor(entries: EnumEntries<Animations>) : this() { set.addAll(entries) }
        constructor(entries: Array<out Animations>) : this() { set.addAll(entries) }

        fun setLevel(from: AnimationLevels) {
            set.clear()
            set.addAll(from.animations.set)
        }
        operator fun get(entry: Animations) = entry in set
        operator fun set(entry: Animations, value: Boolean) {
            if (value) set += entry else set -= entry
        }
        operator fun contains(entry: Animations) = entry in set
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EnabledAnimations) return false
            return set == other.set
        }
        override fun hashCode() = set.hashCode()

        override fun write(json: Json) {
            json.writeArrayStart("items")
            for (entry in set) json.writeValue(entry.name)
            json.writeArrayEnd()
        }
        override fun read(json: Json, jsonData: JsonValue) {
            val array = jsonData.get("items")
            if (array?.isArray != true) return
            var child = array.child
            while (child != null) {
                set.add(json.readValue(Animations::class.java, null, child))
                child = child.next
            }
        }

        companion object {
            fun of(vararg entries: Animations) = EnabledAnimations(entries)
        }
    }

    enum class AnimationLevels(val animations: EnabledAnimations) {
        None(EnabledAnimations()),
        Some(EnabledAnimations.of(Animations.Expanders, Animations.Loading, Animations.HealthBar, Animations.SelectedUnit, Animations.DamageNumbers, Animations.FlashDamageRed)),
        All(EnabledAnimations(Animations.entries))
    }

    //endregion
    //region Multiplayer-specific

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

    class GameSettingsAutoPlay {
        var showAutoPlayButton: Boolean = false
        var autoPlayUntilEnd: Boolean = false
        var autoPlayMaxTurns = 10
        var fullAutoPlayAI: Boolean = true
        var autoPlayMilitary: Boolean = true
        var autoPlayCivilian: Boolean = true
        var autoPlayEconomy: Boolean = true
        var autoPlayTechnology: Boolean = true
        var autoPlayPolicies: Boolean = true
        var autoPlayReligion: Boolean = true
        var autoPlayDiplomacy: Boolean = true
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

    //endregion
}
