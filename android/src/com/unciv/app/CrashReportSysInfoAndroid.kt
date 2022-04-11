package com.unciv.app

import android.os.Build
import com.unciv.ui.crashhandling.CrashReportSysInfo

object CrashReportSysInfoAndroid: CrashReportSysInfo {

    override fun getInfo(): String =
        """
        Device Model: ${Build.MODEL}
        API Level: ${Build.VERSION.SDK_INT}
        """.trimIndent()
}
