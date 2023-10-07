package com.unciv.logic

import com.unciv.logic.HolidayDates.DateRange
import com.unciv.logic.HolidayDates.Holidays
import com.unciv.logic.HolidayDates.getHolidayByDate
import com.unciv.logic.HolidayDates.getHolidayByYear
import org.junit.Assert
import org.junit.Test
import java.time.LocalDate

class HolidayDatesTest {
    @Test
    fun testEasterExamples() {
        val easter1961 = getHolidayByYear(Holidays.Easter, 1961)
        val knownEaster1961 = DateRange.of(1961, 3, 31, 4)
        val easter2022 = getHolidayByYear(Holidays.Easter, 2022)
        val knownEaster2022 = DateRange.of(2022, 4, 15, 4)
        Assert.assertTrue("Easter 1961 is calculated as $easter1961 but should be $knownEaster1961", easter1961 == knownEaster1961)
        Assert.assertTrue("Easter 2022 is calculated as $easter2022 but should be $knownEaster2022", easter2022 == knownEaster2022)
    }

    @Test
    fun testGetHolidayByDate() {
        val date = LocalDate.of(2022, 4, 15)
        val holiday = getHolidayByDate(date)
        Assert.assertTrue("$date should be easter but is calculated to be $holiday", holiday == Holidays.Easter)
    }
}
