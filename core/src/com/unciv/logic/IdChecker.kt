package com.unciv.logic

import yairm210.purity.annotations.Pure
import java.util.Locale
import kotlin.math.abs

/**
 * This class checks whether a Game- or Player-ID matches the old or new format.
 * If old format is used, checks are skipped and input is returned.
 * If new format is detected, prefix and checkDigit are checked and UUID returned.
 *
 * All input is returned trimmed.
 *
 * New format:
 * G-UUID-CheckDigit for Game IDs
 * P-UUID-CheckDigit for Player IDs
 *
 * Example:
 * 2ddb3a34-0699-4126-b7a5-38603e665928
 * Same ID in proposed new Player-ID format:
 * P-2ddb3a34-0699-4126-b7a5-38603e665928-5
 * Same ID in proposed new Game-ID format:
 * G-2ddb3a34-0699-4126-b7a5-38603e665928-5
 */
object IdChecker {

    @Pure
    fun checkAndReturnPlayerUuid(playerId: String): String? {
        return checkAndReturnUuiId(playerId, "P")
    }

    @Pure
    fun checkAndReturnGameUuid(gameId: String): String? {
        return checkAndReturnUuiId(gameId, "G")
    }

    @Pure
    private fun checkAndReturnUuiId(id: String, prefix: String): String? {
        val trimmedPlayerId = id.trim()
        if (trimmedPlayerId.length == 40) { // length of a UUID (36) with pre- and postfix
            if (!trimmedPlayerId.startsWith(prefix, true))
                return null

            val checkDigit = trimmedPlayerId.substring(trimmedPlayerId.lastIndex, trimmedPlayerId.lastIndex + 1)
            // remember, the format is: P-9e37e983-a676-4ecc-800e-ef8ec721a9b9-5
            val shortenedPlayerId = trimmedPlayerId.substring(2, 38)
            val calculatedCheckDigit = getCheckDigit(shortenedPlayerId)
            if (calculatedCheckDigit == null || calculatedCheckDigit.toString() != checkDigit)
                return null

            return shortenedPlayerId
        } else if (trimmedPlayerId.length == 36) {
            return trimmedPlayerId
        }
        return null
    }


    /**
     * Adapted from https://wiki.openmrs.org/display/docs/Check+Digit+Algorithm
     */
    @Pure
    fun getCheckDigit(uuid: String): Int? {
        // allowable characters within identifier
        @Suppress("SpellCheckingInspection")
        val validChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVYWXZ-"
        var idWithoutCheckdigit = uuid
        // remove leading or trailing whitespace, convert to uppercase
        idWithoutCheckdigit = idWithoutCheckdigit.trim().uppercase(Locale.ENGLISH)

        // this will be a running total
        var sum = 0

        // loop through digits from right to left
        idWithoutCheckdigit.indices.forEach { i ->
            //set ch to "current" character to be processed
            val ch = idWithoutCheckdigit[idWithoutCheckdigit.length - i - 1]

            // throw exception for invalid characters
            if(!validChars.contains(ch)) return null

            // our "digit" is calculated using ASCII value - 48
            val digit = ch.code - 48

            // weight will be the current digit's contribution to
            // the running total
            val weight: Int
            if (i % 2 == 0) {

                // for alternating digits starting with the rightmost, we
                // use our formula this is the same as multiplying x 2 and
                // adding digits together for values 0 to 9.  Using the
                // following formula allows us to gracefully calculate a
                // weight for non-numeric "digits" as well (from their
                // ASCII value - 48).
                weight = (2 * digit) - (digit / 5) * 9

            } else {

                // even-positioned digits just contribute their ascii
                // value minus 48
                weight = digit

            }
            // keep a running total of weights
            sum += weight
        }
        // avoid sum less than 10 (if characters below "0" allowed,
        // this could happen)
        sum = abs(sum) + 10

        // check digit is amount needed to reach next number
        // divisible by ten
        return (10 - (sum % 10)) % 10
    }
}
