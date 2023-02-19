package com.unciv

import com.unciv.logic.files.CustomFileLocationHelper
import com.unciv.ui.crashhandling.CrashReportSysInfo
import com.unciv.ui.components.AudioExceptionHelper
import com.unciv.ui.components.GeneralPlatformSpecificHelpers
import com.unciv.ui.components.NativeFontImplementation

class UncivGameParameters(val crashReportSysInfo: CrashReportSysInfo? = null,
                          val cancelDiscordEvent: (() -> Unit)? = null,
                          val fontImplementation: NativeFontImplementation? = null,
                          val consoleMode: Boolean = false,
                          val customFileLocationHelper: CustomFileLocationHelper? = null,
                          val platformSpecificHelper: GeneralPlatformSpecificHelpers? = null,
                          val audioExceptionHelper: AudioExceptionHelper? = null
)
