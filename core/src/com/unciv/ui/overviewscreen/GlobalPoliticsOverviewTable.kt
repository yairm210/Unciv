package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.WondersInfo
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.worldscreen.WorldScreen

class GlobalPoliticsOverviewTable (
    val worldScreen: WorldScreen,
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    init {
        val tablePadding = 30f
        defaults().pad(tablePadding).top()

        add(createGlobalPoliticsTable())
    }

    private fun createGlobalPoliticsTable(): Table {
        val globalPoliticsTable = Table(skin)

        val civs = mutableListOf<CivilizationInfo>()
        civs.add(viewingPlayer)
        civs.addAll(viewingPlayer.getKnownCivs())
        for (civ in civs) {
            if (civ.isBarbarian() || civ.isCityState() || civ.isSpectator()) continue
            val civTable = Table(skin)

            // civ image
            val civIndicator = ImageGetter.getNationIndicator(civ.nation, 100f)
            civTable.add(civIndicator)

            // info about civ
            val civInfoTable = Table(skin)
            val leaderName = civ.getLeaderDisplayName().removeSuffix(" of " + civ.civName)
            civInfoTable.add(leaderName.toLabel()).row()
            civInfoTable.add(civ.civName.toLabel()).row()
            civInfoTable.add(civ.tech.era.name.toLabel()).row()
            civTable.add(civInfoTable)

            // policies
            val policiesTable = Table(skin)
            for (policy in civ.policies.branchCompletionMap) {
                if (policy.value != 0)
                    policiesTable.add(policy.key.name + ": " + policy.value).row()
            }
            civTable.add(policiesTable)

            // wonders
            civTable.add(getWondersOfCivTable(civ))

            //politics
            civTable.add(getPoliticsOfCivTable(civ))

            globalPoliticsTable.add(civTable).row()
        }

        return globalPoliticsTable
    }

    private fun getWondersOfCivTable(civ: CivilizationInfo): Table {
        val wonderTable = Table(skin)
        val wondersInfo = WondersInfo()
        val allWorldWonders = wondersInfo.collectInfo()

        for (wonder in allWorldWonders) {
            if (wonder.civ?.civName == civ.civName) {
                val wonderName = wonder.name.toLabel()
                if (wonder.location != null) {
                    wonderName.onClick {
                        val worldScreen = UncivGame.Current.resetToWorldScreen()
                        worldScreen.mapHolder.setCenterPosition(wonder.location.position)
                    }
                }
                wonderTable.add(wonderName).row()
            }
        }
        return wonderTable
    }

    private fun getPoliticsOfCivTable(civ: CivilizationInfo): Table {
        val politicsTable = Table(skin)

        // wars
        for (enemy in civ.atWarWith) {
            val warText = "At war with $enemy".toLabel()
            warText.color = Color.RED
            politicsTable.add(warText).row()
        }
        politicsTable.row()

        // declaration of friendships
        for (friend in civ.friendCivs) {
            val friendtext = ("Friends with " + civ.civName).toLabel()
            friendtext.color = Color.GREEN
            val turnsLeftText = ("(" + (viewingPlayer.gameInfo.turns - friend.value) + " Turns Left )").toLabel()
            politicsTable.add(friendtext)
            politicsTable.add(turnsLeftText).row()
        }
        politicsTable.row()

        // denounced civs
        for (denouncedCiv in civ.denouncedCivs) {
            val denouncedText = ("Denounced " + denouncedCiv.key).toLabel()
            denouncedText.color = Color.RED
            val turnsLeftText = ("(" + (viewingPlayer.gameInfo.turns - denouncedCiv.value) + " Turns Left )").toLabel()
            politicsTable.add(denouncedText)
            politicsTable.add(turnsLeftText).row()
        }
        politicsTable.row()

        //allied CS
        for (cityState in gameInfo.getAliveCityStates()) {
            if (cityState.diplomacy[civ.civName]?.relationshipLevel() == RelationshipLevel.Ally) {
                val alliedText = "Allied with ${cityState.civName}".toLabel()
                alliedText.color = Color.GREEN
                politicsTable.add(alliedText).row()
            }
        }

        return politicsTable
    }
}
