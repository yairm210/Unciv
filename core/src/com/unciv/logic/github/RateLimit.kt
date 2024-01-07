package com.unciv.logic.github

import com.unciv.utils.debug
import java.net.HttpURLConnection

/**
 * Implements the ability wo work with GitHub's rate limit, recognize blocks from previous attempts, wait and retry.
 * @see <a href="https://docs.github.com/en/rest/reference/search#rate-limit">Github API doc</a>
 */
object RateLimit {
    private const val maxRequestsPerInterval = 10
    private const val intervalInMilliSeconds = 60000L
    private const val maxWaitLoop = 3

    private var account = 0         // used requests
    private var firstRequest = 0L   // timestamp window start (java epoch millisecond)

    /*
        Github rate limits do not use sliding windows - you (if anonymous) get one window
        which starts with the first request (if a window is not already active)
        and ends 60s later, and a budget of 10 requests in that window. Once it expires,
        everything is forgotten and the process starts from scratch
     */

    private val millis: Long
        get() = System.currentTimeMillis()

    /** calculate required wait in ms
     * @return Estimated number of milliseconds to wait for the rate limit window to expire
     */
    private fun getWaitLength()
            = (firstRequest + intervalInMilliSeconds - millis)

    /** Maintain and check a rate-limit
     *  @return **true** if rate-limited, **false** if another request is allowed
     */
    private fun isLimitReached(): Boolean {
        val now = millis
        val elapsed = if (firstRequest == 0L) intervalInMilliSeconds else now - firstRequest
        if (elapsed >= intervalInMilliSeconds) {
            firstRequest = now
            account = 1
            return false
        }
        if (account >= maxRequestsPerInterval) return true
        account++
        return false
    }

    /** If rate limit in effect, sleep long enough to allow next request.
     *
     *  @return **true** if waiting did not clear isLimitReached() (can only happen if the clock is broken),
     *                  or the wait has been interrupted by Thread.interrupt()
     *          **false** if we were below the limit or slept long enough to drop out of it.
     */
    fun waitForLimit(): Boolean {
        var loopCount = 0
        while (isLimitReached()) {
            val waitLength = getWaitLength()
            try {
                Thread.sleep(waitLength)
            } catch ( ex: InterruptedException ) {
                return true
            }
            if (++loopCount >= maxWaitLoop) return true
        }
        return false
    }

    /** http responses should be passed to this so the actual rate limit window can be evaluated and used.
     *  The very first response and all 403 ones are good candidates if they can be expected to contain GitHub's rate limit headers.
     *
     *  @see <a href="https://docs.github.com/en/rest/overview/resources-in-the-rest-api#rate-limiting">Github API doc</a>
     */
    fun notifyHttpResponse(response: HttpURLConnection) {
        if (response.responseMessage != "rate limit exceeded" && response.responseCode != 200) return

        fun getHeaderLong(name: String, default: Long = 0L) =
            response.headerFields[name]?.get(0)?.toLongOrNull() ?: default
        val limit = getHeaderLong("X-RateLimit-Limit", maxRequestsPerInterval.toLong()).toInt()
        val remaining = getHeaderLong("X-RateLimit-Remaining").toInt()
        val reset = getHeaderLong("X-RateLimit-Reset")

        if (limit != maxRequestsPerInterval)
            debug("GitHub API Limit reported via http (%s) not equal assumed value (%s)", limit, maxRequestsPerInterval)
        account = maxRequestsPerInterval - remaining
        if (reset == 0L) return
        firstRequest = (reset + 1L) * 1000L - intervalInMilliSeconds
    }
}
