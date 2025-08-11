package com.unciv.logic

import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class IdHelperTests {
    @Test
    fun testCheckDigits() {
        val correctString = "2ddb3a34-0699-4126-b7a5-38603e665928"
        val inCorrectString1 = "2ddb3a34-0699-4126-b7a5-38603e665929"
        val inCorrectString2 = "2ddb3a34-0969-4126-b7a5-38603e665928"
        val inCorrectString3 = "2ddb3a34-0699-4126-b7a"
        val inCorrectString4 = "0699-4126-b7a5-38603e665928-2ddb3a34"

        val correctLuhn = IdChecker.getCheckDigit(correctString)
        val correctLuhn2 = IdChecker.getCheckDigit(correctString)
        val inCorrectLuhn1 = IdChecker.getCheckDigit(inCorrectString1)
        val inCorrectLuhn2 = IdChecker.getCheckDigit(inCorrectString2)
        val inCorrectLuhn3 = IdChecker.getCheckDigit(inCorrectString3)
        val inCorrectLuhn4 = IdChecker.getCheckDigit(inCorrectString4)

        Assert.assertEquals(correctLuhn, correctLuhn2)
        Assert.assertNotEquals(inCorrectLuhn1, correctLuhn)
        Assert.assertNotEquals(inCorrectLuhn2, correctLuhn)
        Assert.assertNotEquals(inCorrectLuhn3, correctLuhn)
        Assert.assertNotEquals(inCorrectLuhn4, correctLuhn)

        Assert.assertNotEquals(inCorrectLuhn1, inCorrectLuhn2)
        Assert.assertNotEquals(inCorrectLuhn1, inCorrectLuhn3)
        Assert.assertNotEquals(inCorrectLuhn1, inCorrectLuhn4)

        Assert.assertNotEquals(inCorrectLuhn2, inCorrectLuhn3)
        Assert.assertNotEquals(inCorrectLuhn2, inCorrectLuhn4)

        Assert.assertNotEquals(inCorrectLuhn3, inCorrectLuhn4)
    }

    @Test
    fun testIdsSuccess() {
        val correctString = "2ddb3a34-0699-4126-b7a5-38603e665928"

        Assert.assertEquals(correctString, IdChecker.checkAndReturnPlayerUuid(correctString))
        Assert.assertEquals(
            "c872b8e0-f274-47d4-b761-ce684c5d224c",
            IdChecker.checkAndReturnGameUuid("c872b8e0-f274-47d4-b761-ce684c5d224c")
        )

        Assert.assertEquals(correctString, IdChecker.checkAndReturnGameUuid("G-$correctString-2"))
        Assert.assertEquals(correctString, IdChecker.checkAndReturnPlayerUuid("P-$correctString-2"))
    }

    @Test // too short
    fun testIdFailure1() {
        Assert.assertNull(IdChecker.checkAndReturnGameUuid("2ddb3a34-0699-4126-b7a5-38603e66592"))
    }

    @Test // wrong prefix
    fun testIdFailure2() {
        Assert.assertNull(IdChecker.checkAndReturnGameUuid("P-2ddb3a34-0699-4126-b7a5-38603e665928-2"))
    }

    @Test // wrong prefix
    fun testIdFailure3() {
        Assert.assertNull(IdChecker.checkAndReturnPlayerUuid("G-2ddb3a34-0699-4126-b7a5-38603e665928-2"))
    }

    @Test // changed checkDigit
    fun testIdFailure4() {
        Assert.assertNull(IdChecker.checkAndReturnGameUuid("G-2ddb3a34-0699-4126-b7a5-38603e665928-3"))
    }

    @Test // changed uuid without changing checkdigit
    fun testIdFailure5() {
        Assert.assertNull(IdChecker.checkAndReturnGameUuid("G-2ddb3a34-0699-4126-b7a5-48603e665928-2"))
    }
}
