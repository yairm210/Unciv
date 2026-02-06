package com.unciv.logic.github

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap

object Github {
    suspend fun tryGetGithubReposWithTopic(
        page: Int,
        amountPerPage: Int,
        searchRequest: String = "",
    ): GithubAPI.RepoSearch? {
        return null
    }

    suspend fun getPreviewImageOrNull(
        modUrl: String,
        defaultBranch: String,
        avatarUrl: String?,
    ): Pixmap? {
        return null
    }

    suspend fun getRepoSize(repo: GithubAPI.Repo): Int {
        return -1
    }

    suspend fun tryGetGithubTopics(): GithubAPI.TopicSearchResponse? {
        return null
    }

    fun rewriteModOptions(repo: GithubAPI.Repo, modFolder: FileHandle) {
        // No-op on web phase 1.
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
