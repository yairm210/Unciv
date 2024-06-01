package com.unciv.testing

import com.unciv.logic.github.GraphQL
import com.unciv.logic.github.GraphQLQuery
import com.unciv.logic.github.GraphQLResult
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

/**
 *  # Instructions
 *  - Fill out testing account login, mail (those so you don't forget) and personal access token in the companion object
 *  - Comment out the `@Ignore` annotation
 *  - Run the tests as you please
 *  - Never push a PAT to github: Exclude or rollback these changes
 */
@RunWith(GdxTestRunner::class)
@Ignore("This should not run from github's automatic build runners")
class GraphQLTest {
    companion object {
        /**
         *  Testing account:
         *  - ...
         *  - ...
         */
        val token: String? = null
    }

    private val graphQL = if (token == null) GraphQL(GraphQL.Backend.Gdx) else GraphQL(GraphQL.Backend.Gdx) { token }

    @Test
    @RedirectOutput(RedirectPolicy.Show)
    fun `GraphQL self-query gives correct login`() {
        val result = graphQL.synchronousRequest(GraphQLQuery.Self(9))
        println("Result: $result")
        val parsed = GraphQLResult.Self(graphQL, result)
        Assert.assertEquals("UncivIsCool", parsed.login)
    }

    @Test
    @RedirectOutput(RedirectPolicy.Show)
    fun `Can list repositories via GraphQL`() {
        listRepositories(GraphQLQuery.ListRepositories(null))
    }
    @Test
    @RedirectOutput(RedirectPolicy.Show)
    fun `Can list repositories with release data via GraphQL`() {
        listRepositories(GraphQLQuery.ListRepositories(null, 3, withRelease = true))
    }

    @Test
    @RedirectOutput(RedirectPolicy.Show)
    fun `Can check for updates via GraphQL`() {
        val mods = listOf(
            GraphQLQuery.CheckForUpdates.RepoWithOwner("SomeTroglodyte", "Additional-Music-Ambient"),
            GraphQLQuery.CheckForUpdates.RepoWithOwner("Caballero-Arepa", "Latin-American_Civs")
            )
        val result = graphQL.synchronousRequest(GraphQLQuery.CheckForUpdates(mods.asSequence(), withRelease = true))
        println("Result: $result")
        val parsed = try {
            GraphQLResult.CheckForUpdatesResult(graphQL, result)
        } catch (ex: GraphQLResult.NoDataException) {
            val errors = GraphQLResult.Errors(graphQL, result)
            errors.forEach { println("GraphQL error: $it") }
            throw ex
        }
        for (entry in parsed) println(entry.toString())
    }

    @Test
    fun `Validate access token`() {
        Assert.assertFalse("The github token does not match the required pattern", graphQL.isTokenInvalid())
    }

    @OptIn(ExperimentalTime::class)
    private fun listRepositories(query: GraphQLQuery) {
        val timeMark = kotlin.time.TimeSource.Monotonic.markNow()
        val result = graphQL.synchronousRequest(query)
        println("Duration: ${timeMark.elapsedNow()} (${graphQL.backend})")
        println("Result: $result")
        val parsed = try {
            GraphQLResult.RepositoryList(graphQL, result)
        } catch (ex: GraphQLResult.NoDataException) {
            val errors = GraphQLResult.Errors(graphQL, result)
            errors.forEach { println("GraphQL error: $it") }
            throw ex
        }
        println("Parsed:" + parsed.joinToString("\n", "\n") { (name, repo) ->
            "\t$name: $repo"
        })
        println("Ratelimit: ${GraphQL.RateLimit.percent()}% ${GraphQL.RateLimit}")
    }
}
