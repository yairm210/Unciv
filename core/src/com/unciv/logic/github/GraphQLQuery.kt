package com.unciv.logic.github

import com.unciv.logic.github.Github.folderNameToRepoName
import com.unciv.models.ruleset.RulesetCache

@Suppress("unused", "MemberVisibilityCanBePrivate") // This is an API
abstract class GraphQLQuery(
    open val query: String
) {
    interface Variables

    open val variables: Variables? = null

    /** "whoami": Github knows who they issued our token for */
    @Suppress("UNUSED_PARAMETER") // Prevents Json from calling a no-args constructor, which would lead to it outputting nothing
    class Self(dummy: Int) : GraphQLQuery("""
        query {
          viewer {
            login
          }
        }
    """)

    open class ListRepositories(cursor: String?, pageSize: Int = 20, maxTopics: Int = 10, withRelease: Boolean = false) : GraphQLQuery("""
        query(${"$"}pageSize:Int, ${"$"}after:String, ${"$"}maxTopics:Int) {
          topic(name: "unciv-mod") {
            repositories(orderBy:{field:STARGAZERS, direction:DESC}, first:${"$"}pageSize, after:${"$"}after) {
              totalCount
              pageInfo {
                endCursor
                hasNextPage
              }
              nodes {
                name
                owner {login, avatarUrl}
                description
                pushedAt
                stargazerCount
                defaultBranchRef{name}
                ${releaseQuery(withRelease)}
                repositoryTopics(first:${"$"}maxTopics) {
                  nodes{topic{name}}
                }
              }
            }
          }
        }
    """.trimIndent()) {
        class Variables(
            val after: String?,
            val pageSize: Int,
            val maxTopics: Int
        ) : GraphQLQuery.Variables
        override val variables = Variables(cursor, pageSize, maxTopics)
        companion object {
            private fun releaseQuery(withRelease: Boolean) = if (withRelease) """
                latestRelease {
                  name
                  tagName
                  author{login}
                  publishedAt
                  description
                  url
                  releaseAssets(first:1) {
                    nodes {
                      name
                      downloadUrl
                    }
                  }
                }
            """.trimIndent() else ""
        }
    }

    class CheckForUpdates private constructor(query: String, maxTopics: Int = 10) : GraphQLQuery(query) {
        data class RepoWithOwner(val login: String, val name: String)

        /** This constructor identifies the repositories to check with known owner and name. [mods] are the folder names as stored, not the repo names. */
        constructor(mods: Sequence<RepoWithOwner>, withRelease: Boolean = false) : this(buildQuery(mods, withRelease))

        companion object {
            /** This factory retrieves the required owner to sufficiently identify a repository by looking up the modOptions from RulesetCache */
            operator fun invoke(mods: Sequence<String>, withRelease: Boolean = false) = CheckForUpdates(mods.mapNotNull(::modNameToRepoWithOwner), withRelease)

            private fun buildQuery(mods: Sequence<RepoWithOwner>, withRelease: Boolean): String {
                val sb = StringBuilder()
                sb.appendLine("query (${"$"}maxTopics:Int) {")
                for ((index, mod) in mods.withIndex()) {
                    sb.append("  Repo%03d: repository(owner:\"%s\", name:\"%s\")".format(index + 1, mod.login, mod.name))
                    sb.append(" {name, owner{login,avatarUrl}, description, pushedAt, stargazerCount, defaultBranchRef{name}${releaseQuery(withRelease)}")
                    sb.appendLine(", repositoryTopics(first:${"$"}maxTopics) {nodes{topic{name}}}}")
                }
                sb.appendLine("}")
                return sb.toString()
            }
            private fun releaseQuery(withRelease: Boolean) = if (withRelease) ", latestRelease {name, publishedAt}" else ""

            private fun modNameToRepoWithOwner(mod: String): RepoWithOwner? {
                val ruleset = RulesetCache[mod] ?: return null
                val owner = ruleset.modOptions.author
                if (owner.isEmpty()) return null
                val name = mod.folderNameToRepoName()
                return RepoWithOwner(owner, name)
            }
        }

        class Variables(
            val maxTopics: Int
        ) : GraphQLQuery.Variables
        override val variables = Variables(maxTopics)
    }
}
