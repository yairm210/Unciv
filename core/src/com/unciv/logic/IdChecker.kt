package com.unciv.logic

import com.unciv.logic.multiplayer.FriendList.Friend
import com.unciv.utils.Log
import com.unciv.utils.isUUID
import com.unciv.utils.softRequire
import io.ktor.http.URLParserException
import io.ktor.http.Url
import yairm210.purity.annotations.LocalState
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
    const val uncivUrlPrefix = "https://yairm210.github.io/Unciv/"

    @Pure
    fun isGameDeepLink(url: String) = url.startsWith(uncivUrlPrefix + "G/", ignoreCase = true)
    @Pure
    fun isFriendDeepLink(url: String) = url.startsWith(uncivUrlPrefix + "P/", ignoreCase = true)
    
    @Pure
    fun checkAndReturnPlayerUuid(playerId: String): Friend? {
        return checkAndReturnId(playerId, "P")
    }

    @Pure
    fun checkAndReturnUuiId(gameId: String): String? {
        return checkAndReturnId(gameId, "G")?.playerID
    }
    
    @Pure
    private fun checkAndReturnId(id: String, prefix: String): Friend? {
        val trimmedId = id.trim(spaceOrWrapperPredicate)
        return if (trimmedId.startsWith(uncivUrlPrefix, ignoreCase = true))
            checkAndReturnIdFromUrl(trimmedId, prefix)
        else checkAndReturnUuiId(trimmedId, prefix)?.let { Friend("", it) }
    }

    // parses urls like "http://unciv.app/G/G-2ddb3a34-0699-4126-b7a5-38603e665928-2"
    // or "http://unciv.app/P-2ddb3a34-0699-4126-b7a5-38603e665928-2?name=%C4%90%E1%BA%B7ng"
    @Pure
    private fun checkAndReturnIdFromUrl(id: String, prefix: String): Friend? {
        @LocalState
        val url = try {
            Url(id)
        } catch (e: URLParserException) {
            Log.error("invalid format for url $id", e)
            return null
        }
        @LocalState
        val segments = url.segments
        @LocalState
        val parameters = url.parameters
        softRequire(segments.size==3, "url %s should be in \"Unciv\\G\" path", id) ?: return null
        softRequire(segments[0].equals("Unciv", ignoreCase=true), "%s url %s is not an \"Unciv\" link", id, segments[0]) ?: return null
        softRequire(segments[1].equals(prefix, ignoreCase=true), "%s url %s has incorrect prefix %s", prefix, id, segments[0]) ?: return null
        val gameId = checkAndReturnUuiId(segments[2], prefix, id) ?: return null
        val name = parameters["name"] ?: ""
        return Friend(name, gameId)
    }

    @Pure
    private fun checkAndReturnUuiId(id: String, prefix: String, url:String = id): String? {
        val trimmedPlayerId = id.trim(spaceOrWrapperPredicate)
        if (trimmedPlayerId.length == 40) { // length of a UUID (36) with pre- and postfix
            if (!trimmedPlayerId.startsWith(prefix, true)) {
                Log.error("$prefix uuid $url has incorrect prefix")
                return null
            }

            val checkDigit = trimmedPlayerId.substring(trimmedPlayerId.lastIndex, trimmedPlayerId.lastIndex + 1)
            // remember, the format is: P-9e37e983-a676-4ecc-800e-ef8ec721a9b9-5
            val shortenedPlayerId = trimmedPlayerId.substring(2, 38)
            val calculatedCheckDigit = getCheckDigit(shortenedPlayerId)
            if (calculatedCheckDigit == null || calculatedCheckDigit.toString() != checkDigit) {
                Log.error("$prefix uuid $url has incorrect checkDigit $checkDigit")
                return null
            }

            if (!shortenedPlayerId.isUUID()) {
                Log.error("invalid uuid $url")
                return null
            }
            return shortenedPlayerId
        } else if (trimmedPlayerId.length == 36) {
            if (!trimmedPlayerId.isUUID()) {
                Log.error("invalid uuid $url")
                return null
            }
            return trimmedPlayerId
        }
        Log.error("$prefix uuid $url has incorrect length")
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
    
    private val spaceOrWrapperPredicate: (Char)->Boolean = {
        // trim spaces, parenthesis, quotation marks, periods, commas, newlines, etc.
        // We do NOT drop hyphens or connectors.
        when (Character.getType(it).toByte()) {
            Character.SPACE_SEPARATOR,
            Character.START_PUNCTUATION,
            Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION,
            Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION,
            Character.CONTROL -> true
            else -> false
        }
    }
}
