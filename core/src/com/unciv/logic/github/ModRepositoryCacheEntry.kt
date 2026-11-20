package com.unciv.logic.github

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

class ModRepositoryCacheEntry {
    var repository = ""
    var checkedAt = ""
    var repo = GithubAPI.Repo()

    fun isFresh(now: Instant, maxAge: Duration): Boolean {
        val checkedAtInstant = try {
            Instant.parse(checkedAt)
        } catch (_: DateTimeParseException) {
            return false
        }
        val age = Duration.between(checkedAtInstant, now)
        return !age.isNegative && age < maxAge
    }
}
