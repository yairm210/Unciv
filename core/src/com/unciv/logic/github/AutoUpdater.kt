package com.unciv.logic.github

import com.badlogic.gdx.Gdx
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.ui.images.ImageGetter
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive

class AutoUpdater(
    private val onResult: (Boolean) -> Unit
) : DisposableHandle, Iterable<GraphQLResult.UpdateInfo> {
    private var updateCheckerJob: Job? = null
    private var updaterJob: Job? = null
    private lateinit var result: GraphQLResult.CheckForUpdatesResult

    init {
        val token = GUI.getSettings().githubAccessToken
        if (token != null) runCheck(token)
    }

    private fun runCheck(token: String) {
        updateCheckerJob = Concurrency.run("ModUpdateChecker") {
            val graphQL = GraphQL { token }
            val mods = RulesetCache.values.asSequence()
                .map { it.name }
                .filter { it != "" && BaseRuleset.values().none { br -> br.fullName == it } }
            val query = GraphQLQuery.CheckForUpdates(mods)
            result = graphQL.request(query, this).let {
                GraphQLResult.CheckForUpdatesResult(graphQL, it)
            }
            launchOnGLThread { // for GL context
                onResult(result.needsUpdate())
            }
        }.apply {
            invokeOnCompletion {
                cancelCheck()
            }
        }
    }

    fun isChecking() = updateCheckerJob != null

    override fun dispose() {
        cancel()
    }

    fun cancelCheck() {
        val job = updateCheckerJob
        updateCheckerJob = null
        job?.run { if (!isCancelled) cancel() }
    }

    fun cancelUpdate() {
        val job = updaterJob
        updaterJob = null
        job?.run { if (!isCancelled) cancel() }
    }

    fun cancel() {
        cancelCheck()
        cancelUpdate()
    }

    fun doUpdate(onComplete: (successes: Int, total: Int)->Unit, onProgress: (percent: Int)->Unit, onToast: (message: String)->Unit) {
        if (!::result.isInitialized) throw UnsupportedOperationException("You can call doUpdate only after the onResult callback")
        updaterJob = Concurrency.run("ModAutoUpdate") {
            var successes = 0
            var count = 0
            val total = result.count { it.needsUpdate() }
            for (updateInfo in result) {
                if (!updateInfo.needsUpdate()) continue
                // Percent should not start at 0 end never reach 100, nor show 100 when there's still work to do? Let's just add 20% of a step.
                // Also, don't do the math in the launchOnGLThread runner with a ***boxed*** `count` closure - it will pull the current value from our var here.
                val percent = (count * 100 + 20) / total
                count++
                launchOnGLThread { onProgress(percent) }
                if (updateMod(updateInfo) { modName, error ->
                    onToast(error ?: "[$modName] Downloaded!")
                }) successes++
            }
            launchOnGLThread {
                reloadCachesAfterModChange()  // Needs GL context for resetFont...createAndCacheGlyph (and possibly more)
                onComplete(successes, total)
            }
        }.apply {
            invokeOnCompletion {
                cancelUpdate()
            }
        }
    }

    override operator fun iterator() = result.iterator()
    fun getUpdatableRepositories() = result.getUpdatableRepositories()
    fun hasErrors() = result.hasErrors()

    private suspend fun CoroutineScope.updateMod(
        updateInfo: GraphQLResult.UpdateInfo,
        postAction: (modName: String, error: String?) -> Unit
    ): Boolean {
        if (!isActive) return false
        val repository = (updateInfo as? GraphQLResult.Repository) ?: return false
        val repo = repository.translateToOldRepo()
        return downloadMod(repo, postAction)
    }

    companion object {
        /** Instantiate an AutoUpdater, see if it _can_ work, return it if yes, otherwise return `null` */
        fun createAndStart(onResult: (Boolean) -> Unit): AutoUpdater? {
            val updater = AutoUpdater(onResult)
            if (updater.isChecking()) return updater
            return null
        }

        /**
         *  @param repo A GithubAPI.Repo with default_branch, owner.login, pushedAt, topics and either direct_zip_url or html_url populated
         */
        suspend fun downloadMod(repo: GithubAPI.Repo, postAction: (modName: String, error: String?) -> Unit): Boolean {
            return coroutineScope {
                try {
                    val modFolder = Github.downloadAndExtract(
                        repo,
                        Gdx.files.local("mods")
                    )
                        ?: throw Exception("Exception during GitHub download")    // downloadAndExtract returns null for 404 errors and the like -> display something!
                    Github.rewriteModOptions(repo, modFolder)
                    launchOnGLThread {
                        postAction(modFolder.name(), null) // repo.name still has the replaced "-"'s
                    }
                    true
                } catch (ex: UncivShowableException) {
                    Log.error("Could not download $repo", ex)
                    launchOnGLThread {
                        postAction(repo.name, ex.message)
                    }
                    false
                } catch (ex: Exception) {
                    Log.error("Could not download $repo", ex)
                    launchOnGLThread {
                        postAction(repo.name, "Could not download [${repo.name}]")
                    }
                    false
                }
            }
        }

        private fun reloadCachesAfterModChange() {
            RulesetCache.loadRulesets()
            ImageGetter.reloadImages()
            TileSetCache.loadTileSetConfigs()
            UncivGame.Current.translations.tryReadTranslationForCurrentLanguage()
        }
    }
}
