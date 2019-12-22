package com.unciv.ui.utils

import com.badlogic.gdx.utils.Json
import com.unciv.UncivGame
import com.unciv.models.CrashReport
import com.unciv.ui.saves.Gzip
import com.unciv.ui.worldscreen.optionstable.PopupTable

interface CrashController {

    fun crashOccurred()
    fun showDialogIfNeeded()

    class Impl(private val crashReportSender: CrashReportSender?) : CrashController {

        companion object {
            private const val MESSAGE = "Oh no! It looks like something went DISASTROUSLY wrong!" +
                                        " This is ABSOLUTELY not supposed to happen! Please send us an report" +
                                        " and we'll try to fix it as fast as we can!"
            private const val MESSAGE_FALLBACK = "Oh no! It looks like something went DISASTROUSLY wrong!" +
                                                 " This is ABSOLUTELY not supposed to happen! Please send me (yairm210@hotmail.com)" +
                                                 " an email with the game information (menu -> save game -> copy game info -> paste into email)" +
                                                 " and I'll try to fix it as fast as I can!"
        }

        override fun crashOccurred() {
            UncivGame.Current.settings.run {
                hasCrashedRecently = true
                save()
            }
        }

        override fun showDialogIfNeeded() {
            UncivGame.Current.settings.run {
                if (hasCrashedRecently) {
                    prepareDialog().open()
                    hasCrashedRecently = false
                    save()
                }
            }
        }

        private fun prepareDialog(): PopupTable {
            return if (crashReportSender == null) {
                PopupTable(UncivGame.Current.worldScreen).apply {
                    addGoodSizedLabel(MESSAGE_FALLBACK).row()
                    addCloseButton()
                }
            } else {
                PopupTable(UncivGame.Current.worldScreen).apply {
                    addGoodSizedLabel(MESSAGE).row()
                    addButton("Send report") {
                        crashReportSender.sendReport(buildReport())
                        close()
                    }
                    addCloseButton()
                }
            }
        }

        private fun buildReport(): CrashReport {
            return UncivGame.Current.run {
                val zippedGameInfo = Json().toJson(gameInfo).let { Gzip.zip(it) }
                val zippedGameSettings = Json().toJson(settings).let { Gzip.zip(it) }
                CrashReport(zippedGameInfo, zippedGameSettings, version)
            }
        }
    }
}