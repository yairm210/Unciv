package com.unciv.testing

import java.io.IOException

/** Helper to query git properties.
 *  - Works by looking for Github runner environment variables and/or running a git process.
 *  - Any failure to do so results in these properties being `null`.
 *  @property currentBranch
 *  @property currentCommit
 *  @property describe
 */
@Suppress("ConstPropertyName")
object GitInfo {
    private const val runnerBranch = "GITHUB_HEAD_REF"
    private const val runnerCommit = "GITHUB_SHA"
    private const val runnerPRNumber = "GITHUB_REF_NAME"

    /** Returns the currently active branch name, if it can be determined */
    val currentBranch by lazy {
        System.getenv(runnerBranch) ?:
        runGit("rev-parse", "--abbrev-ref", "HEAD").takeUnless { it == "HEAD" } // don't show literal "HEAD" for a detached head.
    }

    /** Returns the currently active commit hash, if it can be determined (full hash) */
    val currentCommit by lazy {
        System.getenv(runnerCommit) ?:
        runGit("rev-parse", "HEAD")
    }

    /** A parsed `git describe` output
     *  @property result       The full result of the `git describe --always --tags --long --dirty --broken` command
     *  @property tag          The most recent tag the current branch is based on
     *  @property commitsAhead How many commits the current branch is ahead of [tag] 
     *  @property commitAbbrev The current commit abbreviation (shortest form that remains unique, at least 7 hex digits)
     *  @property isDirty      Whether there are local changes on top of the last commit
     *  @property isBroken     Whether "a repository is corrupt and Git cannot determine if there is local modification"
     */
    data class GitDescription(
        val result: String,
        val tag: String,
        val commitsAhead: Int,
        val commitAbbrev: String,
        val isDirty: Boolean,
        val isBroken: Boolean
    )

    /** Returns git's description of the current code's position relative to the working tree.
     *  @see GitDescription
     */
    val describe by lazy {
        runGit("describe", "--always", "--tags", "--long", "--dirty", "--broken").parseDescription()
    }

    private fun String?.parseDescription(): GitDescription? {
        if (this == null) return null
        var working = this.trimEnd(' ', '\n', '\r')
        fun detect(suffix: String): Boolean {
            if (!working.endsWith(suffix)) return false
            working = working.removeSuffix(suffix)
            return true
        }
        fun commit(): String? {
            val match = Regex("-g([a-f0-9]{7,40})$").find(working) ?: return null
            working = working.removeSuffix(match.value)
            return match.groupValues[1]
        }
        fun ahead(): Int? {
            val match = Regex("-([0-9]{1,10})$").find(working) ?: return null
            working = working.removeSuffix(match.value)
            return match.groupValues[1].toInt()
        }
        val result = working
        val broken = detect("-broken")
        val dirty = detect("-dirty")
        val commit = commit() ?: System.getenv(runnerCommit) ?: ""
        val ahead = ahead() ?: 0
        val tag = working.ifBlank { "#" + System.getenv(runnerPRNumber) }
        return GitDescription(result, tag, ahead, commit, dirty, broken)
    }

    private fun runGit(vararg args: String): String? = try {
        val process = ProcessBuilder("git", *args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) output else null
    } catch (ex: IOException) {
        println("Exception running git ${args.joinToString(" ")}: ${ex::class.simpleName} ${ex.message}")
        null
    }
}
