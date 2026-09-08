package com.unciv.ui.popups.options

import com.badlogic.gdx.Gdx
import com.unciv.GUI
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.extensions.areSecretKeysPressed
import com.unciv.ui.images.ImageGetter

enum class OptionsPopupPages(
    val label: String,
    internal val iconPath: String,
    internal val getContent: OptionsPopup.() -> OptionsPopupTab
) {
    About("About", "Icons/Unciv128.png", { AboutTab(this) }),
    Display("Display", "UnitPromotionIcons/Scouting", { DisplayTab(this) }),
    Gameplay("Gameplay", "OtherIcons/Options", { GameplayTab(this) }),
    Automation("Automation", "OtherIcons/NationSwap", { AutomationTab(this) }),
    Language("Language", "FlagIcons/", { LanguageTab(this) }) {
        override fun getIcon(language: String) = ImageGetter.getImage(iconPath + language)
    },
    Sound("Sound", "OtherIcons/Speaker", { SoundTab(this) }),
    Multiplayer("Multiplayer", "OtherIcons/Multiplayer", { MultiplayerTab(this) }),
    Keys("Keys", "OtherIcons/Keyboard", { KeyBindingsTab(this, tabMinWidth - 40f) }) {   // 40 = padding
        override fun visible(withDebug: Boolean) = GUI.keyboardAvailable
    },
    Advanced("Advanced", "OtherIcons/Settings", { AdvancedTab(this) }),
    ModCheck("Locate mod errors", "OtherIcons/Mods", { ModCheckTab(this) }) {
        override fun visible(withDebug: Boolean) = RulesetCache.size > BaseRuleset.entries.size
    },
    Debug("Debug", "OtherIcons/SecretOptions", { DebugTab(this) }) {
        override fun visible(withDebug: Boolean) = withDebug || Gdx.input.areSecretKeysPressed()
    },
    ;

    internal open fun visible(withDebug: Boolean) = true
    internal open fun getIcon(language: String) =
        if (iconPath.endsWith(".png")) ImageGetter.getExternalImage(iconPath)
        else ImageGetter.getImage(iconPath)

    companion object {
        operator fun get(ordinal: Int) = entries.first { it.ordinal == ordinal }
    }
}
