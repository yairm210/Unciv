package com.unciv.logic.github

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.updateDeprecations
import com.unciv.models.ruleset.ModOptions
import com.unciv.logic.web.WebHttp
import com.unciv.logic.github.GithubAPI.fetchReleaseZip

object Github {
    suspend fun tryGetGithubReposWithTopic(
        page: Int,
        amountPerPage: Int,
        searchRequest: String = "",
    ): GithubAPI.RepoSearch? {
        val resp = GithubAPI.fetchGithubReposWithTopic(searchRequest, page, amountPerPage)
        if (resp.ok) {
            val text = resp.text.orEmpty()
            try {
                return json().fromJson(GithubAPI.RepoSearch::class.java, text)
            } catch (_: Throwable) {
                throw Exception("Failed to parse Github response as json - $text")
            }
        }
        return null
    }

    suspend fun getPreviewImageOrNull(
        modUrl: String,
        defaultBranch: String,
        avatarUrl: String?,
    ): Pixmap? {
        try {
            val responses = listOfNotNull(
                GithubAPI.fetchPreviewImageOrNull(modUrl, defaultBranch, "jpg"),
                GithubAPI.fetchPreviewImageOrNull(modUrl, defaultBranch, "png"),
                avatarUrl?.let { WebHttp.requestBytes("GET", it) },
            )
            val resp = responses.firstOrNull { it.ok && it.bytes != null } ?: return null
            val bytes = resp.bytes ?: return null
            return Pixmap(bytes, 0, bytes.size)
        } catch (_: Throwable) {
            return null
        }
    }

    suspend fun getRepoSize(repo: GithubAPI.Repo): Int {
        val resp = repo.fetchReleaseZip()
        if (resp.ok && !resp.text.isNullOrBlank()) {
            val tree = json().fromJson(GithubAPI.Tree::class.java, resp.text)
            if (tree.truncated) return -1
            var totalSizeBytes = 0L
            for (file in tree.tree) totalSizeBytes += file.size
            return ((totalSizeBytes + 512) / 1024).toInt()
        }
        return -1
    }

    suspend fun tryGetGithubTopics(): GithubAPI.TopicSearchResponse? {
        val resp = GithubAPI.fetchGithubTopics()
        if (resp.ok && !resp.text.isNullOrBlank()) {
            return json().fromJson(GithubAPI.TopicSearchResponse::class.java, resp.text)
        }
        return null
    }

    fun rewriteModOptions(repo: GithubAPI.Repo, modFolder: FileHandle) {
        val modOptionsFile = modFolder.child("jsons/ModOptions.json")
        val modOptions = if (modOptionsFile.exists()) json().fromJsonFile(ModOptions::class.java, modOptionsFile) else ModOptions()

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

    fun String.repoNameToFolderName(onlyOuterBlanks: Boolean = false): String {
        var result = if (onlyOuterBlanks) this else replace('-', ' ')
        if (result.endsWith(' ')) result = result.trimEnd() + outerBlankReplacement
        if (result.startsWith(' ')) result = outerBlankReplacement + result.trimStart()
        return result
    }

    fun String.folderNameToRepoName(): String {
        var result = replace(' ', '-')
        if (result.endsWith(outerBlankReplacement)) result = result.trimEnd(outerBlankReplacement) + '-'
        if (result.startsWith(outerBlankReplacement)) result = '-' + result.trimStart(outerBlankReplacement)
        return result
    }
}
