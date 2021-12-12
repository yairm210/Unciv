package com.unciv

import com.unciv.logic.CustomSaveLocationHelper
import com.unciv.scripting.ExecResult
import com.unciv.scripting.ScriptingBackendType
import com.unciv.ui.utils.CrashReportSender
import com.unciv.ui.utils.LimitOrientationsHelper
import com.unciv.ui.utils.NativeFontImplementation

class UncivGameParameters(val version: String,
                          val crashReportSender: CrashReportSender? = null,
                          val cancelDiscordEvent: (() -> Unit)? = null,
                          val fontImplementation: NativeFontImplementation? = null,
                          val consoleMode: Boolean = false,
                          val customSaveLocationHelper: CustomSaveLocationHelper? = null,
                          val limitOrientationsHelper: LimitOrientationsHelper? = null,
                          val runScriptAndExit: Triple<ScriptingBackendType, String, ((ExecResult) -> Unit)?>? = null
) { }
