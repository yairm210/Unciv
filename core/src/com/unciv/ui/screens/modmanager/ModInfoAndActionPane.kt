package com.unciv.ui.screens.modmanager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.UncivDateFormat.formatDate
import com.unciv.ui.components.extensions.UncivDateFormat.parseDate
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.pickerscreens.Github
import com.unciv.utils.Concurrency
import kotlin.math.max

internal class ModInfoAndActionPane : Table() {
    private val repoUrlToPreviewImage = HashMap<String, Texture?>()
    private val imageHolder = Table()
    private val sizeLabel = "".toLabel()
    private var isBuiltin = false

    /** controls "Permanent audiovisual mod" checkbox existence */
    private var enableVisualCheckBox = false

    init {
        defaults().pad(10f)
    }

    /** Recreate the information part of the right-hand column
     * @param repo: the repository instance as received from the GitHub api
     */
    fun update(repo: Github.Repo) {
        isBuiltin = false
        enableVisualCheckBox = false
        update(
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
        isBuiltin = modOptions.modUrl.isEmpty() && BaseRuleset.values().any { it.fullName == modName }
        enableVisualCheckBox = shouldShowVisualCheckbox(mod)
        update(
            modName, modOptions.modUrl, modOptions.defaultBranch,
            modOptions.lastUpdated, modOptions.author, modOptions.modSize
        )
    }

    private fun update(
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

        imageHolder.clear()
        when {
            isBuiltin -> addUncivLogo()
            repoUrl.isEmpty() -> addLocalPreviewImage(modName)
            else -> addPreviewImage(repoUrl, defaultBranch, avatarUrl)
        }
        add(imageHolder).row()

        if (author.isNotEmpty())
            add("Author: [$author]".toLabel()).row()

        updateSize(modSize)
        add(sizeLabel).padBottom(15f).row()

        // offer link to open the repo itself in a browser
        if (repoUrl.isNotEmpty()) {
            add("Open Github page".toTextButton().onClick {
                Gdx.net.openURI(repoUrl)
            }).row()
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
        val updateModTextbutton = "Update [${modInfo.name}]".toTextButton()
        updateModTextbutton.onClick {
            updateModTextbutton.setText("Downloading...".tr())
            doDownload()
        }
        add(updateModTextbutton).row()
    }

    private fun addPreviewImage(repoUrl: String, defaultBranch: String, avatarUrl: String?) {
        if (!repoUrl.startsWith("http")) return // invalid url

        if (repoUrlToPreviewImage.containsKey(repoUrl)) {
            val texture = repoUrlToPreviewImage[repoUrl]
            if (texture != null) setTextureAsPreview(texture)
            return
        }

        Concurrency.run {
            val imagePixmap = Github.tryGetPreviewImage(repoUrl, defaultBranch, avatarUrl)

            if (imagePixmap == null) {
                repoUrlToPreviewImage[repoUrl] = null
                return@run
            }
            Concurrency.runOnGLThread {
                val texture = Texture(imagePixmap)
                imagePixmap.dispose()
                repoUrlToPreviewImage[repoUrl] = texture
                setTextureAsPreview(texture)
            }
        }
    }

    private fun addLocalPreviewImage(modName: String) {
        // No concurrency, order of magnitude 20ms
        val modFolder = Gdx.files.local("mods/$modName")
        val previewFile = modFolder.child("preview.jpg").takeIf { it.exists() }
            ?: modFolder.child("preview.png").takeIf { it.exists() }
            ?: return
        setTextureAsPreview(Texture(previewFile))
    }

    private fun addUncivLogo() {
        setTextureAsPreview(Texture(Gdx.files.internal("ExtraImages/banner.png")))
    }

    private fun setTextureAsPreview(texture: Texture) {
        val cell = imageHolder.add(Image(texture))
        val largestImageSize = max(texture.width, texture.height)
        if (largestImageSize > ModManagementScreen.maxAllowedPreviewImageSize) {
            val resizeRatio = ModManagementScreen.maxAllowedPreviewImageSize / largestImageSize
            cell.size(texture.width * resizeRatio, texture.height * resizeRatio)
        }
    }

    private fun shouldShowVisualCheckbox(mod: Ruleset): Boolean {
        val folder = mod.folderLocation ?: return false  // Also catches isBuiltin

        // Check declared Mod Compatibility
        if (mod.modOptions.hasUnique(UniqueType.ModIsAudioVisualOnly)) return true
        if (mod.modOptions.hasUnique(UniqueType.ModIsAudioVisual)) return true
        if (mod.modOptions.hasUnique(UniqueType.ModIsNotAudioVisual)) return false

        // The following is the "guessing" part: If there's media, show the PAV choice...
        // Might be deprecated if declarative Mod compatibility succeeds
        fun isSubFolderNotEmpty(modFolder: FileHandle, name: String): Boolean {
            val file = modFolder.child(name)
            if (!file.exists()) return false
            if (!file.isDirectory) return false
            return file.list().isNotEmpty()
        }
        if (isSubFolderNotEmpty(folder, "music")) return true
        if (isSubFolderNotEmpty(folder, "sounds")) return true
        if (isSubFolderNotEmpty(folder, "voices")) return true
        return folder.list("atlas").isNotEmpty()
    }
}
