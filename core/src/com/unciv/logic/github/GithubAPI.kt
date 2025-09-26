package com.unciv.logic.github

import com.unciv.json.json
import com.unciv.logic.github.GithubAPI.parseUrl

/**
 *  "Namespace" collects all Github API structural knowledge
 *  - Response schema
 *  - Query URL builders
 *
 *  ### Collected doc links:
 *  - https://docs.github.com/en/repositories/working-with-files/using-files/downloading-source-code-archives#source-code-archive-urls
 *  - https://docs.github.com/en/rest/reference/search#search-repositories--code-samples
 *  - https://docs.github.com/en/rest/repos/repos
 *  - https://docs.github.com/en/rest/releases/releases
 *  - https://docs.github.com/en/rest/git/trees#get-a-tree
 */
@Suppress("PropertyName")  // We're declaring an external API schema
object GithubAPI {
    // region URL formatters

    /** Format a download URL for a branch archive */
    // URL format see: https://docs.github.com/en/repositories/working-with-files/using-files/downloading-source-code-archives#source-code-archive-urls
    // Note: https://api.github.com/repos/owner/mod/zipball would be an alternative. Its response is a redirect, but our lib follows that and delivers the zip just fine.
    // Problems with the latter: Internal zip structure different, finalDestinationName would need a patch. Plus, normal URL escaping for owner/reponame does not work.
    internal fun getUrlForBranchZip(gitRepoUrl: String, branch: String) = "$gitRepoUrl/archive/refs/heads/$branch.zip"

    /** Format URL to fetch one specific [Repo] metadata from the API */
    private fun getUrlForSingleRepoQuery(owner: String, repoName: String) = "https://api.github.com/repos/$owner/$repoName"

    /** Format a download URL for a release archive */
    private fun Repo.getUrlForReleaseZip() = "$html_url/archive/refs/tags/$release_tag.zip"

    /** Format a URL to query a repo tree - to calculate actual size */
    // It's hard to see in the doc this not only accepts a commit SHA, but either branch (used here) or tag names too
    internal fun Repo.getUrlForTreeQuery() =
        "https://api.github.com/repos/$full_name/git/trees/$default_branch?recursive=true"

    /** Format a URL to fetch a preview image - without extension */
    internal fun getUrlForPreview(modUrl: String, branch: String) = "$modUrl/$branch/preview"
        .replace("github.com", "raw.githubusercontent.com")

    //endregion
    //region responses

    /**
     * Parsed Github repo search response
     * @property total_count Total number of hits for the search (ignoring paging window)
     * @property incomplete_results A flag set by github to indicate search was incomplete (never seen it on)
     * @property items Array of [repositories][Repo]
     * @see <a href="https://docs.github.com/en/rest/reference/search#search-repositories--code-samples">Github API doc</a>
     */
    class RepoSearch {
        @Suppress("MemberVisibilityCanBePrivate")
        var total_count = 0
        var incomplete_results = false
        var items = ArrayList<Repo>()
    }

    /** Part of [RepoSearch] in Github API response - one repository entry in [items][RepoSearch.items] */
    class Repo {

        /** Unlike the rest of this class, this is not part of the API but added by us locally
         *  to track whether [getRepoSize][Github.getRepoSize] has been run successfully for this repo */
        var hasUpdatedSize = false

        /** Not part of the github schema: Explicit final zip download URL for non-github or release downloads */
        var direct_zip_url = ""
        /** Not part of the github schema: release tag, for debugging (DL via direct_zip_url) */
        var release_tag = ""

        var name = ""
        var full_name = ""
        var description: String? = null
        var owner = RepoOwner()
        var stargazers_count = 0
        var default_branch = ""
        var html_url = ""
        var pushed_at = "" // don't use updated_at - see https://github.com/yairm210/Unciv/issues/6106
        var size = 0
        var topics = mutableListOf<String>()
        //var stargazers_url = ""
        //var homepage: String? = null      // might use instead of go to repo?
        //var has_wiki = false              // a wiki could mean proper documentation for the mod?

        /** String representation to be used for logging */
        override fun toString() = name.ifEmpty { direct_zip_url }

        companion object {
            /** Create a [Repo] metadata instance from a [url], supporting various formats
             *  from a repository landing page url to a free non-github zip download.
             *
             *  @see GithubAPI.parseUrl
             *  @return `null` for invalid links or any other failures
             */
            fun parseUrl(url: String): Repo? = Repo().parseUrl(url)

            /** Query Github API for [owner]'s [repoName] repository metadata */
            fun query(owner: String, repoName: String): Repo? {
                val response = Github.download(getUrlForSingleRepoQuery(owner, repoName))
                    ?: return null
                val repoString = response.bufferedReader().readText()
                return json().fromJson(Repo::class.java, repoString)
            }
        }
    }

    /** Part of [Repo] in Github API response */
    class RepoOwner {
        var login = ""
        var avatar_url: String? = null
    }

    /** Topic search response */
    class TopicSearchResponse {
        // Commented out: Github returns them, but we're not interested
//         var total_count = 0
//         var incomplete_results = false
        var items = ArrayList<Topic>()
        class Topic {
            var name = ""
            var display_name: String? = null  // Would need to be curated, which is alottawork
//             var featured = false
//             var curated = false
            var created_at = "" // iso datetime with "Z" timezone
            var updated_at = "" // iso datetime with "Z" timezone
        }
    }

    /** Class to receive a github API "Get a tree" response parsed as json */
    // Parts of the response we ignore are commented out
    internal class Tree {
        //val sha = ""
        //val url = ""

        class TreeFile {
            //val path = ""
            //val mode = 0
            //val type = "" // blob / tree
            //val sha = ""
            //val url = ""
            var size: Long = 0L
        }

        @Suppress("MemberNameEqualsClassName")
        var tree = ArrayList<TreeFile>()
        var truncated = false
    }

    //endregion

    //region Flexible URL parsing
    /**
     * Initialize `this` with an url, extracting all possible fields from it
     * (html_url, author, repoName, branchName).
     *
     * Allow url formats:
     * * Basic repo url:
     *   https://github.com/author/repoName
     * * or complete 'zip' url from github's code->download zip menu:
     *   https://github.com/author/repoName/archive/refs/heads/branchName.zip
     * * or the branch url same as one navigates to on github through the "branches" menu:
     *   https://github.com/author/repoName/tree/branchName
     * * or release tag
     *   https://github.com/author/repoName/releases/tag/tagname
     *   https://github.com/author/repoName/archive/refs/tags/tagname.zip
     *
     * In the case of the basic repo url, an [API query](https://docs.github.com/en/rest/repos/repos#get-a-repository) is sent to determine the default branch.
     * Other url forms will not go online.
     *
     * @return a new Repo instance for the 'Basic repo url' case, otherwise `this`, modified, to allow chaining, `null` for invalid links or any other failures
     * @see <a href="https://docs.github.com/en/rest/repos/repos#get-a-repository--code-samples">Github API Repository Code Samples</a>
     */
    private fun Repo.parseUrl(url: String): Repo? {
        fun processMatch(matchResult: MatchResult): Repo {
            html_url = matchResult.groups[1]!!.value
            owner.login = matchResult.groups[2]!!.value
            name = matchResult.groups[3]!!.value
            default_branch = matchResult.groups[4]!!.value
            return this
        }

        html_url = url
        default_branch = "master"
        val matchZip = Regex("""^(.*/(.*)/(.*))/archive/(?:.*/)?heads/([^.]+).zip$""").matchEntire(url)
        if (matchZip != null && matchZip.groups.size > 4)
            return processMatch(matchZip)

        val matchBranch = Regex("""^(.*/(.*)/(.*))/tree/(.*)$""").matchEntire(url)
        if (matchBranch != null && matchBranch.groups.size > 4)
            return processMatch(matchBranch)

        // Releases and tags -
        // TODO Query for latest release and save as Mod Version?
        // https://docs.github.com/en/rest/releases/releases#get-the-latest-release
        // TODO Query a specific release for its name attribute - the page will link the tag
        // https://docs.github.com/en/rest/releases/releases#get-a-release-by-tag-name

        val matchTagArchive = Regex("""^(.*/(.*)/(.*))/archive/(?:.*/)?tags/([^.]+).zip$""").matchEntire(url)
        if (matchTagArchive != null && matchTagArchive.groups.size > 4) {
            processMatch(matchTagArchive)
            release_tag = default_branch
            // leave default_branch even if it's actually a tag not a branch name
            // so the suffix of the inner first level folder inside the zip can be removed later
            direct_zip_url = url
            return this
        }
        val matchTagPage = Regex("""^(.*/(.*)/(.*))/releases/(?:.*/)?tag/([^.]+)$""").matchEntire(url)
        if (matchTagPage != null && matchTagPage.groups.size > 4) {
            processMatch(matchTagPage)
            release_tag = default_branch
            direct_zip_url = getUrlForReleaseZip()
            return this
        }

        val matchRepo = Regex("""^.*//.*/(.+)/(.+)/?$""").matchEntire(url)
        if (matchRepo != null && matchRepo.groups.size > 2) {
            // Query API if we got the 'https://github.com/author/repoName' URL format to get the correct default branch
            val repo = Repo.query(matchRepo.groups[1]!!.value, matchRepo.groups[2]!!.value)
            if (repo != null) return repo
        }

        // Only complain about invalid link if it isn't a http protocol (to think about: android document protocol? file protocol?)
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            return null

        // From here, we'll always return success and treat the url as direct-downloadable zip.
        // The Repo instance will be a pseudo-repo not corresponding to an actual github repo.
        html_url = ""
        direct_zip_url = url
        owner.login = "-unknown-"
        default_branch = "master" // only used to remove this suffix should the zip contain a inner folder
        // But see if we can extract a file name from the url
        // Will use filename from response headers, if content-disposition is sent, for the Mod name instead, done in downloadAndExtract
        val matchAnyZip = Regex("""^.*//(?:.*/)*([^/]+\.zip)$""").matchEntire(url)
        if (matchAnyZip != null && matchAnyZip.groups.size > 1)
            name = matchAnyZip.groups[1]!!.value
        full_name = name
        return this
    }
    //endregion
}
