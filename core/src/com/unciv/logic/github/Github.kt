package com.unciv.logic.github

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.updateDeprecations
import com.unciv.logic.UncivKtor
import com.unciv.logic.github.Github.CountingInputStream.Companion.create
import com.unciv.logic.github.Github.repoNameToFolderName
import com.unciv.logic.github.GithubAPI.fetchReleaseZip
import com.unciv.models.ruleset.ModOptions
import com.unciv.utils.Concurrency
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.nio.ByteBuffer


/**
 * Utility managing Github access (except the link in WorldScreenCommunityPopup)
 */
object Github {
    /** Helper implementing minimum viable bytes-read tracking with a callback to display a progress percentage.
     *  Pass-through if either the callback is null or the content length is not known beforehand.
     *
     *  This works as follows: [create] creates a new instance that wraps the input stream and counts within its `read()` override.
     *  Before this is returned, a tracker coroutine is launched that sends the progress percentace to the callback every 100ms.
     *  The tracker stops either once 100% is reported or the stream is closed.
     *  In other words, the 'driving force' is the client reading the stream.
     *
     *  @constructor Private, call [CountingInputStream.create] instead.
     */
    private class CountingInputStream private constructor(
        private val wrapped: InputStream,
        private val contentLength: Int,
        private val updateProgressPercent: (Int)->Unit
    ): InputStream() {
        private var count = 0
        private var trackerThread: Job? = null

        companion object {
            /**
             *  @param originalStream The data to stream while tracking progress
             *  @param contentLength The length of the conten in bytes needs to be known beforehand
             *  @param updateProgressPercent The callback to notify with percentages
             *  @return [originalStream] if either [contentLength] isn't positive or [updateProgressPercent] is `null`, otherwise a new [CountingInputStream]
             *          with tracking already started.
             */
            fun create(originalStream: InputStream, contentLength: Int, updateProgressPercent: ((Int)->Unit)?): InputStream {
                if (updateProgressPercent == null || contentLength <= 0) return originalStream
                val newStream = CountingInputStream(originalStream, contentLength, updateProgressPercent)
                newStream.startTracking()
                return newStream
            }
        }

        override fun read(): Int {
            count++
            return wrapped.read()
        }

        override fun close() {
            stopTracking()
            super.close()
        }

        fun bytesRead() = count

        private fun startTracking() {
            trackerThread = Concurrency.run("Downloading mod progress") {
                while (this.isActive) {
                    val percentage = (bytesRead() * 100L / contentLength).toInt()
                    updateProgressPercent(percentage.coerceIn(0, 100))
                    if (percentage >= 100) {
                        stopTracking()
                        break
                    }
                    delay(100)
                }
            }
        }

        private fun stopTracking() {
            trackerThread?.cancel()
            trackerThread = null
        }
    }

    /**
     * Query GitHub for repositories marked "unciv-mod"
     * @param amountPerPage Number of search results to return for this request.
     * @param page          The "page" number, starting at 1.
     * @return              Parsed [RepoSearch][GithubAPI.RepoSearch] json on success, `null` on failure.
     * @see <a href="https://docs.github.com/en/rest/reference/search#search-repositories">Github API doc</a>
     */
    suspend fun tryGetGithubReposWithTopic(
        page: Int, amountPerPage: Int, searchRequest: String = ""
    ): GithubAPI.RepoSearch? {
        val resp = GithubAPI.fetchGithubReposWithTopic(searchRequest, page, amountPerPage)
        if (resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            try {
                return json().fromJson(GithubAPI.RepoSearch::class.java, text)
            } catch (_: Throwable) {
                throw Exception("Failed to parse Github response as json - $text")
            }
        }
        return null
    }

    /** Get a Pixmap from a "preview" png or jpg file at the root of the repo, falling back to the
     *  repo owner's avatar [avatarUrl]. The file content url is constructed from [modUrl] and [defaultBranch]
     *  by replacing the host with `raw.githubusercontent.com`.
     *  
     *  @return [Pixmap] on success or `null` on failure
     */
    suspend fun getPreviewImageOrNull(modUrl: String, defaultBranch: String, avatarUrl: String?): Pixmap? {
        // Side note: github repos also have a "Social Preview" optionally assignable on the repo's
        // settings page, but that info is inaccessible using the v3 API anonymously. The easiest way
        // to get it would be to query the the repo's frontend page (modUrl), and parse out
        // `head/meta[property=og:image]/@content`, which is one extra spurious roundtrip and a
        // non-trivial waste of bandwidth.
        // Thus we ask for a "preview" file as part of the repo contents instead.
        try {
            val resp = runBlocking {
                /**
                 * Note: avatar urls look like: https://avatars.githubusercontent.com/u/<number>?v=4
                 * So the image format is only recognizable from the response "Content-Type" header
                 * or by looking for magic markers in the bits - which the Pixmap constructor below does.
                 */
                return@runBlocking listOf(
                    async { GithubAPI.fetchPreviewImageOrNull(modUrl, defaultBranch, "jpg") },
                    async { GithubAPI.fetchPreviewImageOrNull(modUrl, defaultBranch, "png") },
                    async { avatarUrl?.let { UncivKtor.getOrNull(it) } }
                ).awaitAll()
                    // automatically falls back to last succeeding response
                    .firstNotNullOf { it }
            }

            val byteArray = resp.bodyAsBytes()
            val buffer = ByteBuffer.allocateDirect(byteArray.size).put(byteArray).position(0)
            return Pixmap(buffer)
        } catch (_: Throwable) {
            return null
        }
    }

    /** Queries github for a tree and calculates the sum of the blob sizes.
     *  @return -1 on failure, else size rounded to kB
     *  @see <a href="https://docs.github.com/en/rest/git/trees#get-a-tree">Github API "Get a tree"</a>
     */
    suspend fun getRepoSize(repo: GithubAPI.Repo): Int {
        val resp = repo.fetchReleaseZip()

        if (resp.status.isSuccess()) {
            val tree = json().fromJson(GithubAPI.Tree::class.java, resp.bodyAsText())
            if (tree.truncated) return -1  // unlikely: >100k blobs or blob > 7MB

            var totalSizeBytes = 0L
            for (file in tree.tree)
                totalSizeBytes += file.size

            // overflow unlikely: >2TB
            return ((totalSizeBytes + 512) / 1024).toInt()
        }

        return -1
    }

    /**
     * Query GitHub for topics named "unciv-mod*"
     * @return Parsed [TopicSearchResponse][GithubAPI.TopicSearchResponse] json on success, `null` on failure.
     */
    suspend fun tryGetGithubTopics(): GithubAPI.TopicSearchResponse? {
        val resp = GithubAPI.fetchGithubTopics()
        if (resp.status.isSuccess()) {
            return json().fromJson(GithubAPI.TopicSearchResponse::class.java, resp.bodyAsText())
        }
        return null
    }

    /** Rewrite modOptions file for a mod we just installed to include metadata we got from the GitHub api
     *
     *  (called on background thread)
     */
    fun rewriteModOptions(repo: GithubAPI.Repo, modFolder: FileHandle) {
        /**
         *  - On `ModManagementScreen`, when the user selects a local mod without preview,
         *    fallback code to display the owner's avatar would be cumbersome
         *  - Therefore, save the avatar after download, so local mods always have a preview as long as the avatar could be fetched
         *  - Using the **cached** version of the image failed miserably - the pixmap inside the texture is already disposed
         *  - Compromise: Download _again_
         *  - Do it here so it works for `LoadOrSaveScreen.loadMissingMods` too
         */
        if (!modFolder.child("preview.jpg").exists() && !modFolder.child("preview.png").exists())
            trySaveAvatarAsPreview(modFolder, repo)

        val modOptionsFile = modFolder.child("jsons/ModOptions.json")
        val modOptions = if (modOptionsFile.exists()) json().fromJsonFile(ModOptions::class.java, modOptionsFile) else ModOptions()

        // If this is false we didn't get github repo info, do a defensive merge so the Repo.parseUrl or download
        // code can decide defaults but leave any meaningful field of a zip-included ModOptions alone.
        val overwriteAlways = repo.direct_zip_url.isEmpty()

        if (overwriteAlways || modOptions.modUrl.isEmpty()) modOptions.modUrl = repo.html_url
        if (overwriteAlways || modOptions.defaultBranch == "master" && repo.default_branch.isNotEmpty())
            modOptions.defaultBranch = repo.default_branch
        if (overwriteAlways || modOptions.lastUpdated.isEmpty()) modOptions.lastUpdated = repo.pushed_at
        if (overwriteAlways || modOptions.author.isEmpty()) modOptions.author = repo.owner.login
        if (overwriteAlways || modOptions.modSize == 0) modOptions.modSize = repo.size
        if (overwriteAlways || modOptions.topics.isEmpty()) modOptions.topics = repo.topics

        modOptions.updateDeprecations()
        json().toJson(modOptions, modOptionsFile)
    }

    private const val outerBlankReplacement = '='
    // Github disallows **any** special chars and replaces them with '-' - so use something ascii the
    // OS accepts but still is recognizable as non-original, to avoid confusion

    /** Convert a [Repo][[GithubAPI.Repo] name to a local name for both display and folder name
     *
     *  Replaces '-' with blanks but ensures no leading or trailing blanks.
     *  As mad modders know no limits, trailing "-" did indeed happen, causing things to break due to trailing blanks on a folder name.
     *  As "test-" and "test" are different allowed repository names, trimmed blanks are replaced with one equals sign per side.
     *  @param onlyOuterBlanks If `true` ignores inner dashes - only start and end are treated. Useful when modders have manually created local folder names using dashes.
     */
    fun String.repoNameToFolderName(onlyOuterBlanks: Boolean = false): String {
        var result = if (onlyOuterBlanks) this else replace('-', ' ')
        if (result.endsWith(' ')) result = result.trimEnd() + outerBlankReplacement
        if (result.startsWith(' ')) result = outerBlankReplacement + result.trimStart()
        return result
    }

    /** Inverse of [repoNameToFolderName] */
    // As of this writing, only used for loadMissingMods
    fun String.folderNameToRepoName(): String {
        var result = replace(' ', '-')
        if (result.endsWith(outerBlankReplacement)) result = result.trimEnd(outerBlankReplacement) + '-'
        if (result.startsWith(outerBlankReplacement)) result = '-' + result.trimStart(outerBlankReplacement)
        return result
    }

    /** Get owner.avatar_url from [repo], replacing it with a predictable redirecting endpoint if needed.
     *  We get repo instances with owner.login known but no avatar_url e.g. when using the
     *  download directly from url feature for some url formats.
     */
    private fun tryGetMissingAvatar(repo: GithubAPI.Repo): String? {
        if (!repo.owner.avatar_url.isNullOrEmpty()) return repo.owner.avatar_url!!
        if (repo.owner.login.isEmpty()) return null
        // According to https://github.com/orgs/community/discussions/147297
        return "https://github.com/${repo.owner.login}.png"
        // This worked too: GithubAPI.RepoOwner.query(repo.owner.login)?.avatar_url
    }

    private fun trySaveAvatarAsPreview(modFolder: FileHandle, repo: GithubAPI.Repo) {
        val avatarUrl = tryGetMissingAvatar(repo) ?: return
        Concurrency.run("Save avatar") {
            val response = UncivKtor.getOrNull(avatarUrl) ?: return@run
            if (!response.status.isSuccess()) return@run
            // looking for 0xFFD8 or 0x89504E47 in the body bytes works too, but kotlin's embarrassing bytes & literals support makes that ugly
            val type = response.headers["Content-Type"] ?: return@run
            val extension = when(type) {
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                else -> return@run
            }
            val fileHandle = modFolder.child("preview.$extension")
            fileHandle.writeBytes(response.bodyAsBytes(), false)
        }
    }
}
