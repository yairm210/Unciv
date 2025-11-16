package com.unciv.ui.popups.options

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.extensions.areSecretKeysPressed
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.getCloseButton
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import kotlin.reflect.KMutableProperty0

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
    override val activePage get() = tabs.activePage
    override val rightWidgetMinWidth: Float
    private val tabMinWidth: Float

    //endregion

    companion object {
        const val defaultPage = 2  // Gameplay
    }

    init {
        clickBehindToClose = true

        if (settings.addCompletedTutorialTask("Open the options table"))
            (screen as? WorldScreen)?.shouldUpdate = true

        innerTable.pad(0f)

        val tabMaxWidth: Float
        val tabMaxHeight: Float
        screen.run {
            rightWidgetMinWidth = if (stage.width < 600f) 200f else 240f
            tabMaxWidth = if (isPortrait()) stage.width - 10f else 0.8f * stage.width
            tabMinWidth = 0.6f * stage.width
            tabMaxHeight = 0.8f * stage.height
        }
        // Since all pages now initialize their content late, on activation, we can't measure their preferred size anymore -> use tabMaxHeight for tabMinHeight
        // That's not really bad, the tabs are long enough so some will always need scrolling even on the largest UI size setting.
        tabs = TabbedPager(
            tabMinWidth, tabMaxWidth, tabMaxHeight, tabMaxHeight,
            headerFontSize = 21, backgroundColor = Color.CLEAR, capacity = 8
        )
        add(tabs).pad(0f).grow().row()

        tabs.addPage(
            "About",
            aboutTab(),
            ImageGetter.getExternalImage("Icons/Unciv128.png"), 24f
        )
        tabs.addPage(
            "Display",
            DisplayTab(this),
            ImageGetter.getImage("UnitPromotionIcons/Scouting"), 24f
        )
        tabs.addPage(
            "Gameplay",
            GameplayTab(this),
            ImageGetter.getImage("OtherIcons/Options"), 24f
        )
        tabs.addPage(
            "Automation",
            AutomationTab(this),
            ImageGetter.getImage("OtherIcons/NationSwap"), 24f
        )
        tabs.addPage(
            "Language",
            LanguageTab(this),
            ImageGetter.getImage("FlagIcons/${settings.language}"), 24f
        )
        tabs.addPage(
            "Sound",
            SoundTab(this),
            ImageGetter.getImage("OtherIcons/Speaker"), 24f
        )
        tabs.addPage(
            "Multiplayer",
            MultiplayerTab(this),
            ImageGetter.getImage("OtherIcons/Multiplayer"), 24f
        )

        if (GUI.keyboardAvailable) {
            tabs.addPage(
                "Keys",
                KeyBindingsTab(this, tabMinWidth - 40f),  // 40 = padding
                ImageGetter.getImage("OtherIcons/Keyboard"), 24f
            )
        }

        tabs.addPage(
            "Advanced",
            AdvancedTab(this),
            ImageGetter.getImage("OtherIcons/Settings"), 24f
        )

        if (RulesetCache.size > BaseRuleset.entries.size) {
            val content = ModCheckTab(screen)
            tabs.addPage("Locate mod errors", content, ImageGetter.getImage("OtherIcons/Mods"), 24f)
        }
        if (withDebug || Gdx.input.areSecretKeysPressed()) {
            tabs.addPage("Debug", DebugTab(this), ImageGetter.getImage("OtherIcons/SecretOptions"), 24f)
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
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        if (!visible) return
        if (tabs.activePage < 0) tabs.selectPage(selectPage)
    }

    internal fun addCheckbox(table: Table, text: String, initialState: Boolean, updateWorld: Boolean = false, newRow: Boolean = true, action: ((Boolean) -> Unit)) {
        val checkbox = text.toCheckBox(initialState) {
            action(it)
            val worldScreen = GUI.getWorldScreenIfActive()
            if (updateWorld && worldScreen != null) worldScreen.shouldUpdate = true
        }
        if (newRow) table.add(checkbox).colspan(2).left().row()
        else table.add(checkbox).left()
    }

    internal fun addCheckbox(
        table: Table,
        text: String,
        settingsProperty: KMutableProperty0<Boolean>,
        updateWorld: Boolean = false,
        action: (Boolean) -> Unit = {}
    ) {
        addCheckbox(table, text, settingsProperty.get(), updateWorld) {
            action(it)
            settingsProperty.set(it)
        }
    }
}
