package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class AlertPopup(val worldScreen: WorldScreen, val popupAlert: PopupAlert): Popup(worldScreen){
    fun getCloseButton(text: String, action: (() -> Unit)?=null): TextButton {
        val button = text.toTextButton()
        button.onClick {
            if(action!=null) action()
            worldScreen.shouldUpdate=true
            close()
        }
        return button
    }

    fun addLeaderName(civInfo : CivilizationInfo){
        val otherCivLeaderName = civInfo.getLeaderDisplayName()
        add(otherCivLeaderName.toLabel())
        addSeparator()
    }

    init {

        when(popupAlert.type){
            AlertType.WarDeclaration -> {
                val civInfo = worldScreen.gameInfo.getCivilization(popupAlert.value)
                addLeaderName(civInfo)
                addGoodSizedLabel(civInfo.nation.declaringWar).row()
                val responseTable = Table()
                responseTable.add(getCloseButton("You'll pay for this!"))
                responseTable.add(getCloseButton("Very well."))
                add(responseTable)
            }
            AlertType.Defeated -> {
                val civInfo = worldScreen.gameInfo.getCivilization(popupAlert.value)
                addLeaderName(civInfo)
                addGoodSizedLabel(civInfo.nation.defeated).row()
                add(getCloseButton("Farewell."))
            }
            AlertType.FirstContact -> {
                val civInfo = worldScreen.gameInfo.getCivilization(popupAlert.value)
                val nation = civInfo.nation
                if (civInfo.isCityState()) {
                    addLeaderName(civInfo)
                    addGoodSizedLabel("We have encountered the City-State of [${nation.name}]!").row()
                    add(getCloseButton("Excellent!"))
                } else {
                    addLeaderName(civInfo)
                    addGoodSizedLabel(nation.introduction).row()
                    add(getCloseButton("A pleasure to meet you."))
                }
            }
            AlertType.CityConquered -> {
                val city = worldScreen.gameInfo.getCities().first { it.id == popupAlert.value }
                addGoodSizedLabel("What would you like to do with the city?", 24)
                        .padBottom(20f).row()
                val conqueringCiv = worldScreen.gameInfo.currentPlayerCiv

                if (city.foundingCiv != ""
                        && city.civInfo.civName != city.foundingCiv // can't liberate if the city actually belongs to those guys
                        && conqueringCiv.civName != city.foundingCiv) { // or belongs originally to us
                    add("Liberate".toTextButton().onClick {
                        city.liberateCity(conqueringCiv)
                        worldScreen.shouldUpdate = true
                        close()
                    }).row()
                    addGoodSizedLabel("Liberating a city returns it to its original owner, giving you a massive relationship boost with them!")
                    addSeparator()
                }

                if (!conqueringCiv.isOneCityChallenger()) {

                    add("Annex".toTextButton().onClick {
                        city.puppetCity(conqueringCiv)
                        city.annexCity()
                        worldScreen.shouldUpdate = true
                        close()
                    }).row()
                    addGoodSizedLabel("Annexed cities become part of your regular empire.").row()
                    addGoodSizedLabel("Their citizens generate 2x the unhappiness, unless you build a courthouse.").row()
                    addSeparator()

                    add("Puppet".toTextButton().onClick {
                        city.puppetCity(conqueringCiv)
                        worldScreen.shouldUpdate = true
                        close()
                    }).row()
                    addGoodSizedLabel("Puppeted cities do not increase your tech or policy cost, but their citizens generate 1.5x the regular unhappiness.").row()
                    addGoodSizedLabel("You have no control over the the production of puppeted cities.").row()
                    addGoodSizedLabel("Puppeted cities also generate 25% less Gold and Science.").row()
                    addGoodSizedLabel("A puppeted city can be annexed at any time.").row()
                    addSeparator()


                    add("Raze".toTextButton().apply {
                        if (city.isOriginalCapital) disable()
                        else onClick {
                            city.puppetCity(conqueringCiv)
                            city.annexCity()
                            city.isBeingRazed = true
                            worldScreen.shouldUpdate = true
                            close()
                        }
                    }).row()
                    addGoodSizedLabel("Razing the city annexes it, and starts razing the city to the ground.").row()
                    addGoodSizedLabel("The population will gradually dwindle until the city is destroyed.").row()
                } else {

                    add("Destroy".toTextButton().onClick {
                        city.puppetCity(conqueringCiv)
                        city.destroyCity()
                        worldScreen.shouldUpdate = true
                        close()
                    }).row()
                    addGoodSizedLabel("Destroying the city instantly razes the city to the ground.").row()
                }
            }
            AlertType.BorderConflict -> {
                val civInfo = worldScreen.gameInfo.getCivilization(popupAlert.value)
                addLeaderName(civInfo)
                addGoodSizedLabel("Remove your troops in our border immediately!").row()
                val responseTable = Table()
                responseTable.add(getCloseButton("Sorry."))
                responseTable.add(getCloseButton("Never!"))
                add(responseTable)
            }
            AlertType.DemandToStopSettlingCitiesNear -> {
                val otherciv= worldScreen.gameInfo.getCivilization(popupAlert.value)
                val playerDiploManager = worldScreen.viewingCiv.getDiplomacyManager(otherciv)
                addLeaderName(otherciv)
                addGoodSizedLabel("Please don't settle new cities near us.").row()
                add(getCloseButton("Very well, we shall look for new lands to settle."){
                    playerDiploManager.agreeNotToSettleNear()
                }).row()
                add(getCloseButton("We shall do as we please.") {
                    playerDiploManager.refuseDemandNotToSettleNear()
                }).row()
            }
            AlertType.CitySettledNearOtherCivDespiteOurPromise -> {
                val otherciv= worldScreen.gameInfo.getCivilization(popupAlert.value)
                addLeaderName(otherciv)
                addGoodSizedLabel("We noticed your new city near our borders, despite your promise. This will have....implications.").row()
                add(getCloseButton("Very well."))
            }
            AlertType.WonderBuilt -> {
                val wonder = worldScreen.gameInfo.ruleSet.buildings[popupAlert.value]!!
                addGoodSizedLabel(wonder.name)
                addSeparator()
                val centerTable = Table()
                centerTable.add(wonder.quote.toLabel().apply { setWrap(true) }).width(worldScreen.stage.width/3)
                centerTable.add(ImageGetter.getConstructionImage(wonder.name).surroundWithCircle(100f)).pad(20f)
                centerTable.add(wonder.getShortDescription(worldScreen.gameInfo.ruleSet)
                        .toLabel().apply { setWrap(true) }).width(worldScreen.stage.width/3)
                add(centerTable).row()
                add(getCloseButton(Constants.close))
            }
            AlertType.TechResearched -> {
                val gameBasics = worldScreen.gameInfo.ruleSet
                val tech = gameBasics.technologies[popupAlert.value]!!
                addGoodSizedLabel(tech.name)
                addSeparator()
                val centerTable = Table()
                centerTable.add(tech.quote.toLabel().apply { setWrap(true) }).width(worldScreen.stage.width/3)
                centerTable.add(ImageGetter.getTechIconGroup(tech.name,100f)).pad(20f)
                centerTable.add(tech.getDescription(gameBasics).toLabel().apply { setWrap(true) }).width(worldScreen.stage.width/3)
                add(centerTable).row()
                add(getCloseButton(Constants.close))
            }
            AlertType.GoldenAge -> {
                addGoodSizedLabel("GOLDEN AGE")
                addSeparator()
                addGoodSizedLabel("Your citizens have been happy with your rule for so long that the empire enters a Golden Age!").row()
                add(getCloseButton(Constants.close))
            }
            AlertType.DeclarationOfFriendship -> {
                val otherciv = worldScreen.gameInfo.getCivilization(popupAlert.value)
                val playerDiploManager = worldScreen.viewingCiv.getDiplomacyManager(otherciv)
                addLeaderName(otherciv)
                addGoodSizedLabel("My friend, shall we declare our friendship to the world?").row()
                add(getCloseButton("We are not interested.")).row()
                add(getCloseButton("Declare Friendship ([30] turns)") {
                    playerDiploManager.signDeclarationOfFriendship()
                }).row()
            }
        }
    }

    override fun close(){
        worldScreen.viewingCiv.popupAlerts.remove(popupAlert)
        super.close()
    }
}
