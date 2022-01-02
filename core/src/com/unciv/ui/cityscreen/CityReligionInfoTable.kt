package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.city.CityInfoReligionManager
import com.unciv.models.Religion
import com.unciv.ui.utils.*

class CityReligionInfoTable(
    private val religionManager: CityInfoReligionManager,
    showMajority: Boolean = false
) : Table(BaseScreen.skin) {
    private val gameInfo = religionManager.cityInfo.civInfo.gameInfo

    init {
        val gridColor = Color.DARK_GRAY
        val followers = religionManager.getNumberOfFollowers()
        val futurePressures = religionManager.getPressuresFromSurroundingCities()

        if (showMajority) {
            val (icon, label) = getIconAndLabel(religionManager.getMajorityReligion())
            add(ImageGetter.getCircledReligionIcon(icon, 30f)).pad(5f)
            add()  // skip vertical separator
            add("Majority Religion: [$label]".toLabel()).colspan(3).center().row()
        }

        val (icon, label) = getIconAndLabel(religionManager.religionThisIsTheHolyCityOf)
        if (label != "None") {
            add(ImageGetter.getCircledReligionIcon(icon, 30f)).pad(5f)
            add()
            add("Holy city of: [$label]".toLabel()).colspan(3).center().row()
        }

        if (!followers.isEmpty()) {
            add().pad(5f)  // column for icon
            addSeparatorVertical(gridColor)
            add("Followers".toLabel()).pad(5f)
            addSeparatorVertical(gridColor)
            add("Pressure".toLabel()).pad(5f).row()
            addSeparator(gridColor)

            for ((religion, followerCount) in followers) {
                val iconName = gameInfo.religions[religion]!!.getIconName()
                add(ImageGetter.getCircledReligionIcon(iconName, 30f)).pad(5f)
                addSeparatorVertical(gridColor)
                add(followerCount.toLabel()).pad(5f)
                addSeparatorVertical(gridColor)
                if (futurePressures.containsKey(religion))
                    add(("+ [${futurePressures[religion]!!}] pressure").toLabel()).pad(5f)
                else
                    add()
                row()
            }
        }
    }

    private fun getIconAndLabel(religionName: String?) =
        getIconAndLabel(gameInfo.religions[religionName])
    private fun getIconAndLabel(religion: Religion?): Pair<String, String> {
        return if (religion == null) "Religion" to "None" 
            else religion.getIconName() to religion.getReligionDisplayName()
    }

    fun asExpander(onChange: (()->Unit)?): ExpanderTab {
        val (icon, label) = getIconAndLabel(religionManager.getMajorityReligion())
        return ExpanderTab(
                title = "Majority Religion: [$label]",
                fontSize = 18,
                icon = ImageGetter.getCircledReligionIcon(icon, 30f),
                defaultPad = 0f,
                persistenceID = "CityStatsTable.Religion",
                startsOutOpened = false,
                onChange = onChange
            ) {
                defaults().center().pad(5f)
                it.add(this)
            }
    }
}
