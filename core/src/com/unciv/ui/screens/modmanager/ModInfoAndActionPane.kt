package com.unciv.ui.screens.modmanager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.github.Github
import com.unciv.logic.github.GithubAPI
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.validation.ModCompatibility
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.UncivDateFormat.formatDate
import com.unciv.ui.components.extensions.UncivDateFormat.parseDate
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.modmanager.ModManagementScreen.Companion.cleanModName
import com.unciv.utils.Concurrency
import kotlin.math.max

internal class ModInfoAndActionPane(private val onDownloadModFromUrl: ((String) -> Unit)? = null) : Table() {
    private val repoUrlToPreviewImage = HashMap<String, Texture?>()
    private val imageHolder = Table()
    private val sizeLabel = "".toLabel()
    private var isBuiltin = false
    private var currentRepoName = ""

    /** controls "Permanent audiovisual mod" checkbox existence */
    private var enableVisualCheckBox = false

    init {
        defaults().pad(10f)
    }

    /** Recreate the information part of the right-hand column
     * @param repo: the repository instance as received from the GitHub api
     */
    fun update(repo: GithubAPI.Repo) {
        isBuiltin = false
        enableVisualCheckBox = false
        update(
            isLocal = false,
            repo.name, repo.html_url, repo.default_branch,
            repo.pushed_at, repo.owner.login, repo.size,
            repo.owner.avatar_url
        )
    }

    /** Recreate the information part of the right-hand column
     * @param mod: The mod RuleSet (from RulesetCache)
     */
    fun update(mod: Ruleset) {
        val modName = mod.name
        val modOptions = mod.modOptions  // The ModOptions as enriched by us with GitHub metadata when originally downloaded
        isBuiltin = modOptions.modUrl.isEmpty() && BaseRuleset.entries.any { it.fullName == modName }
        enableVisualCheckBox = ModCompatibility.isAudioVisualMod(mod)
        update(
            isLocal = true,
            modName, modOptions.modUrl, modOptions.defaultBranch,
            modOptions.lastUpdated, modOptions.author, modOptions.modSize
        )
    }

    private fun update(
        isLocal: Boolean,
        modName: String,
        repoUrl: String,
        defaultBranch: String,
        updatedAt: String,
        author: String,
        modSize: Int,
        avatarUrl: String? = null
    ) {
        // Display metadata
        clear()
        currentRepoName = modName

        imageHolder.clear()
        when {
            isBuiltin -> addUncivLogo(modName)
            isLocal -> addLocalPreviewImage(modName)
            else -> addPreviewImage(modName, repoUrl, defaultBranch, avatarUrl)
        }
        add(imageHolder).row()

        if (author.isNotEmpty())
            add("Author: [$author]".toLabel()).row()

        updateSize(modSize)
        add(sizeLabel).padBottom(15f).row()

        // offer link to open the repo itself in a browser
        if (repoUrl.isNotEmpty()) {
            val githubButton = "Open Github page".toTextButton()
            githubButton.onClick {
                Gdx.net.openURI(repoUrl)
            }
            githubButton.onRightClick {
                Gdx.app.clipboard.contents = repoUrl
                ToastPopup("Link copied to clipboard", stage)
            }
            add(githubButton).row()
        }
        
        // allow to re-download mod from on-file link in case of mod update or corruption
        if (repoUrl.isNotEmpty()) {
            val redownloadButton = "Re-download Mod".toTextButton()
            redownloadButton.onClick {
                redownloadButton.setText("Downloading...".tr())
                onDownloadModFromUrl?.invoke(repoUrl)
            }
            add(redownloadButton).row()
        }

        // display "updated" date
        if (updatedAt.isNotEmpty()) {
            val date = updatedAt.parseDate()
            val updateString = "{Updated}: " + date.formatDate()
            add(updateString.toLabel()).row()
        }
    }

    fun updateSize(size: Int) {
        val text = when {
            size <= 0 -> ""
            size < 2048 -> "Size: [$size] kB"
            else -> "Size: [${(size + 512) / 1024}] MB"
        }
        sizeLabel.setText(text.tr())
    }

    fun addVisualCheckBox(startsOutChecked: Boolean = false, changeAction: ((Boolean)->Unit)? = null) {
        if (enableVisualCheckBox)
            add("Permanent audiovisual mod".toCheckBox(startsOutChecked, changeAction)).row()
    }

    fun addUpdateModButton(modInfo: ModUIData, doDownload: () -> Unit) {
        if (!modInfo.hasUpdate) return
        val updateModTextbutton = "Update [${cleanModName(modInfo.name)}]".toTextButton()
        updateModTextbutton.onClick {
            updateModTextbutton.setText("Downloading...".tr())
            doDownload()
        }
        add(updateModTextbutton).row()
    }

    private fun addPreviewImage(modName: String, repoUrl: String, defaultBranch: String, avatarUrl: String?) {
        if (!repoUrl.startsWith("http")) return // invalid url

        if (repoUrlToPreviewImage.containsKey(repoUrl)) {
            val texture = repoUrlToPreviewImage[repoUrl]
            if (texture != null) setTextureAsPreview(texture, modName)
            return
        }

        Concurrency.run {
            val imagePixmap = Github.getPreviewImageOrNull(repoUrl, defaultBranch, avatarUrl)

            if (imagePixmap == null) {
                repoUrlToPreviewImage[repoUrl] = null
                return@run
            }
            Concurrency.runOnGLThread {
                val texture = Texture(imagePixmap)
                imagePixmap.dispose()
                repoUrlToPreviewImage[repoUrl] = texture
                setTextureAsPreview(texture, modName)
            }
        }
    }

    private fun addLocalPreviewImage(modName: String) {
        // No concurrency, order of magnitude 20ms
        val modFolder = UncivGame.Current.files.getModFolder(modName)
        val previewFile = modFolder.child("preview.jpg").takeIf { it.exists() }
            ?: modFolder.child("preview.png").takeIf { it.exists() }
            ?: return
        setTextureAsPreview(Texture(previewFile), modName)
    }

    private fun addUncivLogo(modName: String) {
        setTextureAsPreview(Texture(Gdx.files.internal("ExtraImages/banner.png")), modName)
    }

    private fun setTextureAsPreview(texture: Texture, modName: String) {
        val image = Image(texture)
        if (modName != currentRepoName) return // user has selected another mod in the meantime
        val cell = imageHolder.add(image)
        val largestImageSize = max(texture.width, texture.height)
        if (largestImageSize > ModManagementScreen.maxAllowedPreviewImageSize) {
            val resizeRatio = ModManagementScreen.maxAllowedPreviewImageSize / largestImageSize
            cell.size(texture.width * resizeRatio, texture.height * resizeRatio)
        }
    }
}
