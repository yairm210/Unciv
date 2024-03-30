package com.unciv.ui.components

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.popups.Popup
import com.unciv.ui.components.input.KeyCharAndCode.Companion.makeChar
import com.unciv.ui.components.input.KeyCharAndCode.Companion.toCode
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.abs

@Suppress("MemberVisibilityCanBePrivate")  // stuff only accessed through allSymbols looks cleaner with same visibility
object MayaCalendar {
    // Glyphs / icons
    private const val iconFolder = "MayaCalendar/"
    const val notificationIcon = "MayaCalendar/Maya"
    const val tunIcon = "MayaCalendar/Tun"
    const val katunIcon = "MayaCalendar/Katun"
    const val baktunIcon = "MayaCalendar/Baktun"
    const val tun = 'ම' // U+0DB8, no relation to maya, arbitrary choice (it's the sinhala letter 'mayanna')
    const val katun = 'ඹ' // U+0DB9
    const val baktun = 'ය' // U+0DBA
    // The mayan numerals are actually unicode U+1D2E0 to U+1D2F3, but we can't do those
    // so - I'm replacing the code points for small roman numerals U+2170 to U+2183
    const val zero = 'ⅰ' // U+2170
    const val nineteen = 'Ↄ' // U+2183
    val digits = zero..nineteen
    fun digitIcon(ch: Char) = iconFolder + (ch.toCode() - zero.toCode()).toString()

    val allSymbols = sequence {
        yield(tun to tunIcon)
        yield(katun to katunIcon)
        yield(baktun to baktunIcon)
        yieldAll(digits.map {it to digitIcon(it)})
    }.iterator().run {
        Array(23) { next() }
    }

    // Calculation
    private const val daysOn30000101BCE = 36000 + 5040 + 240 + 11

    private class MayaYear(year: Int) {
        val baktuns: Int
        val katuns: Int
        val tuns: Int

        init {
            val mayaDays = (year + 3000) * 365 + (year + 3000) / 4 + daysOn30000101BCE
            val totalTuns = if (mayaDays >= 0) mayaDays / 360 else 13 * 20 * 20 + mayaDays / 360
            val totalKatuns = totalTuns / 20
            baktuns = totalKatuns / 20
            katuns = totalKatuns - baktuns * 20
            tuns = totalTuns - totalKatuns * 20
        }

        override fun toString(): String {
            val baktunDigit = (zero.toCode() + baktuns).makeChar()
            val katunDigit = (zero.toCode() + katuns).makeChar()
            val tunDigit = (zero.toCode() + tuns).makeChar()
            return "$baktunDigit$baktun$katunDigit$katun$tunDigit$tun"
        }
    }

    fun yearToMayaDate(year: Int) = MayaYear(year).toString()

    // Maya ability implementation
    private fun isNewCycle(year: Int, otherYear: Int) = MayaYear(year).baktuns != MayaYear(otherYear).baktuns

    fun startTurnForMaya(civInfo: Civilization) {
        val game = civInfo.gameInfo
        val year = game.getYear()
        if (!isNewCycle(year, game.getYear(-1))) return
        civInfo.greatPeople.triggerMayanGreatPerson()
        if (civInfo.isAI() || UncivGame.Current.settings.autoPlay.fullAutoPlayAI)
            NextTurnAutomation.chooseGreatPerson(civInfo)
    }

    // User interface to explain changed year display
    fun openPopup(previousScreen: BaseScreen, civInfo: Civilization, year: Int) {
        Popup(previousScreen).apply {
            name = "MayaCalendar"
            addGoodSizedLabel("The Mayan Long Count", Constants.headingFontSize).apply {
                actor.color = civInfo.nation.getOuterColor()
            }.row()
            addSeparator(color = Color.DARK_GRAY)
            addGoodSizedLabel("Your scientists and theologians have devised a systematic approach to measuring long time spans - the Long Count. During the festivities whenever the current b'ak'tun ends, a Great Person will join you.").row()
            val yearText = ("[" + abs(year) + "] " + (if (year < 0) "BC" else "AD")).tr()
            addGoodSizedLabel("While the rest of the world calls the current year [$yearText], in the Maya Calendar that is:").padTop(10f).row()
            val mayaYear = MayaYear(year)
            addGoodSizedLabel(mayaYear.toString(), 42).row()
            addGoodSizedLabel("[${mayaYear.baktuns}] b'ak'tun, [${mayaYear.katuns}] k'atun, [${mayaYear.tuns}] tun").padBottom(10f).row()
            addCloseButton()
        }.open(true)
    }
}
