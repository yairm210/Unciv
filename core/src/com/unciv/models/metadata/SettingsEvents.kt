package com.unciv.models.metadata

import com.unciv.logic.event.Event
import com.unciv.models.metadata.GameSettings.GameSetting

/** **Warning:** this event is in the process of completion and **not** used for all settings yet! **Only the settings in [GameSetting] get events sent!** */
interface SettingsPropertyChanged : Event {
    val gameSetting: GameSetting
}
