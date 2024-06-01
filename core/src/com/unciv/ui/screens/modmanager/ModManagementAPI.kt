package com.unciv.ui.screens.modmanager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Disposable
import com.unciv.logic.github.Github
import com.unciv.logic.github.Github.repoNameToFolderName
import com.unciv.logic.github.GithubAPI
import com.unciv.logic.github.GraphQLQuery
import com.unciv.logic.github.GraphQLResult
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import com.unciv.logic.github.GraphQL as GraphQLImpl

abstract class ModManagementAPI : Disposable {

    // cleanup - background processing needs to be stopped on exit and memory freed
    protected var runningSearchJob: Job? = null
    // This is only set for cleanup, not when the user stops the query (by clicking the loading icon)
    // Therefore, finding `runningSearchJob?.isActive == false && !stopBackgroundTasks` means stopped by user
    protected var stopBackgroundTasks = false

    abstract fun start()

    open fun stop() {
        if (runningSearchJob?.isActive != true) return
        runningSearchJob?.cancel()
    }

    fun isRunning() = runningSearchJob != null

    override fun dispose() {
        // make sure the worker threads will not continue trying their time-intensive job
        stop()
        stopBackgroundTasks = true
    }

    internal class REST(
        private val screen: BaseScreen,
        private val onNewModData: (ModUIData) -> Unit,
        private val onRefreshUI: () -> Unit,
        private val onFinished: () -> Unit,
        private val onQueryIncomplete: () -> Unit
    ) : ModManagementAPI() {
        override fun start() {
            tryDownloadPage(1)
        }

        private fun tryDownloadPage(pageNum: Int) {
            runningSearchJob = Concurrency.run("GitHubSearch") {
                val repoSearch: GithubAPI.RepoSearch
                try {
                    repoSearch = Github.tryGetGithubReposWithTopic(ModManagementScreen.amountPerPage, pageNum)!!
                } catch (ex: Exception) {
                    Log.error("Could not download mod list", ex)
                    launchOnGLThread {
                        ToastPopup("Could not download mod list", screen)
                        onFinished()
                    }
                    Gdx.app.clipboard.contents = ex.stackTraceToString()
                    runningSearchJob = null
                    return@run
                }

                if (!isActive) {
                    return@run
                }

                launchOnGLThread { addModInfoFromRepoSearch(repoSearch, pageNum) }
                runningSearchJob = null
            }
        }


        private fun addModInfoFromRepoSearch(repoSearch: GithubAPI.RepoSearch, pageNum: Int) {
            for (repo in repoSearch.items) {
                if (stopBackgroundTasks) return
                repo.name = repo.name.repoNameToFolderName()
                onNewModData(ModUIData(repo))
            }

            // Now the tasks after the 'page' of search results has been fully processed
            // The search has reached the last page!
            if (repoSearch.items.size < ModManagementScreen.amountPerPage) {
                // Check: It is also not impossible we missed a mod - just inform user
                if (repoSearch.incomplete_results) {
                    onQueryIncomplete()
                }
            }

            onRefreshUI()

            // continue search unless last page was reached
            if (repoSearch.items.size >= ModManagementScreen.amountPerPage && !stopBackgroundTasks)
                tryDownloadPage(pageNum + 1)
            else
                onFinished()
        }
    }

    internal class GraphQL(
        token: String,
        private val screen: BaseScreen,
        private val onNewModData: (ModUIData) -> Unit,
        private val onRefreshUI: () -> Unit,
        private val onFinished: () -> Unit,
        private val onQueryIncomplete: () -> Unit
    ) : ModManagementAPI() {
        private val graphQL = GraphQLImpl { token }

        override fun start() {
            var cursor: String? = null
            var totalCount = 0L
            var expectedCount: Long

            runningSearchJob = Concurrency.run("GitHubSearch") {
                do {
                    // Using let to ensure the json string is immediately GC-collectible
                    val data = graphQL.request(GraphQLQuery.ListRepositories(cursor, ModManagementScreen.amountPerPage), this).let {
                        GraphQLResult.RepositoryList(graphQL, it)
                    }
                    cursor = data.endCursor
                    expectedCount = data.totalCount
                    launchOnGLThread {
                        for (repository in data.values) {
                            totalCount++
                            val modUIData = ModUIData(repository.translateToOldRepo())
                            onNewModData(modUIData)
                        }
                        onRefreshUI()
                    }
                } while (data.hasNextPage)
                launchOnGLThread {
                    if (totalCount < expectedCount)
                        onQueryIncomplete()
                    onFinished()
                }
            }
            runningSearchJob!!.invokeOnCompletion {
                runningSearchJob = null
            }
        }
    }
}
