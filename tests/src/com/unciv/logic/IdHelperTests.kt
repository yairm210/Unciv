package com.unciv.logic

import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class IdHelperTests {
    @Test
    fun getCheckDigit_uuid_tests() {
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
    fun checkAndReturnUuiId_uuid_valid_success() {
        val correctString = "2ddb3a34-0699-4126-b7a5-38603e665928"

        Assert.assertEquals(correctString, IdChecker.checkAndReturnPlayerUuid(correctString)?.playerID)
        Assert.assertEquals(
            "c872b8e0-f274-47d4-b761-ce684c5d224c",
            IdChecker.checkAndReturnUuiId("c872b8e0-f274-47d4-b761-ce684c5d224c")
        )

        Assert.assertEquals(correctString, IdChecker.checkAndReturnUuiId("G-$correctString-2"))
        Assert.assertEquals(correctString, IdChecker.checkAndReturnPlayerUuid("P-$correctString-2")?.playerID)
    }

    @Test
    fun checkAndReturnUuiId_gameId_tooShort_isNull() {
        Assert.assertNull(IdChecker.checkAndReturnUuiId("2ddb3a34-0699-4126-b7a5-38603e66592"))
    }

    @Test
    fun checkAndReturnUuiId_gameId_wrongPrefix_isNull() {
        Assert.assertNull(IdChecker.checkAndReturnUuiId("P-2ddb3a34-0699-4126-b7a5-38603e665928-2"))
    }

    @Test 
    fun checkAndReturnUuiId_playerId_wrongPrefix_isNull() {
        Assert.assertNull(IdChecker.checkAndReturnPlayerUuid("G-2ddb3a34-0699-4126-b7a5-38603e665928-2")?.playerID)
    }

    @Test
    fun checkAndReturnUuiId_gameId_changedCheckDigit_isNull() {
        Assert.assertNull(IdChecker.checkAndReturnUuiId("G-2ddb3a34-0699-4126-b7a5-38603e665928-3"))
    }

    @Test
    fun checkAndReturnUuiId_gameId_changedUuid_isNull() {
        Assert.assertNull(IdChecker.checkAndReturnUuiId("G-2ddb3a34-0699-4126-b7a5-48603e665928-2"))
    }

    @Test
    fun checkAndReturnUuiId_gameId_withPunctuation_succeeds() {
        val result = IdChecker.checkAndReturnUuiId(" (“G-2ddb3a34-0699-4126-b7a5-38603e665928-2”.)\n")
        Assert.assertEquals("2ddb3a34-0699-4126-b7a5-38603e665928", result)
    }

    @Test
    fun checkAndReturnUuiId_url_valid_success() {
        val correctString = "2ddb3a34-0699-4126-b7a5-38603e665928"

        Assert.assertEquals(correctString, IdChecker.checkAndReturnPlayerUuid(correctString)?.playerID)
        Assert.assertEquals(
            "c872b8e0-f274-47d4-b761-ce684c5d224c",
            IdChecker.checkAndReturnUuiId("c872b8e0-f274-47d4-b761-ce684c5d224c")
        )

        Assert.assertEquals(correctString, IdChecker.checkAndReturnUuiId("https://yairm210.github.io/Unciv/G/G-$correctString-2"))
        Assert.assertEquals(correctString, IdChecker.checkAndReturnPlayerUuid("https://yairm210.github.io/Unciv/P/P-$correctString-2")?.playerID)
    }

    @Test
    fun checkAndReturnUuiId_url_tooShort_isNull() {
        Assert.assertNull(IdChecker.checkAndReturnUuiId("https://yairm210.github.io/Unciv/G/2ddb3a34-0699-4126-b7a5-38603e66592"))
    }

    @Test 
    fun checkAndReturnUuiId_gameUrl_wrongPrefix_isNull() {
        Assert.assertNull(IdChecker.checkAndReturnUuiId("https://yairm210.github.io/Unciv/P/P-2ddb3a34-0699-4126-b7a5-38603e665928-2"))
    }

    @Test
    fun checkAndReturnUuiId_playerUrl_wrongPrefix_isNull() {
        Assert.assertNull(IdChecker.checkAndReturnPlayerUuid("https://yairm210.github.io/Unciv/G/G-2ddb3a34-0699-4126-b7a5-38603e665928-2")?.playerID)
    }

    @Test 
    fun checkAndReturnUuiId_url_changedCheckDigit_isNull() {
        Assert.assertNull(IdChecker.checkAndReturnUuiId("https://yairm210.github.io/Unciv/G/G-2ddb3a34-0699-4126-b7a5-38603e665928-3"))
    }

    @Test // changed uuid without changing checkdigit
    fun checkAndReturnUuiId_url_changedUuid_isNull() {
        Assert.assertNull(IdChecker.checkAndReturnUuiId("https://yairm210.github.io/Unciv/G/G-2ddb3a34-0699-4126-b7a5-48603e665928-2"))
    }

    @Test
    fun checkAndReturnUuiId_url_withPunctuation_succeeds() {
        val result = IdChecker.checkAndReturnUuiId(" (“https://yairm210.github.io/Unciv/G/G-2ddb3a34-0699-4126-b7a5-38603e665928-2”.)\n")
        Assert.assertEquals("2ddb3a34-0699-4126-b7a5-38603e665928", result)
    }

    @Test
    fun checkAndReturnUuiId_url_withName_succeeds() {
        val result = IdChecker.checkAndReturnPlayerUuid(" (“https://yairm210.github.io/Unciv/P/P-2ddb3a34-0699-4126-b7a5-38603e665928-2?name=%C4%90%E1%BA%B7ng”.)\n")
        Assert.assertEquals("2ddb3a34-0699-4126-b7a5-38603e665928", result?.playerID)
        Assert.assertEquals("Đặng", result?.name)
    }
}
