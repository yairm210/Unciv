package com.unciv.ui.options

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.models.UncivSound
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSetting
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.SettingsPropertyChanged
import com.unciv.models.metadata.SettingsPropertyUncivSoundChanged
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.TabbedPager
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.toCheckBox
import com.unciv.ui.utils.extensions.toGdxArray
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.withGLContext
import kotlin.reflect.KMutableProperty0

/**
 * The Options (Settings) Popup
 * @param screen The caller - note if this is a [WorldScreen] or [MainMenuScreen] they will be rebuilt when major options change.
 */
//region Fields
class OptionsPopup(
    screen: BaseScreen,
    private val selectPage: Int = defaultPage,
    private val onClose: () -> Unit = {}
) : Popup(screen.stage, /** [TabbedPager] handles scrolling */ scrollable = false ) {
    val settings = screen.game.settings
    val tabs: TabbedPager
    val selectBoxMinWidth: Float

    //endregion

    companion object {
        const val defaultPage = 2  // Gameplay
    }

    init {
        if (settings.addCompletedTutorialTask("Open the options table"))
            (screen as? WorldScreen)?.shouldUpdate = true

        innerTable.pad(0f)
        val tabMaxWidth: Float
        val tabMinWidth: Float
        val tabMaxHeight: Float
        screen.run {
            selectBoxMinWidth = if (stage.width < 600f) 200f else 240f
            tabMaxWidth = if (isPortrait()) stage.width - 10f else 0.8f * stage.width
            tabMinWidth = 0.6f * stage.width
            tabMaxHeight = (if (isPortrait()) 0.7f else 0.8f) * stage.height
        }
        tabs = TabbedPager(
            tabMinWidth, tabMaxWidth, 0f, tabMaxHeight,
            headerFontSize = 21, backgroundColor = Color.CLEAR, capacity = 8
        )
        add(tabs).pad(0f).grow().row()

        tabs.addPage(
            "About",
            aboutTab(screen),
            ImageGetter.getExternalImage("Icon.png"), 24f
        )
        tabs.addPage(
            "Display",
            displayTab(this, ::reloadWorldAndOptions, ::reloadWorldAndOptions),
            ImageGetter.getImage("UnitPromotionIcons/Scouting"), 24f
        )
        tabs.addPage(
            "Gameplay",
            gameplayTab(this),
            ImageGetter.getImage("OtherIcons/Options"), 24f
        )
        tabs.addPage(
            "Language",
            languageTab(this, ::reloadWorldAndOptions),
            ImageGetter.getImage("FlagIcons/${settings.language}"), 24f
        )
        tabs.addPage(
            "Sound",
            soundTab(this),
            ImageGetter.getImage("OtherIcons/Speaker"), 24f
        )
        tabs.addPage(
            "Multiplayer",
            multiplayerTab(this),
            ImageGetter.getImage("OtherIcons/Multiplayer"), 24f
        )
        tabs.addPage(
            "Advanced",
            advancedTab(this, ::reloadWorldAndOptions),
            ImageGetter.getImage("OtherIcons/Settings"), 24f
        )

        if (RulesetCache.size > BaseRuleset.values().size) {
            val content = ModCheckTab(screen)
            tabs.addPage("Locate mod errors", content, ImageGetter.getImage("OtherIcons/Mods"), 24f)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT))) {
            tabs.addPage("Debug", debugTab(), ImageGetter.getImage("OtherIcons/SecretOptions"), 24f, secret = true)
        }

        addCloseButton {
            screen.game.musicController.onChange(null)
            screen.game.platformSpecificHelper?.allowPortrait(settings.allowAndroidPortrait)
            onClose()
        }.padBottom(10f)

        pack() // Needed to show the background.
        center(screen.stage)
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        if (!visible) return
        tabs.askForPassword(secretHashCode = 2747985)
        if (tabs.activePage < 0) tabs.selectPage(selectPage)
    }

    /** Reload this Popup after major changes (resolution, tileset, language, font) */
    private fun reloadWorldAndOptions() {
        Concurrency.run("Reload from options") {
            settings.save()
            val screen = UncivGame.Current.screen
            if (screen is WorldScreen) {
                UncivGame.Current.reloadWorldscreen()
            } else if (screen is MainMenuScreen) {
                withGLContext {
                    UncivGame.Current.replaceCurrentScreen(MainMenuScreen())
                }
            }
            withGLContext {
                UncivGame.Current.screen?.openOptionsPopup(tabs.activePage)
            }
        }
    }

    fun addCheckbox(table: Table, text: String, initialState: Boolean, updateWorld: Boolean = false, action: ((Boolean) -> Unit)) {
        val checkbox = text.toCheckBox(initialState) {
            action(it)
            settings.save()
            val worldScreen = UncivGame.Current.getWorldScreenIfActive()
            if (updateWorld && worldScreen != null) worldScreen.shouldUpdate = true
        }
        table.add(checkbox).colspan(2).left().row()
    }

    fun addCheckbox(table: Table,
                    text: String,
                    settingsProperty: KMutableProperty0<Boolean>,
                    updateWorld: Boolean = false,
                    action: (Boolean) -> Unit = {}) {
        addCheckbox(table, text, settingsProperty.get(), updateWorld) {
            action(it)
            settingsProperty.set(it)
        }
    }

}

class SelectItem<T>(val label: String, val value: T) {
    override fun toString(): String = label.tr()
    override fun equals(other: Any?): Boolean = other is SelectItem<*> && value == other.value
    override fun hashCode(): Int = value.hashCode()
}

/**
 * For creating a SelectBox that is automatically backed by a [GameSettings] property.
 *
 * **Warning:** [T] has to be the same type as the [GameSetting.kClass] of the [GameSetting] argument.
 *
 * This will also automatically send [SettingsPropertyChanged] events.
 */
open class SettingsSelect<T : Any>(
    labelText: String,
    items: Iterable<SelectItem<T>>,
    private val setting: GameSetting,
    settings: GameSettings
) {
    private val settingsProperty: KMutableProperty0<T> = setting.getProperty(settings)
    private val label = createLabel(labelText)
    protected val refreshSelectBox = createSelectBox(items.toGdxArray(), settings)
    val items by refreshSelectBox::items

    private fun createLabel(labelText: String): Label {
        val selectLabel = labelText.toLabel()
        selectLabel.wrap = true
        return selectLabel
    }

    private fun createSelectBox(initialItems: Array<SelectItem<T>>, settings: GameSettings): SelectBox<SelectItem<T>> {
        val selectBox = SelectBox<SelectItem<T>>(BaseScreen.skin)
        selectBox.items = initialItems

        selectBox.selected = initialItems.firstOrNull() { it.value == settingsProperty.get() } ?: items.first()
        selectBox.onChange {
            val newValue = selectBox.selected.value
            settingsProperty.set(newValue)
            sendChangeEvent(newValue)
            settings.save()
        }

        return selectBox
    }

    fun onChange(listener: (event: ChangeListener.ChangeEvent?) -> Unit) {
        refreshSelectBox.onChange(listener)
    }

    fun addTo(table: Table) {
        table.add(label).growX().left()
        table.add(refreshSelectBox).row()
    }

    /** Maintains the currently selected item if possible, otherwise resets to the first item */
    fun replaceItems(options: Array<SelectItem<T>>) {
        val prev = refreshSelectBox.selected
        refreshSelectBox.items = options
        refreshSelectBox.selected = prev
    }

    private fun sendChangeEvent(item: T) {
        when (item) {
            is UncivSound -> EventBus.send(object : SettingsPropertyUncivSoundChanged {
                override val gameSetting = setting
                override val value: UncivSound = settingsProperty.get() as UncivSound
            })
            else -> EventBus.send(object : SettingsPropertyChanged {
                override val gameSetting = setting
            })
        }
    }
}
