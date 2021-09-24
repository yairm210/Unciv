package com.unciv.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.unciv.models.CrashReport
import com.unciv.ui.utils.CrashReportSender

class CrashReportSenderAndroid(private val activity: Activity) : CrashReportSender {

    companion object {
        private const val EMAIL_TO = "yairm210@hotmail.com"
        private const val CHOOSER_TITLE = "Send mail"
        private const val CANNOT_SEND_EMAIL = "There are no email clients installed."
        private const val EMAIL_TITLE = "Crash report"
        private const val EMAIL_BODY = "\n--------------------------------\n" +
                                       "Game version: %s\n" +
                                       "OS version: %s\n" +
                                       "Device model: %s\n" +
                                       "Mods: %s\n" +
                                       "Game data: \n%s\n"
    }

    override fun sendReport(report: CrashReport) {
        activity.runOnUiThread {
            try {
                activity.startActivity(Intent.createChooser(prepareIntent(report), CHOOSER_TITLE))
            } catch (ex: ActivityNotFoundException) {
                Toast.makeText(activity, CANNOT_SEND_EMAIL, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun prepareIntent(report: CrashReport) = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL_TO))
        putExtra(Intent.EXTRA_SUBJECT, "$EMAIL_TITLE - ${report.version}")
        putExtra(Intent.EXTRA_TEXT, buildEmailBody(report))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun buildEmailBody(report: CrashReport): String =
            EMAIL_BODY.format(report.version, Build.VERSION.SDK_INT, Build.MODEL, report.mods.joinToString(), report.gameInfo)
}