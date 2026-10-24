package com.unciv.ui.components

import com.unciv.models.translations.tr
import kotlin.math.abs

object YearTextUtil {

    /** Converts a year to a human-readable year (e.g. "1800 AD" or "3000 BC") while respecting the Maya calendar. */
    fun toYearText(year: Int, usesMayaCalendar: Boolean): String {
        val yearText = if (usesMayaCalendar) MayaCalendar.yearToMayaDate(year)
        else "[" + abs(year) + "] " + (if (year < 0) "BC" else "AD")
        return yearText.tr()
    }
}
