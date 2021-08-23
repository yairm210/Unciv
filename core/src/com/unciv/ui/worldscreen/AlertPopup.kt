package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.models.translations.fillPlaceholders
import com.unciv.ui.trade.LeaderIntroTable
import com.unciv.ui.utils.*

/**
 * [Popup] communicating events other than trade offers to the player.
 * (e.g. First Contact, Wonder built, Tech researched,...) 
 *
 * Called in [WorldScreen].update, which pulls them from viewingCiv.popupAlerts.
 * 
 * @param worldScreen The parent screen
 * @param popupAlert The [PopupAlert] entry to present
 * 
 * @see AlertType
 */
class AlertPopup(val worldScreen: WorldScreen, val popupAlert: PopupAlert): Popup(worldScreen) {
    fun getCloseButton(text: String, key: Char = Char.MIN_VALUE, action: (() -> Unit)? = null): TextButton {
        // Popup.addCloseButton is close but AlertPopup needs the flexibility to add these inside a wrapper
        val button = text.toTextButton()
        val buttonAction = {
            if (action != null) action()
            worldScreen.shouldUpdate = true
            close()
        }
        button.onClick(buttonAction)
        if (key == Char.MIN_VALUE) {
            keyPressDispatcher[KeyCharAndCode.BACK] = buttonAction
            keyPressDispatcher[KeyCharAndCode.SPACE] = buttonAction
        } else {
            keyPressDispatcher[key] = buttonAction
        }
        return button
    }

    fun addLeaderName(civInfo: CivilizationInfo) {
        add(LeaderIntroTable(civInfo))
        addSeparator()
    }

    init {

        when (popupAlert.type) {
            AlertType.WarDeclaration -> {
                val civInfo = worldScreen.gameInfo.getCivilization(popupAlert.value)
                addLeaderName(civInfo)
                addGoodSizedLabel(civInfo.nation.declaringWar).row()
                val responseTable = Table()
                responseTable.defaults().pad(0f, 5f)
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
                addLeaderName(civInfo)
                if (civInfo.isCityState()) {
                    addGoodSizedLabel("We have encountered the City-State of [${nation.name}]!").row()
                    add(getCloseButton("Excellent!"))
                } else {
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
                    val liberateAction = {
                        city.liberateCity(conqueringCiv)
                        worldScreen.shouldUpdate = true
                        close()
                    }        
                    val liberateText = "Liberate (city returns to [originalOwner])".fillPlaceholders(city.foundingCiv)
                    add(liberateText.toTextButton().onClick(function = liberateAction)).row()
                    keyPressDispatcher['l'] = liberateAction
                    addGoodSizedLabel("Liberating a city returns it to its original owner, giving you a massive relationship boost with them!")
                    addSeparator()
                }

                if (conqueringCiv.isOneCityChallenger()) {
                    val destroyAction = {
                        city.puppetCity(conqueringCiv)
                        city.destroyCity()
                        worldScreen.shouldUpdate = true
                        close()
                    }
                    add("Destroy".toTextButton().onClick(function = destroyAction)).row()
                    keyPressDispatcher['d'] = destroyAction
                    addGoodSizedLabel("Destroying the city instantly razes the city to the ground.").row()
                } else {
                    val annexAction = {
                        city.puppetCity(conqueringCiv)
                        city.annexCity()
                        worldScreen.shouldUpdate = true
                        close()
                    }
                    add("Annex".toTextButton().onClick(function = annexAction)).row()
                    keyPressDispatcher['a'] = annexAction
                    addGoodSizedLabel("Annexed cities become part of your regular empire.").row()
                    addGoodSizedLabel("Their citizens generate 2x the unhappiness, unless you build a courthouse.").row()
                    addSeparator()

                    val puppetAction = {
                        city.puppetCity(conqueringCiv)
                        worldScreen.shouldUpdate = true
                        close()
                    }
                    add("Puppet".toTextButton().onClick(function = puppetAction) ).row()
                    keyPressDispatcher['p'] = puppetAction
                    addGoodSizedLabel("Puppeted cities do not increase your tech or policy cost, but their citizens generate 1.5x the regular unhappiness.").row()
                    addGoodSizedLabel("You have no control over the the production of puppeted cities.").row()
                    addGoodSizedLabel("Puppeted cities also generate 25% less Gold and Science.").row()
                    addGoodSizedLabel("A puppeted city can be annexed at any time.").row()
                    addSeparator()

                    val razeAction = {
                        city.puppetCity(conqueringCiv)
                        city.annexCity()
                        city.isBeingRazed = true
                        worldScreen.shouldUpdate = true
                        close()
                    }
                    add("Raze".toTextButton().apply {
                        if (!city.canBeDestroyed(justCaptured = true)) disable()
                        else {
                            onClick(function = razeAction)
                            keyPressDispatcher['r'] = razeAction
                        } 
                    }).row()
                    addGoodSizedLabel("Razing the city annexes it, and starts razing the city to the ground.").row()
                    addGoodSizedLabel("The population will gradually dwindle until the city is destroyed.").row()
                }
            }
            AlertType.BorderConflict -> {
                val civInfo = worldScreen.gameInfo.getCivilization(popupAlert.value)
                addLeaderName(civInfo)
                addGoodSizedLabel("Remove your troops in our border immediately!").row()
                val responseTable = Table()
                responseTable.defaults().pad(0f, 5f)
                responseTable.add(getCloseButton("Sorry."))
                responseTable.add(getCloseButton("Never!"))
                add(responseTable)
            }
            AlertType.DemandToStopSettlingCitiesNear -> {
                val otherciv = worldScreen.gameInfo.getCivilization(popupAlert.value)
                val playerDiploManager = worldScreen.viewingCiv.getDiplomacyManager(otherciv)
                addLeaderName(otherciv)
                addGoodSizedLabel("Please don't settle new cities near us.").row()
                add(getCloseButton("Very well, we shall look for new lands to settle.", 'y') {
                    playerDiploManager.agreeNotToSettleNear()
                }).row()
                add(getCloseButton("We shall do as we please.", 'n') {
                    playerDiploManager.refuseDemandNotToSettleNear()
                }).row()
            }
            AlertType.CitySettledNearOtherCivDespiteOurPromise -> {
                val otherciv = worldScreen.gameInfo.getCivilization(popupAlert.value)
                addLeaderName(otherciv)
                addGoodSizedLabel("We noticed your new city near our borders, despite your promise. This will have....implications.").row()
                add(getCloseButton("Very well."))
            }
            AlertType.WonderBuilt -> {
                val wonder = worldScreen.gameInfo.ruleSet.buildings[popupAlert.value]!!
                addGoodSizedLabel(wonder.name)
                addSeparator()
                if(ImageGetter.wonderImageExists(wonder.name)) {    // Wonder Graphic exists
                    if(worldScreen.stage.height * 3 > worldScreen.stage.width * 4) {    // Portrait
                        add(ImageGetter.getWonderImage(wonder.name))
                            .width(worldScreen.stage.width / 1.5f)
                            .height(worldScreen.stage.width / 3)
                            .row()
                    }
                    else {  // Landscape (or squareish)
                        add(ImageGetter.getWonderImage(wonder.name))
                            .width(worldScreen.stage.width / 2.5f)
                            .height(worldScreen.stage.width / 5)
                            .row()
                    }
                } else {    // Fallback
                    add(ImageGetter.getConstructionImage(wonder.name).surroundWithCircle(100f)).pad(20f).row()
                }

                val centerTable = Table()
                centerTable.add(wonder.quote.toLabel().apply { wrap = true }).width(worldScreen.stage.width / 3).pad(10f)
                centerTable.add(wonder.getShortDescription(worldScreen.gameInfo.ruleSet)
                        .toLabel().apply { wrap = true }).width(worldScreen.stage.width / 3).pad(10f)
                add(centerTable).row()
                add(getCloseButton(Constants.close))
            }
            AlertType.TechResearched -> {
                val gameBasics = worldScreen.gameInfo.ruleSet
                val tech = gameBasics.technologies[popupAlert.value]!!
                addGoodSizedLabel(tech.name)
                addSeparator()
                val centerTable = Table()
                centerTable.add(tech.quote.toLabel().apply { wrap = true }).width(worldScreen.stage.width / 3)
                centerTable.add(ImageGetter.getTechIconGroup(tech.name, 100f)).pad(20f)
                val descriptionScroll = ScrollPane(tech.getDescription(gameBasics).toLabel().apply { wrap = true })
                centerTable.add(descriptionScroll).width(worldScreen.stage.width / 3).maxHeight(worldScreen.stage.height / 2)
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
                add(getCloseButton("We are not interested.", 'n')).row()
                add(getCloseButton("Declare Friendship ([30] turns)", 'y') {
                    playerDiploManager.signDeclarationOfFriendship()
                }).row()
            }
            AlertType.StartIntro -> {
                val civInfo = worldScreen.viewingCiv
                addLeaderName(civInfo)
                addGoodSizedLabel(civInfo.nation.startIntroPart1).row()
                addGoodSizedLabel(civInfo.nation.startIntroPart2).row()
                add(getCloseButton("Let's begin!"))
            }
        }
    }

    override fun close() {
        worldScreen.viewingCiv.popupAlerts.remove(popupAlert)
        super.close()
    }
}
