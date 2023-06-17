package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.metadata.ModCategories
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.newgamescreen.TranslatedSelectBox
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.ExpanderTab
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import kotlin.math.sign

/**
 * Helper class for Mod Manager - filtering and sorting.
 *
 * This isn't a UI Widget, but offers one: [expander] can be used to offer filtering and sorting options.
 * It holds the variables [sortInstalled] and [sortOnline] for the [modManagementScreen] and knows
 * how to sort collections of [ModUIData] by providing comparators.
 */
class ModManagementOptions(private val modManagementScreen: ModManagementScreen) {
    companion object {
        val sortByName = Comparator { mod1, mod2: ModUIData -> mod1.name.compareTo(mod2.name, true) }
        val sortByNameDesc = Comparator { mod1, mod2: ModUIData -> mod2.name.compareTo(mod1.name, true) }
        // lastUpdated is compared as string, but that should be OK as it's ISO format
        val sortByDate = Comparator { mod1, mod2: ModUIData -> mod1.lastUpdated().compareTo(mod2.lastUpdated()) }
        val sortByDateDesc = Comparator { mod1, mod2: ModUIData -> mod2.lastUpdated().compareTo(mod1.lastUpdated()) }
        // comparators for stars or status
        val sortByStars = Comparator { mod1, mod2: ModUIData ->
            10 * (mod2.stargazers() - mod1.stargazers()) + mod1.name.compareTo(mod2.name, true).sign
        }
        val sortByStatus = Comparator { mod1, mod2: ModUIData ->
            10 * (mod2.state.sortWeight() - mod1.state.sortWeight()) + mod1.name.compareTo(mod2.name, true).sign
        }

        const val installedHeaderText = "Current mods"
        const val onlineHeaderText = "Downloadable mods"
    }

    enum class SortType(
        val label: String,
        val symbols: String,
        val comparator: Comparator<in ModUIData>
    ) {
        Name("Name ￪", "￪", sortByName),
        NameDesc("Name ￬", "￬", sortByNameDesc),
        Date("Date ￪", "⌚￪", sortByDate),
        DateDesc("Date ￬", "⌚￬", sortByDateDesc),
        Stars("Stars ￬", "✯￬", sortByStars),
        Status("Status ￬", "◉￬", sortByStatus);

        fun next() = values()[(ordinal + 1) % values().size]

        companion object {
            fun fromSelectBox(selectBox: TranslatedSelectBox): SortType {
                val selected = selectBox.selected.value
                return values().firstOrNull { it.label == selected } ?: Name
            }
        }
    }

    class Filter(
        val text: String,
        val topic: String
    )

    fun getFilter(): Filter {
        return Filter(textField.text, category.topic)
    }

    private val textField = UncivTextField.create("Enter search text")

    var category = ModCategories.default()

    var sortInstalled = SortType.Name
    var sortOnline = SortType.Stars

    private val categorySelect: TranslatedSelectBox
    private val sortInstalledSelect: TranslatedSelectBox
    private val sortOnlineSelect: TranslatedSelectBox

    var expanderChangeEvent: (()->Unit)? = null
    val expander: ExpanderTab

    init {
        val searchIcon = ImageGetter.getImage("OtherIcons/Search")
            .surroundWithCircle(50f, color = Color.CLEAR)

        sortInstalledSelect = TranslatedSelectBox(
            SortType.values().filter { sort -> sort != SortType.Stars }.map { sort -> sort.label },
            sortInstalled.label,
            BaseScreen.skin
        )
        sortInstalledSelect.onChange {
            sortInstalled = SortType.fromSelectBox(sortInstalledSelect)
            modManagementScreen.refreshInstalledModTable()
        }

        sortOnlineSelect = TranslatedSelectBox(
            SortType.values().map { sort -> sort.label },
            sortOnline.label,
            BaseScreen.skin
        )
        sortOnlineSelect.onChange {
            sortOnline = SortType.fromSelectBox(sortOnlineSelect)
            modManagementScreen.refreshOnlineModTable()
        }

        categorySelect = TranslatedSelectBox(
            ModCategories.asSequence().map { it.label }.toList(),
            category.label,
            BaseScreen.skin
        )
        categorySelect.onChange {
            category = ModCategories.fromSelectBox(categorySelect)
            modManagementScreen.refreshInstalledModTable()
            modManagementScreen.refreshOnlineModTable()
        }

        expander = ExpanderTab(
            "Sort and Filter",
            fontSize = Constants.defaultFontSize,
            startsOutOpened = false,
            defaultPad = 2.5f,
            headerPad = 15f,
            expanderWidth = 360f,
            onChange = { expanderChangeEvent?.invoke() }
        ) {
            it.background = BaseScreen.skinStrings.getUiBackground(
                "ModManagementOptions/ExpanderTab",
                tintColor = Color(0x203050ff)
            )
            it.pad(7.5f)
            it.add(Table().apply {
                add("Filter:".toLabel()).left()
                add(textField).pad(0f, 5f, 0f, 5f).growX()
                add(searchIcon).right()
            }).colspan(2).growX().padBottom(7.5f).row()
            it.add("Category:".toLabel()).left()
            it.add(categorySelect).right().padBottom(7.5f).row()
            it.add("Sort Current:".toLabel()).left()
            it.add(sortInstalledSelect).right().padBottom(7.5f).row()
            it.add("Sort Downloadable:".toLabel()).left()
            it.add(sortOnlineSelect).right().row()
        }

        searchIcon.touchable = Touchable.enabled
        searchIcon.onActivation {
            if (expander.isOpen) {
                modManagementScreen.refreshInstalledModTable()
                modManagementScreen.refreshOnlineModTable()
            } else {
                modManagementScreen.stage.keyboardFocus = textField
            }
            expander.toggle()
        }
        searchIcon.keyShortcuts.add(KeyCharAndCode.RETURN)
        searchIcon.addTooltip(KeyCharAndCode.RETURN, 18f)
    }

    fun getInstalledHeader() = installedHeaderText.tr() + " " + sortInstalled.symbols
    fun getOnlineHeader() = onlineHeaderText.tr() + " " + sortOnline.symbols

    fun installedHeaderClicked() {
        do {
            sortInstalled = sortInstalled.next()
        } while (sortInstalled == SortType.Stars)
        sortInstalledSelect.selected = TranslatedSelectBox.TranslatedString(sortInstalled.label)
        modManagementScreen.refreshInstalledModTable()
    }

    fun onlineHeaderClicked() {
        sortOnline = sortOnline.next()
        sortOnlineSelect.selected = TranslatedSelectBox.TranslatedString(sortOnline.label)
        modManagementScreen.refreshOnlineModTable()
    }
}

private fun getTextButton(nameString: String, topics: List<String>): TextButton {
    val categories = ArrayList<ModCategories.Category>()
    for (category in ModCategories) {
        if (category == ModCategories.default()) continue
        if (topics.contains(category.topic)) categories += category
    }

    val button = nameString.toTextButton()
    val topicString = categories.joinToString { it.label.tr() }
    if (categories.isNotEmpty()) {
        button.row()
        button.add(topicString.toLabel(fontSize = 14))
    }
    return button
}

/** Helper class holds combined mod info for ModManagementScreen, used for both installed and online lists
 *
 *  Note it is guaranteed either ruleset or repo are non-null, never both.
 */
class ModUIData private constructor(
    val name: String,
    val description: String,
    val ruleset: Ruleset?,
    val repo: Github.Repo?,
    var y: Float,
    var height: Float,
    var button: TextButton
) {
    var state = ModStateImages()  // visible only on the 'installed' side - todo?

    constructor(ruleset: Ruleset): this (
        ruleset.name,
        ruleset.getSummary().let {
            "Installed".tr() + (if (it.isEmpty()) "" else ": $it")
        },
        ruleset, null, 0f, 0f, getTextButton(ruleset.name, ruleset.modOptions.topics)
    )

    constructor(repo: Github.Repo, isUpdated: Boolean): this (
        repo.name,
        (repo.description ?: "-{No description provided}-".tr()) +
                "\n" + "[${repo.stargazers_count}]✯".tr(),
        null, repo, 0f, 0f,
        getTextButton(repo.name + (if (isUpdated) " - {Updated}" else ""), repo.topics)
    ) {
        state.hasUpdate = isUpdated
    }


    fun lastUpdated() = ruleset?.modOptions?.lastUpdated ?: repo?.pushed_at ?: ""
    fun stargazers() = repo?.stargazers_count ?: 0
    fun author() = ruleset?.modOptions?.author ?: repo?.owner?.login ?: ""

    fun matchesFilter(filter: ModManagementOptions.Filter): Boolean = when {
        !matchesCategory(filter) -> false
        filter.text.isEmpty() -> true
        name.contains(filter.text, true) -> true
        // description.contains(filterText, true) -> true // too many surprises as description is different in the two columns
        author().contains(filter.text, true) -> true
        else -> false
    }

    private fun matchesCategory(filter: ModManagementOptions.Filter): Boolean {
        if (filter.topic == ModCategories.default().topic)
            return true
        val modTopics = repo?.topics ?: ruleset?.modOptions?.topics!!
        return filter.topic in modTopics
    }
}

/** Helper class keeps references to decoration images of installed mods to enable dynamic visibility
 * (actually we do not use isVisible but refill a container selectively which allows the aggregate height to adapt and the set to center vertically)
 * @param visualImage   image indicating _enabled as permanent visual mod_
 * @param hasUpdateImage  image indicating _online mod has been updated_
 */
class ModStateImages (
    isVisual: Boolean = false,
    isUpdated: Boolean = false,
    private val visualImage: Image = ImageGetter.getImage("UnitPromotionIcons/Scouting"),
    private val hasUpdateImage: Image = ImageGetter.getImage("OtherIcons/Mods")
) {
    /** The table containing the indicators (one per mod, narrow, arranges up to three indicators vertically) */
    val container: Table = Table().apply { defaults().size(20f).align(Align.topLeft) }
    // mad but it's really initializing with the primary constructor parameter and not calling update()
    var isVisual: Boolean = isVisual
        set(value) { if (field!=value) { field = value; update() } }
    var hasUpdate: Boolean = isUpdated
        set(value) { if (field!=value) { field = value; update() } }
    private val spacer = Table().apply { width = 20f; height = 0f }

    fun update() {
        container.run {
            clear()
            if (isVisual) add(visualImage).row()
            if (hasUpdate) add(hasUpdateImage).row()
            if (!isVisual && !hasUpdate) add(spacer)
            pack()
        }
    }

    fun sortWeight() = when {
        hasUpdate && isVisual -> 3
        hasUpdate -> 2
        isVisual -> 1
        else -> 0
    }
}
