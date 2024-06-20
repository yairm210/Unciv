package com.unciv.ui.screens.modmanager

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.models.metadata.ModCategories
import com.unciv.models.translations.tr
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onChange
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.widgets.TranslatedSelectBox
import kotlin.math.sign

/**
 * Helper class for Mod Manager - filtering and sorting.
 *
 * This isn't a UI Widget, but offers one: [expander] can be used to offer filtering and sorting options.
 * It holds the variables [sortInstalled] and [sortOnline] for the [modManagementScreen] and knows
 * how to sort collections of [ModUIData] by providing comparators.
 */
internal class ModManagementOptions(private val modManagementScreen: ModManagementScreen) {
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
            10 * (mod2.stateSortWeight() - mod1.stateSortWeight()) + mod1.name.compareTo(mod2.name, true).sign
        }

        const val installedHeaderText = "Current mods"
        const val onlineHeaderText = "Downloadable mods"
    }

    enum class SortType(
        val label: String,
        val symbols: String,
        val comparator: Comparator<in ModUIData>
    ) {
        Name("Name ${Fonts.sortUpArrow}", Fonts.sortUpArrow.toString(), sortByName),
        NameDesc("Name ${Fonts.sortDownArrow}", Fonts.sortDownArrow.toString(), sortByNameDesc),
        Date("Date ${Fonts.sortUpArrow}", "${Fonts.clock}${Fonts.sortUpArrow}", sortByDate),
        DateDesc("Date ${Fonts.sortDownArrow}", "${Fonts.clock}${Fonts.sortDownArrow}", sortByDateDesc),
        Stars("Stars ${Fonts.sortDownArrow}", "${Fonts.star}${Fonts.sortDownArrow}", sortByStars),
        Status("Status ${Fonts.sortDownArrow}", "${Fonts.status}${Fonts.sortDownArrow}", sortByStatus)
        ;
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
