package com.unciv.ui.utils

import com.unciv.models.CrashReport

interface CrashReportSender {

    fun sendReport(report: CrashReport)
}