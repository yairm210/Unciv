package com.unciv.logic.github

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.github.Github.repoNameToFolderName
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import java.time.DateTimeException
import java.time.Instant

private fun JsonValue.getOrNull(name: String) = get(name)?.takeUnless { it.isNull }

private fun JsonValue.getStringOrNull(name: String) = getOrNull(name)?.asString()

@Suppress("unused", "MemberVisibilityCanBePrivate") // This is an API
interface GraphQLResult : Json.Serializable {
    open class InvalidSchemaException(missingNode: String) : Exception("GraphQL response is missing node \"$missingNode\"")
    class NoDataException : InvalidSchemaException("data")

    override fun write(json: Json?) {
        throw UnsupportedOperationException("GraphQLResult instances cannot be serialized")
    }

    /** Represents the answer to a [GraphQLQuery.Self] query: gets login from token */
    class Self private constructor() : GraphQLResult {
        var login: String = ""
            private set

        companion object {
            operator fun invoke(graphQL: GraphQL, jsonText: String): Self {
                return graphQL.parseResult(Self::class.java, jsonText)
            }
        }

        override fun read(json: Json, jsonData: JsonValue) {
            login = jsonData["data"]["viewer"]["login"].asString()
        }
    }

    /** Represents the answer to a [GraphQLQuery.ListRepositories] query */
    class RepositoryList private constructor()
        : GraphQLResult,
        LinkedHashMap<String, Repository>(),
        Iterable<MutableMap.MutableEntry<String, Repository>>
    {
        var totalCount = 0L
            private set
        var endCursor = ""
            private set
        var hasNextPage = false
            private set

        companion object {
            operator fun invoke(graphQL: GraphQL, jsonText: String): RepositoryList {
                return graphQL.parseResult(RepositoryList::class.java, jsonText)
            }
        }

        override fun read(json: Json, jsonData: JsonValue) {
            val data = jsonData["data"] ?: throw NoDataException()
            val topic = data.get("topic") ?: throw InvalidSchemaException("topic")
            val repos = topic.get("repositories") ?: throw InvalidSchemaException("repositories")
            totalCount = repos.getLong("totalCount")
            hasNextPage = repos["pageInfo"].getBoolean("hasNextPage")
            endCursor = repos["pageInfo"].getString("endCursor")
            var node = repos["nodes"].child
            while (node != null) {
                val name = node.getString("name")
                this[name] = Repository(node)
                node = node.next
            }
        }

        override fun iterator(): Iterator<MutableMap.MutableEntry<String, Repository>> = entries.iterator()
    }

    /** Represents the answer to a [GraphQLQuery.CheckForUpdates] query */
    class CheckForUpdatesResult private constructor() : GraphQLResult, ArrayList<UpdateInfo>() {

        class Error private constructor(val type: String, val message: String) : UpdateInfo {
            constructor(node: JsonValue) : this(node.getString("type"), node.getString("message"))
            override fun toString() = "Error($type: $message)"
        }

        companion object {
            operator fun invoke(graphQL: GraphQL, jsonText: String): CheckForUpdatesResult {
                return graphQL.parseResult(CheckForUpdatesResult::class.java, jsonText)
            }
        }

        override fun read(json: Json, jsonData: JsonValue) {
            val data = jsonData["data"] ?: throw NoDataException()
            var child = data.child
            while (child != null) {
                if (!child.isNull)
                    add(Repository(child))
                child = child.next
            }
            child = jsonData["errors"]?.child ?: return
            while (child != null) {
                if (!child.isNull)
                    add(Error(child))
                child = child.next
            }
        }

        fun needsUpdate() = any { it.needsUpdate() }
        fun hasErrors() = any { it is Error }
        fun getUpdatableRepositories(): Sequence<Repository> = asSequence().filter { it.needsUpdate() }.filterIsInstance<Repository>()
    }

    /** Any query answer can contain an `errors` node, which we can fetch with this */
    class Errors private constructor() : GraphQLResult, Iterable<Errors.Error> {
        class Error private constructor(
            val message: String,
            val line: Int,
            val column: Int,
            val explanation: String?
        ) {
            internal constructor(node: JsonValue) : this(
                node.getString("message"),
                node["locations"].child.getInt("line"),
                node["locations"].child.getInt("column"),
                node["extensions"]?.get("problems")?.child?.getString("explanation")
            )
            override fun toString() = "$message at L$line,C$column" + (explanation?.let { " ($it)" } ?: "")
            fun asException() = GraphQL.GraphQLException(toString())
        }

        private val errors = ArrayList<Error>()

        companion object {
            operator fun invoke(graphQL: GraphQL, jsonText: String): Errors {
                return graphQL.parseResult(Errors::class.java, jsonText)
            }
        }

        override fun read(json: Json, jsonData: JsonValue) {
            var child: JsonValue? = jsonData["errors"]?.child ?: throw InvalidSchemaException("errors")
            while (child != null) {
                errors += Error(child)
                child = child.next
            }
        }

        override fun iterator() = errors.iterator()
    }

    interface UpdateInfo {
        fun needsUpdate() = false
    }

    class Repository private constructor(
        val name: String,
        val login: String,
        val avatarUrl: String,
        val description: String?,
        val pushedAt: Instant,
        val stargazerCount: Int,
        val defaultBranch: String,
        val topics: Set<String>,
        val latestRelease: Release?
    ) : UpdateInfo {
        internal constructor(node: JsonValue) : this(
            node.getString("name"),
            node["owner"].getString("login"),
            node["owner"].getString("avatarUrl"),
            node.getStringOrNull("description"),
            Instant.parse(node.getString("pushedAt")),
            node.getInt("stargazerCount"),
            node["defaultBranchRef"].getString("name"),
            parseTopics(node["repositoryTopics"]["nodes"]),
            // From ListRepositories, node["latestRelease"] is null.
            // From ListRepositoriesWithRelease, when there's no releases, but since we asked for it, it will be a JsonValue with the isNull property instead.
            node["latestRelease"]?.takeUnless { it.isNull }?.let { Release(it) }
        )

        companion object {
            fun parseTopics(node: JsonValue): Set<String> {
                var child: JsonValue? = node.child ?: return emptySet()
                val set = mutableSetOf<String>()
                while (child != null) {
                    set += child["topic"].getString("name")
                    child = child.next
                }
                return set
            }
        }

        private var needsUpdateCache: Boolean? = null

        val publishedAt get() = latestRelease?.publishedAt ?: pushedAt
        val displayName get() = name.repoNameToFolderName()

        fun getInstalledMod(): Ruleset? = RulesetCache[name.repoNameToFolderName()]

        override fun needsUpdate(): Boolean {
            if (needsUpdateCache != null) return needsUpdateCache!!
            val ruleset = getInstalledMod() ?: return false
            val installedTime = try { Instant.parse(ruleset.modOptions.lastUpdated) } catch (_: DateTimeException) { return false }
            needsUpdateCache = installedTime < publishedAt
            return needsUpdateCache!!
        }

        fun translateToOldRepo() = GithubAPI.Repo().also {
            it.name = name.repoNameToFolderName()
            it.full_name = name
            it.description = description
            it.owner.login = login
            it.owner.avatar_url = avatarUrl
            it.stargazers_count = stargazerCount
            it.default_branch = defaultBranch
            it.html_url = "https://github.com/$login/$name"
            it.pushed_at = publishedAt.toString() // ISO-8601
            it.topics.addAll(topics)
        }

        override fun toString() = listOfNotNull(
            "$login/$name",
            description?.let { "\"$it\"" },
            if (stargazerCount == 0) "" else "✯$stargazerCount",
            latestRelease?.let { "release: $it" } ?: "⌚$pushedAt",
            topics.joinToString(",","[", "]", 3)
        ).joinToString(", ", "Repository(", ")")

        class Release private constructor(
            val name: String,
            val tag: String?,
            val author: String?,
            val publishedAt: Instant,
            val description: String?,
            val url: String?,
            val assetName: String?,
            val downloadUrl: String?
        ) {
            internal constructor(node: JsonValue) : this(
                node.getString("name"),
                node.getStringOrNull("tagName"),
                node.getOrNull("author")?.getString("login"),
                Instant.parse(node.getString("publishedAt")),
                node.getStringOrNull("description"),
                node.getStringOrNull("url"),
                node.getOrNull("releaseAssets")?.get("nodes")?.child?.getString("name"),
                node.getOrNull("releaseAssets")?.get("nodes")?.child?.getString("downloadUrl")
            )

            override fun toString() = if(description == null) "$name ⌚$publishedAt" else "$name \"$description\" ⌚$publishedAt"
        }
    }
}
