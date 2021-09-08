package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.newgamescreen.TranslatedSelectBox
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ExpanderTab
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.toLabel
import kotlin.math.sign

class ModManagementOptions(private val modManagementScreen: ModManagementScreen) {
    companion object {
        val sortByName = Comparator { mod1, mod2: Ruleset -> mod1.name.compareTo(mod2.name) }
        val sortByNameDesc = Comparator { mod1, mod2: Ruleset -> -mod1.name.compareTo(mod2.name) }
        // lastUpdated is compared as string, but that should be OK as it's ISO format
        val sortByDate = Comparator { mod1, mod2: Ruleset -> mod1.modOptions.lastUpdated.compareTo(mod2.modOptions.lastUpdated) }
        val sortByDateDesc = Comparator { mod1, mod2: Ruleset -> -mod1.modOptions.lastUpdated.compareTo(mod2.modOptions.lastUpdated) }
        // comparators for stars or status need context not available here, those are special-cased in ModManagementScreen
    }
    enum class SortType(
        val label: String,
        val symbols: String,
        val comparator: Comparator<in Ruleset>? = null
    ) {
        Name("Name ￪", "￪", sortByName),
        NameDesc("Name ￬", "￬", sortByNameDesc),
        Date("Date ￪", "⌚￪", sortByDate),
        DateDesc("Date ￬", "⌚￬", sortByDateDesc),
        Stars("Stars ￬", "✯￬"),
        Status("Status ￬", "◉￬");

        fun next() = values()[(ordinal + 1) % values().size]
        fun getComparator(modScreen: ModManagementScreen): Comparator<in Ruleset> = comparator ?: when(this) {
            Status -> Comparator { mod1, mod2 ->
                10 * (modScreen.getModStateSortWeight(mod2.name) - modScreen.getModStateSortWeight(mod1.name))
                + mod1.name.compareTo(mod2.name).sign
            }
            Stars -> Comparator { mod1, mod2 ->
                10 * (modScreen.getStars(mod2.name) - modScreen.getStars(mod1.name))
                + mod1.name.compareTo(mod2.name).sign
            }
            else -> Comparator { _, _ -> 0 }
        }

    }

    var sortInstalled = SortType.Name
    var sortOnline = SortType.Stars

    val expander = ExpanderTab("Sort and Filter", 18, startsOutOpened = false, defaultPad = 5f, expanderWidth = 200f) {
        it.add(Table().apply {
            add("Filter:".toLabel()).left()
            add(TextField("", CameraStageBaseScreen.skin)).pad(0f,5f,0f,5f).growX()
            add(ImageGetter.getImage("OtherIcons/Search")).right()
        }).growX().padBottom(5f).row()
        val sortOptions = SortType.values().map {sort -> sort.label}
        it.add("Sort Current:".toLabel()).left()
        it.add(TranslatedSelectBox(
            sortOptions,
            sortInstalled.label,
            CameraStageBaseScreen.skin
        )).right().padBottom(5f).row()
        it.add("Sort Downloadable:".toLabel()).left()
        it.add(TranslatedSelectBox(
            sortOptions,
            sortOnline.label,
            CameraStageBaseScreen.skin
        )).right().row()
    }

    fun installedHeaderClicked() {
        do {
            sortInstalled = sortInstalled.next()
        } while (sortInstalled == SortType.Stars)
        modManagementScreen.refreshInstalledModTable()
    }

    fun onlineHeaderClicked() {
        do {
            sortOnline = sortOnline.next()
        } while (sortOnline == SortType.Status)
    }

}