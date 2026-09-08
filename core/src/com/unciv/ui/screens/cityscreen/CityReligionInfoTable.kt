package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.models.Religion
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.Portrait
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.view.ForeignCityView

class CityReligionInfoTable(
    private val cityView: ForeignCityView,
    showMajority: Boolean = false
) : Table(BaseScreen.skin) {

    init {
        val gridColor = Color.DARK_GRAY
        val followers = cityView.getNumberOfFollowers()
        val futurePressures = cityView.getPressuresFromSurroundingCities()

        if (showMajority) {
            val majorityReligion = cityView.getMajorityReligion()
            val (iconName, label) = getIconAndLabel(majorityReligion)
            add(linkedReligionIcon(iconName, majorityReligion?.name)).pad(5f)
            add()  // skip vertical separator
            add("Majority Religion: [$label]".toLabel()).colspan(3).center().row()
        }

        val holyCityOf = cityView.getReligionThisIsTheHolyCityOf()
        if (holyCityOf != null) {
            val (iconName, label) = getIconAndLabel(holyCityOf)
            add(linkedReligionIcon(iconName, holyCityOf)).pad(5f)
            add()
            if (!cityView.isBlockedHolyCity()) {
                add("Holy City of: [$label]".toLabel()).colspan(3).center().row()
            } else {
                add("Former Holy City of: [$label]".toLabel()).colspan(3).center().row()
            }
        }

        if (!followers.isEmpty()) {
            add().pad(5f)  // column for icon
            addSeparatorVertical(gridColor)
            add("Followers".toLabel()).pad(5f)
            addSeparatorVertical(gridColor)
            add("Pressure".toLabel()).pad(5f).row()
            addSeparator(gridColor)

            for ((religion, followerCount) in followers.asSequence().sortedByDescending { it.value }) {
                val iconName = cityView.getReligion(religion)!!.getIconName()
                add(linkedReligionIcon(iconName, religion)).pad(5f)
                addSeparatorVertical(gridColor)
                add(followerCount.toLabel()).pad(5f)
                addSeparatorVertical(gridColor)
                if (futurePressures.containsKey(religion))
                    add(("+ [${futurePressures[religion]}] pressure").toLabel()).pad(5f)
                else
                    add()
                row()
            }
        }
    }

    private fun getIconAndLabel(religionName: String?) =
        getIconAndLabel(cityView.getReligion(religionName))
    private fun getIconAndLabel(religion: Religion?): Pair<String, String> {
        return if (religion == null) "Religion" to "None"
            else religion.getIconName() to religion.getReligionDisplayName()
    }
    private fun linkedReligionIcon(iconName: String, religion: String?): Portrait {
        val icon = ImageGetter.getReligionPortrait(iconName, 30f)
        if (religion == null) return icon
        if (religion == iconName)
            icon.onClick {
                GUI.getWorldScreen().openEmpireOverview(EmpireOverviewCategories.Religion, religion)
            }
        else // This is used only for Pantheons
            icon.onClick {
                GUI.openCivilopedia("Belief/$religion")
            }
        return icon
    }

    fun asExpander(onChange: (()->Unit)?): ExpanderTab {
        val (icon, label) = getIconAndLabel(cityView.getMajorityReligion())
        return ExpanderTab(
                title = "Majority Religion: [$label]",
                fontSize = Constants.defaultFontSize,
                icon = ImageGetter.getReligionPortrait(icon, 30f),
                defaultPad = 0f,
                persistenceID = "CityStatsTable.Religion",
                startsOutOpened = false,
                toggleKey = KeyboardBinding.ReligionDetail,
                onChange = onChange
            ) {
                defaults().center().pad(5f)
                it.add(this)
            }
    }
}
