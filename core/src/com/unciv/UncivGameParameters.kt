package com.unciv

import com.unciv.logic.CustomSaveLocationHelper
import com.unciv.ui.crashhandling.CrashReportSysInfo
import com.unciv.ui.utils.AudioExceptionHelper
import com.unciv.ui.utils.LimitOrientationsHelper
import com.unciv.ui.utils.NativeFontImplementation

class UncivGameParameters(val version: String,
                          val crashReportSysInfo: CrashReportSysInfo? = null,
                          val cancelDiscordEvent: (() -> Unit)? = null,
                          val fontImplementation: NativeFontImplementation? = null,
                          val consoleMode: Boolean = false,
                          val customSaveLocationHelper: CustomSaveLocationHelper? = null,
                          val limitOrientationsHelper: LimitOrientationsHelper? = null,
                          val audioExceptionHelper: AudioExceptionHelper? = null
)
