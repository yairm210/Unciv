package com.unciv

import com.unciv.logic.CustomSaveLocationHelper
import com.unciv.ui.utils.CrashReportSender
import com.unciv.ui.utils.NativeFontImplementation

class UncivGameParameters(val version: String,
                          val crashReportSender: CrashReportSender? = null,
                          val exitEvent: (()->Unit)? = null,
                          val cancelDiscordEvent: (()->Unit)? = null,
                          val fontImplementation: NativeFontImplementation? = null,
                          val consoleMode: Boolean = false,
                          val customSaveLocationHelper: CustomSaveLocationHelper? = null) {
}