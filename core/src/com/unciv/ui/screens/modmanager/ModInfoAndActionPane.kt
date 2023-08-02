package com.unciv.ui.screens.modmanager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.ModOptions
import com.unciv.models.ruleset.RulesetCache
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

    init {
        defaults().pad(10f)
    }

//     fun update(modInfo: ModUIData) {
//         if (modInfo.isInstalled) {
//             val modOptions = RulesetCache[modInfo.name]?.modOptions
//                 ?: return
//             update(modInfo.name, modOptions)
//         } else {
//             update(modInfo.repo!!)
//         }
//     }

    /** Recreate the information part of the right-hand column
     * @param repo: the repository instance as received from the GitHub api
     */
    fun update(repo: Github.Repo) {
        update(
            repo.name, repo.html_url, repo.default_branch,
            repo.pushed_at, repo.owner.login, repo.size
        )
    }
    /** Recreate the information part of the right-hand column
     * @param modName: The mod name (name from the RuleSet)
     * @param modOptions: The ModOptions as enriched by us with GitHub metadata when originally downloaded
     */
    fun update(modName: String, modOptions: ModOptions) {
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
           modSize: Int
    ) {
        // Display metadata
        clear()

        imageHolder.clear()
        if (repoUrl.isEmpty())
            addLocalPreviewImage(modName)
        else
            addPreviewImage(repoUrl, defaultBranch)
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
            else -> "Size: [${size/1024}] MB"
        }
        sizeLabel.setText(text.tr())
    }

    fun addVisualCheckBox(startsOutChecked: Boolean = false, changeAction: ((Boolean)->Unit)? = null) {
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

    private fun addPreviewImage(repoUrl: String, defaultBranch: String) {
        if (!repoUrl.startsWith("http")) return // invalid url

        if (repoUrlToPreviewImage.containsKey(repoUrl)) {
            val texture = repoUrlToPreviewImage[repoUrl]
            if (texture != null) setTextureAsPreview(texture)
            return
        }

        Concurrency.run {
            val imagePixmap = Github.tryGetPreviewImage(repoUrl, defaultBranch)

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

    private fun setTextureAsPreview(texture: Texture) {
        val cell = imageHolder.add(Image(texture))
        val largestImageSize = max(texture.width, texture.height)
        if (largestImageSize > ModManagementScreen.maxAllowedPreviewImageSize) {
            val resizeRatio = ModManagementScreen.maxAllowedPreviewImageSize / largestImageSize
            cell.size(texture.width * resizeRatio, texture.height * resizeRatio)
        }
    }
}
