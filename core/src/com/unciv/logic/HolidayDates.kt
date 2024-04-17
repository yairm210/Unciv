package com.unciv.logic

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import kotlin.random.Random


object HolidayDates {
    /**
     *  Known holidays (for easter egg use).
     *  @property getByYear Determines when the holiday happens for a given year.
     *  @property name Can be used as-is in a `-DeasterEgg=name` command line parameter for testing.
     *  @property chance To equalize impact over holidays with different durations. Affects [getHolidayByDate].
     *  @property floatingArt If set, will be presented as random floating art, texture name EasterEggs/$floatingArt$index
     */
    enum class Holidays(val chance: Float = 1f, val floatingArt: String? = null) {
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
        },
        DiaDeLosMuertos(0.5f, "Calavera") {
            // https://en.wikipedia.org/wiki/Day_of_the_Dead
            override fun getByYear(year: Int) = DateRange.of(year, 11, 1, 2)
        },
        YuleGoat {
            //todo add art and visualization

            // https://en.m.wikipedia.org/wiki/Advent:
            // "Advent begins with First Vespers (Evening Prayer I) of the Sunday that falls on or closest to 30 November"
            override fun getByYear(year: Int) =
                DateRange.of(LocalDate.of(year, 11, 30).closestWeekday(DayOfWeek.SUNDAY))
        },
        Qingming {
            //todo add art and visualization

            // https://en.wikipedia.org/wiki/Qingming_Festival
            // "it falls on the first day of the fifth solar term (also called Qingming) of the traditional Chinese lunisolar calendar.
            // This makes it the 15th day after the Spring Equinox, either 4, 5 or 6 April in a given year"
            override fun getByYear(year: Int): DateRange {
                val springEquinoxInstant = Equinoxes.knownValues[year] ?: return DateRange.never
                val springEquinox = LocalDate.ofInstant(springEquinoxInstant, ZoneId.systemDefault())
                return DateRange.of(springEquinox.plusDays(15L))
            }
        },
        Diwali(0.2f, "Diwali") {
            // https://en.wikipedia.org/wiki/Diwali#Dates
            // Darkest new moon night between mid-october and mid-november, then add +/- two days for a 5-day festival...
            // For moon phase, could adapt http://www.stargazing.net/kepler/jsmoon.html - or use a table
            override fun getByYear(year: Int): DateRange {
                val knownValue = DiwaliTable.knownValues[year] ?: return DateRange.never
                return DateRange.of(knownValue.plusDays(-2L), 5)
            }
        }

        // Inspiration...
        // https://github.com/00-Evan/shattered-pixel-dungeon/blob/master/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/utils/Holiday.java
        // ... has Lunar new year, April fools and Pride.
        // The birthday of the game itself (2017-11-21: https://github.com/yairm210/Unciv/commit/45b4131c0b7c9aa9c63cfca26ac744056aa70b71)
        // Fridays the 13th are also candidates.
        // Last not least: Passah - date calculation tricky
        ;

        abstract fun getByYear(year: Int): DateRange

        companion object {
            fun safeValueOf(name: String) = values().firstOrNull { it.name == name }
        }
    }

    open class DateRange(override val start: LocalDate, override val endInclusive: LocalDate) : ClosedRange<LocalDate> {
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
            val never = DateRange(LocalDate.now(), LocalDate.now().plusDays(-1L))
        }
    }

    fun getHolidayByYear(holiday: Holidays, year: Int) = holiday.getByYear(year)

    fun getHolidayByDate(date: LocalDate = LocalDate.now()): Holidays? {
        return System.getProperty("easterEgg")?.let {
            Holidays.safeValueOf(it)
        } ?: Holidays.values().firstOrNull {
            date in it.getByYear(date.year) && Random.nextFloat() <= it.chance
        }
    }

    fun getMonth(): Month = System.getProperty("month")?.toIntOrNull()?.let {
            Month.of(it)
        } ?: LocalDate.now().month

    private fun LocalDate.closestWeekday(day: DayOfWeek): LocalDate {
        val delta = (7 + dayOfWeek.ordinal - day.ordinal) % 7
        return if (delta < 4) plusDays(delta.toLong()) else minusDays((7 - delta).toLong())
    }

    private object Equinoxes {
        // http://www.astropixels.com/ephemeris/soleq2001.html
        val knownValues by lazy {
            listOf(
                2024 to "2024-03-20T03:07:00Z",
                2025 to "2025-03-20T09:02:00Z",
                2026 to "2026-03-20T14:46:00Z",
                2027 to "2027-03-20T20:25:00Z",
                2028 to "2028-03-20T02:17:00Z",
                2029 to "2029-03-20T08:01:00Z",
                2030 to "2030-03-20T13:51:00Z",
                2031 to "2031-03-20T19:41:00Z",
                2032 to "2032-03-20T01:23:00Z",
                2033 to "2033-03-20T07:23:00Z",
                2034 to "2034-03-20T13:18:00Z",
                2035 to "2035-03-20T19:03:00Z",
                2036 to "2036-03-20T01:02:00Z",
                2037 to "2037-03-20T06:50:00Z",
                2038 to "2038-03-20T12:40:00Z",
                2039 to "2039-03-20T18:32:00Z",
                2040 to "2040-03-20T00:11:00Z",
                2041 to "2041-03-20T06:07:00Z",
                2042 to "2042-03-20T11:53:00Z",
                2043 to "2043-03-20T17:29:00Z",
                2044 to "2044-03-19T23:20:00Z",
                2045 to "2045-03-20T05:08:00Z",
                2046 to "2046-03-20T10:58:00Z",
                2047 to "2047-03-20T16:52:00Z",
                2048 to "2048-03-19T22:34:00Z",
                2049 to "2049-03-20T04:28:00Z",
                2050 to "2050-03-20T10:20:00Z",
                2051 to "2051-03-20T15:58:00Z",
                2052 to "2052-03-19T21:56:00Z",
                2053 to "2053-03-20T03:46:00Z",
                2054 to "2054-03-20T09:35:00Z",
                2055 to "2055-03-20T15:28:00Z",
                2056 to "2056-03-19T21:11:00Z",
                2057 to "2057-03-20T03:08:00Z",
                2058 to "2058-03-20T09:04:00Z",
                2059 to "2059-03-20T14:44:00Z",
                2060 to "2060-03-19T20:37:00Z",
                2061 to "2061-03-20T02:26:00Z",
                2062 to "2062-03-20T08:07:00Z",
                2063 to "2063-03-20T13:59:00Z",
                2064 to "2064-03-19T19:40:00Z",
                2065 to "2065-03-20T01:27:00Z",
                2066 to "2066-03-20T07:19:00Z",
                2067 to "2067-03-20T12:55:00Z",
                2068 to "2068-03-19T18:51:00Z",
                2069 to "2069-03-20T00:44:00Z",
                2070 to "2070-03-20T06:35:00Z",
                2071 to "2071-03-20T12:36:00Z",
                2072 to "2072-03-19T18:19:00Z",
                2073 to "2073-03-20T00:12:00Z",
                2074 to "2074-03-20T06:09:00Z",
                2075 to "2075-03-20T11:48:00Z",
                2076 to "2076-03-19T17:37:00Z",
                2077 to "2077-03-19T23:30:00Z",
                2078 to "2078-03-20T05:11:00Z",
                2079 to "2079-03-20T11:03:00Z",
                2080 to "2080-03-19T16:43:00Z",
                2081 to "2081-03-19T22:34:00Z",
                2082 to "2082-03-20T04:32:00Z",
                2083 to "2083-03-20T10:08:00Z",
                2084 to "2084-03-19T15:58:00Z",
                2085 to "2085-03-19T21:53:00Z",
                2086 to "2086-03-20T03:36:00Z",
                2087 to "2087-03-20T09:27:00Z",
                2088 to "2088-03-19T15:16:00Z",
                2089 to "2089-03-19T21:07:00Z",
                2090 to "2090-03-20T03:03:00Z",
                2091 to "2091-03-20T08:40:00Z",
                2092 to "2092-03-19T14:33:00Z",
                2093 to "2093-03-19T20:35:00Z",
                2094 to "2094-03-20T02:20:00Z",
                2095 to "2095-03-20T08:14:00Z",
                2096 to "2096-03-19T14:03:00Z",
                2097 to "2097-03-19T19:49:00Z",
                2098 to "2098-03-20T01:38:00Z",
                2099 to "2099-03-20T07:17:00Z",
                2100 to "2100-03-20T13:04:00Z",
            ).associateBy({ it.first }, { Instant.parse(it.second) })
        }
    }

    private object DiwaliTable {
        // from e.g. https://www.theholidayspot.com/diwali/calendar.htm
        val knownValues by lazy {
            listOf(
                2024 to "2024-11-01",
                2025 to "2025-10-21",
                2026 to "2026-11-08",
                2027 to "2027-10-29",
                2028 to "2028-10-17",
                2029 to "2029-11-05",
                2030 to "2030-10-26",
                2031 to "2031-11-14",
                2032 to "2032-11-02",
                2033 to "2033-10-22",
                2034 to "2034-11-10",
                2035 to "2035-10-20",
                2036 to "2036-10-19",
                2037 to "2037-11-07",
                2038 to "2038-10-27",
                2039 to "2039-10-17",
                2040 to "2040-11-04",
            ).associateBy({ it.first }, { LocalDate.parse(it.second) })
        }
    }
}
