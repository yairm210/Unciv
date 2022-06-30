package com.unciv.logic

import java.time.LocalDate
import java.time.Month


object HolidayDates {
    enum class Holidays {
        Easter {
            override fun getByYear(year: Int): DateRange {
                // https://en.wikipedia.org/wiki/Date_of_Easter
                val a = year % 19
                val b = year / 100
                val c = year % 100
                val d = b / 4
                val e = b % 4
                val g = (8 * b + 13) / 25
                val h = (19 * a + b - d - g + 15) % 30
                val i = c / 4
                val k = c % 4
                val l = (32 + 2 * e + 2 * i - h - k) % 7
                val m = (a + 11 * h + 19 * l) / 433
                val n = (h + l - 7 * m + 90) / 25
                val p = (h + l - 7 * m + 33 * n + 19) % 32
                val sunday = LocalDate.of(year, n, p)
                return DateRange.of(sunday.minusDays(2), 4)
            }
        },
        Samhain {
            override fun getByYear(year: Int) = DateRange.of(year, 10, 31)
        },
        Xmas {
            override fun getByYear(year: Int) = DateRange.of(year, 12, 24, 4)
        }
        ;

        abstract fun getByYear(year: Int): DateRange

        companion object {
            fun safeValueOf(name: String) = values().firstOrNull { it.name == name }
        }
    }

    class DateRange( override val start: LocalDate, override val endInclusive: LocalDate) : ClosedRange<LocalDate> {
        override fun toString() = "$start..$endInclusive"
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DateRange) return false
            return start == other.start && endInclusive == other.endInclusive
        }
        override fun hashCode() = 31 * start.hashCode() + endInclusive.hashCode()

        companion object {
            fun of(date: LocalDate) = DateRange(date, date)
            fun of(year: Int, month: Int, day: Int) = of(LocalDate.of(year, month, day))
            fun of(date: LocalDate, duration: Int) = DateRange(date, date.plusDays(duration - 1L))
            fun of(year: Int, month: Int, day: Int, duration: Int) = of(LocalDate.of(year, month, day), duration)
        }
    }

    fun getHolidayByYear(holiday: Holidays, year: Int) = holiday.getByYear(year)

    fun getHolidayByDate(date: LocalDate = LocalDate.now()): Holidays? {
        return System.getProperty("easterEgg")?.let {
            Holidays.safeValueOf(it)
        } ?: Holidays.values().firstOrNull {
            date in it.getByYear(date.year)
        }
    }

    fun getMonth(): Month = System.getProperty("month")?.toIntOrNull()?.let {
            Month.of(it)
        } ?: LocalDate.now().month
}
