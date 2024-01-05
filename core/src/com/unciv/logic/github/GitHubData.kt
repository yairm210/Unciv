package com.unciv.logic.github

import com.unciv.json.json

@Suppress("PropertyName")  // We're declaring an external API schema
object GitHubData {
    /**
     * Parsed GitHub repo search response
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
         *  to track whether [getRepoSize] has been run successfully for this repo */
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
         */
        fun parseUrl(url: String): Repo? {
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

            val matchBranch = Regex("""^(.*/(.*)/(.*))/tree/([^/]+)$""").matchEntire(url)
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
                direct_zip_url = "$html_url/archive/refs/tags/$release_tag.zip"
                return this
            }

            val matchRepo = Regex("""^.*//.*/(.+)/(.+)/?$""").matchEntire(url)
            if (matchRepo != null && matchRepo.groups.size > 2) {
                // Query API if we got the 'https://github.com/author/repoName' URL format to get the correct default branch
                val response = Github.download("https://api.github.com/repos/${matchRepo.groups[1]!!.value}/${matchRepo.groups[2]!!.value}")
                if (response != null) {
                    val repoString = response.bufferedReader().readText()
                    return json().fromJson(Repo::class.java, repoString)
                }
            }

            // Only complain about invalid link if it isn't a http protocol (to think about: android document protocol? file protocol?)
            if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("blob:https://"))
                return null

            // From here, we'll always return success and treat the url as direct-downloadable zip.
            // The Repo instance will be a pseudo-repo not corresponding to an actual github repo.
            direct_zip_url = url
            owner.login = "-unknown-"
            default_branch = "master" // only used to remove this suffix should the zip contain a inner folder
            // But see if we can extract a file name from the url
            // Will use filename from response headers for the Mod name instead, done in downloadAndExtract
            val matchAnyZip = Regex("""^.*//(?:.*/)*([^/]+\.zip)$""").matchEntire(url)
            if (matchAnyZip != null && matchAnyZip.groups.size > 1)
                name = matchAnyZip.groups[1]!!.value
            return this
        }

        /** String representation to be used for logging */
        override fun toString() = name.ifEmpty { direct_zip_url }
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
}
