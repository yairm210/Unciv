package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.WondersInfo
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.addSeparatorVertical
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel

class GlobalPoliticsOverviewTable (
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    init {
        createGlobalPoliticsTable()
    }

    private fun createGlobalPoliticsTable() {
        val civilizations = mutableListOf<CivilizationInfo>()
        civilizations.add(viewingPlayer)
        civilizations.addAll(viewingPlayer.getKnownCivs())
        civilizations.removeAll(civilizations.filter { it.isBarbarian() || it.isCityState() || it.isSpectator() })
        for (civ in civilizations) {
            // civ image
            add(ImageGetter.getNationIndicator(civ.nation, 100f)).pad(20f)

            addSeparatorVertical(Color.GRAY)

            // info about civ
            add(getCivInfoTable(civ)).pad(20f)

            addSeparatorVertical(Color.GRAY)

            // policies
            add(getPoliciesTable(civ)).pad(20f)

            addSeparatorVertical(Color.GRAY)

            // wonders
            add(getWondersOfCivTable(civ)).pad(20f)

            addSeparatorVertical(Color.GRAY)

            //politics
            add(getPoliticsOfCivTable(civ)).pad(20f)

            if (civilizations.indexOf(civ) != civilizations.lastIndex)
                addSeparator(Color.GRAY)
        }
    }

    private fun getCivInfoTable(civ: CivilizationInfo): Table {
        val civInfoTable = Table(skin)
        val leaderName = civ.getLeaderDisplayName().removeSuffix(" of " + civ.civName)
        civInfoTable.add(leaderName.toLabel(fontSize = 30)).row()
        civInfoTable.add(civ.civName.toLabel()).row()
        civInfoTable.add(civ.tech.era.name.toLabel()).row()
        return civInfoTable
    }

    private fun getPoliciesTable(civ: CivilizationInfo): Table {
        val policiesTable = Table(skin)
        policiesTable.add("Social Policies:".toLabel(fontSize = 30)).row()
        for (policy in civ.policies.branchCompletionMap) {
            if (policy.value != 0)
                policiesTable.add("${policy.key.name}: ${policy.value}".toLabel()).row()
        }
        return policiesTable
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
            val friendtext = "Friends with ${civ.civName}".toLabel()
            friendtext.color = Color.GREEN
            val turnsLeftText = "( ${viewingPlayer.gameInfo.turns - friend.value} Turns Left)".toLabel()
            politicsTable.add(friendtext)
            politicsTable.add(turnsLeftText).row()
        }
        politicsTable.row()

        // denounced civs
        for (denouncedCiv in civ.denouncedCivs) {
            val denouncedText = "Denounced ${denouncedCiv.key}".toLabel()
            denouncedText.color = Color.RED
            val turnsLeftText = "( ${(viewingPlayer.gameInfo.turns - denouncedCiv.value)} Turns Left)".toLabel()
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
