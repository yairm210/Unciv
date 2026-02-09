@file:Suppress("FunctionOnlyReturningConstant")

package com.unciv.logic.github

import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.UncivShowableException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse

enum class DownloadAndExtractState {
    Downloading {
        override fun message(progress: Int?) = if (progress == null) "Downloading..." else "{Downloading...} ${progress.coerceIn(0, 100)}%"
    },
    Finishing {
        override fun message(progress: Int?) = "Finishing..."
    };

    abstract fun message(progress: Int?): String
}

@Suppress("PropertyName")
object GithubAPI {
    const val baseUrl = "https://api.github.com"
    const val bearerToken = ""

    suspend fun request(
        maxRateLimitedRetries: Int = 3,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        throw UncivShowableException("Online mod downloads are disabled on web phase 1")
    }

    suspend fun fetchGithubReposWithTopic(search: String, page: Int, amountPerPage: Int): HttpResponse {
        throw UncivShowableException("Online mod downloads are disabled on web phase 1")
    }

    suspend fun fetchGithubTopics(): HttpResponse {
        throw UncivShowableException("Online mod downloads are disabled on web phase 1")
    }

    suspend fun fetchSingleRepo(owner: String, repoName: String): HttpResponse {
        throw UncivShowableException("Online mod downloads are disabled on web phase 1")
    }

    suspend fun fetchSingleRepoOwner(owner: String): HttpResponse {
        throw UncivShowableException("Online mod downloads are disabled on web phase 1")
    }

    suspend fun fetchPreviewImageOrNull(modUrl: String, branch: String, ext: String): HttpResponse? {
        return null
    }

    class RepoSearch {
        var total_count = 0
        var incomplete_results = false
        var items = ArrayList<Repo>()
    }

    class Repo {
        var hasUpdatedSize = false
        var direct_zip_url = ""
        var release_tag = ""

        var name = ""
        var full_name = ""
        var description: String? = null
        var owner = RepoOwner()
        var stargazers_count = 0
        var default_branch = ""
        var html_url = ""
        var pushed_at = ""
        var size = 0
        var topics = mutableListOf<String>()

        override fun toString() = name.ifEmpty { direct_zip_url }

        companion object {
            suspend fun parseUrl(url: String): Repo? = Repo().parseUrl(url)

            suspend fun query(owner: String, repoName: String): Repo? {
                return null
            }
        }
    }

    class RepoOwner {
        var login = ""
        var avatar_url: String? = null

        companion object {
            suspend fun query(owner: String): RepoOwner? {
                return null
            }
        }
    }

    class TopicSearchResponse {
        var items = ArrayList<Topic>()

        class Topic {
            var name = ""
            var display_name: String? = null
            var created_at = ""
            var updated_at = ""
        }
    }

    internal class Tree {
        class TreeFile {
            var size: Long = 0L
        }

        var tree = ArrayList<TreeFile>()
        var truncated = false
    }

    private suspend fun Repo.parseUrl(url: String): Repo? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        val normalized = url.removeSuffix("/")
        val path = normalized
            .substringAfter("://")
            .substringAfter("/", "")
            .split('/')
            .filter { it.isNotBlank() }

        if (normalized.contains("github.com") && path.size >= 2) {
            owner.login = path[0]
            name = path[1].removeSuffix(".git")
            full_name = "${owner.login}/$name"
            html_url = "https://github.com/$full_name"
            default_branch = "master"
            return this
        }

        html_url = ""
        direct_zip_url = normalized
        owner.login = "-unknown-"
        default_branch = "master"
        name = normalized.substringAfterLast('/').removeSuffix(".zip")
        full_name = name
        return this
    }

    internal suspend fun Repo.fetchReleaseZip(): HttpResponse {
        throw UncivShowableException("Online mod downloads are disabled on web phase 1")
    }

    suspend fun Repo.downloadAndExtract(
        updateProgressPercent: ((DownloadAndExtractState, Int?) -> Unit)? = null,
    ): FileHandle? {
        return null
    }
}
