package com.unciv.ui.popups.options

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.unciv.GUI
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.extensions.areSecretKeysPressed
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.getCloseButton
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Log

/**
 * The Options (Settings) Popup
 * @param screen The caller - note if this is a [WorldScreen] or [MainMenuScreen] they will be rebuilt when major options change.
 */
//region Fields
class OptionsPopup(
    screen: BaseScreen,
    private val selectPage: Int = defaultPage,
    withDebug: Boolean = false,
    private val onClose: () -> Unit = {}
) : Popup(screen.stage, /** [TabbedPager] handles scrolling */ scrollable = Scrollability.None), OptionsPopupHelpers {

    val game = screen.game
    val settings = screen.game.settings
    val tabs: TabbedPager
    override val selectBoxMinWidth: Float
    private val tabMinWidth: Float

    //endregion

    companion object {
        const val defaultPage = 2  // Gameplay
    }

    init {
        Log.debug("Start init OptionsPopup")
        clickBehindToClose = true

        if (settings.addCompletedTutorialTask("Open the options table"))
            (screen as? WorldScreen)?.shouldUpdate = true

        innerTable.pad(0f)
        val tabMaxWidth: Float
        val tabMaxHeight: Float
        screen.run {
            selectBoxMinWidth = if (stage.width < 600f) 200f else 240f
            tabMaxWidth = if (isPortrait()) stage.width - 10f else 0.8f * stage.width
            tabMinWidth = 0.6f * stage.width
            tabMaxHeight = 0.8f * stage.height
        }
        tabs = TabbedPager(
            tabMinWidth, tabMaxWidth, 0f, tabMaxHeight,
            headerFontSize = 21, backgroundColor = Color.CLEAR, capacity = 8
        )
        add(tabs).pad(0f).grow().row()

        tabs.addPage(
            "About",
            measureAndLog("AboutTab") { aboutTab() },
            ImageGetter.getExternalImage("Icons/Unciv128.png"), 24f
        )
        tabs.addPage(
            "Display",
            measureAndLog("DisplayTab") { DisplayTab(this, this::reloadWorldAndOptions) },
            ImageGetter.getImage("UnitPromotionIcons/Scouting"), 24f
        )
        tabs.addPage(
            "Gameplay",
            measureAndLog("GameplayTab") { GameplayTab(this) },
            ImageGetter.getImage("OtherIcons/Options"), 24f
        )
        tabs.addPage(
            "Automation",
            measureAndLog("AutomationTab") { AutomationTab(this) },
            ImageGetter.getImage("OtherIcons/NationSwap"), 24f
        )
        tabs.addPage(
            "Language",
            measureAndLog("LanguageTab") { LanguageTab(this, ::reloadWorldAndOptions) },
            ImageGetter.getImage("FlagIcons/${settings.language}"), 24f
        )
        tabs.addPage(
            "Sound",
            measureAndLog("SoundTab") { SoundTab(this) },
            ImageGetter.getImage("OtherIcons/Speaker"), 24f
        )
        tabs.addPage(
            "Multiplayer",
            measureAndLog("MultiplayerTab") { MultiplayerTab(this) },
            ImageGetter.getImage("OtherIcons/Multiplayer"), 24f
        )

        if (GUI.keyboardAvailable) {
            tabs.addPage(
                "Keys",
                measureAndLog("KeyBindingsTab") { KeyBindingsTab(this, tabMinWidth - 40f) },  // 40 = padding
                ImageGetter.getImage("OtherIcons/Keyboard"), 24f
            )
        }

        tabs.addPage(
            "Advanced",
            measureAndLog("AdvancedTab") { AdvancedTab(this, ::reloadWorldAndOptions) },
            ImageGetter.getImage("OtherIcons/Settings"), 24f
        )

        if (RulesetCache.size > BaseRuleset.entries.size) {
            val content = measureAndLog("ModCheckTab") { ModCheckTab(screen) }
            tabs.addPage("Locate mod errors", content, ImageGetter.getImage("OtherIcons/Mods"), 24f)
        }
        if (withDebug || Gdx.input.areSecretKeysPressed()) {
            tabs.addPage("Debug", measureAndLog("DebugTab") { DebugTab(this) }, ImageGetter.getImage("OtherIcons/SecretOptions"), 24f)
        }

        tabs.decorateHeader(getCloseButton {
            screen.game.musicController.onChange(null)
            center(screen.stage)
            tabs.selectPage(-1, false)
            settings.save()
            onClose() // activate the passed 'on close' callback
            close() // close this popup
        })

        pack() // Needed to show the background.
        center(screen.stage)
        Log.debug("End init OptionsPopup")
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        if (!visible) return
        if (tabs.activePage < 0) tabs.selectPage(selectPage)
    }

    private fun reloadWorldAndOptions() = reloadWorldAndOptions(tabs.activePage)
}
