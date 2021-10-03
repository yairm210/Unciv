package com.unciv.ui.utils

object MayaCalendar {

    const val tun = 'ම' // U+0DB8, no relation to maya, arbitrary choice (it's the sinhala letter 'mayanna')
    const val katun = 'ඹ' // U+0DB9
    const val baktun = 'ය' // U+0DBA
    // The mayan numerals are actually unicode U+1D2E0 to U+1D2F3, but we can't do those
    // so - I'm replacing the code points for small roman numerals U+2170 to U+2183
    const val zero = 'ⅰ' // U+2170
    const val nineteen = 'Ↄ' // U+2183
    fun digitName(ch: Char) = (ch.code - zero.code).toString()

    private const val daysOn30000101BCE = 36000 + 5040 + 240 + 11

    fun yearToMayaDate(year: Int): String {
        val mayaDays = (year + 3000) * 365 + (year + 3000) / 4 + daysOn30000101BCE
        val tuns = if (mayaDays >= 0) mayaDays / 360 else 13 * 20 * 20 + mayaDays / 360
        val katuns = tuns / 20
        val baktuns = katuns / 20
        val baktunDigit = Char(zero.code + baktuns)
        val katunDigit = Char(zero.code + katuns - baktuns * 20)
        val tunDigit = Char(zero.code + tuns - katuns * 20)
        return "$baktunDigit$baktun$katunDigit$katun$tunDigit$tun"
    }
}
