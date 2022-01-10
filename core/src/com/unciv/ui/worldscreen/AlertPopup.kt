package com.unciv.ui.worldscreen

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.*
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.trade.LeaderIntroTable
import com.unciv.ui.utils.*
import java.util.*

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
        val music = UncivGame.Current.musicController

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
                music.chooseTrack(civInfo.civName, MusicMood.War, MusicTrackChooserFlags.setSpecific)
            }
            AlertType.Defeated -> {
                val civInfo = worldScreen.gameInfo.getCivilization(popupAlert.value)
                addLeaderName(civInfo)
                addGoodSizedLabel(civInfo.nation.defeated).row()
                add(getCloseButton("Farewell."))
                music.chooseTrack(civInfo.civName, MusicMood.Defeat, EnumSet.of(MusicTrackChooserFlags.SuffixMustMatch))
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
                    music.chooseTrack(civInfo.civName, MusicMood.themeOrPeace, MusicTrackChooserFlags.setSpecific)
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
                    addLiberateOption(city.foundingCiv, liberateAction)
                    addSeparator()
                }

                if (conqueringCiv.isOneCityChallenger()) {
                    val destroyAction = {
                        city.puppetCity(conqueringCiv)
                        city.destroyCity()
                        worldScreen.shouldUpdate = true
                        close()
                    }
                    addDestroyOption(destroyAction)
                } else {
                    val annexAction = {
                        city.puppetCity(conqueringCiv)
                        city.annexCity()
                        worldScreen.shouldUpdate = true
                        close()
                    }
                    addAnnexOption(annexAction)
                    addSeparator()

                    val puppetAction = {
                        city.puppetCity(conqueringCiv)
                        worldScreen.shouldUpdate = true
                        close()
                    }
                    addPuppetOption(puppetAction)
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
            AlertType.DiplomaticMarriage -> {
                val city = worldScreen.gameInfo.getCities().first { it.id == popupAlert.value }
                addGoodSizedLabel(city.name.tr() + ": " + "What would you like to do with the city?".tr(), 24) // Add name because there might be several cities
                    .padBottom(20f).row()
                val marryingCiv = worldScreen.gameInfo.currentPlayerCiv

                if (marryingCiv.isOneCityChallenger()) {
                    val destroyAction = {
                        city.destroyCity(overrideSafeties = true)
                        worldScreen.shouldUpdate = true
                        close()
                    }
                    addDestroyOption(destroyAction)
                } else {
                    val annexAction = {
                        city.annexCity()
                        close()
                    }
                    addAnnexOption(annexAction)
                    addSeparator()

                    val puppetAction = {
                        city.isPuppet = true
                        city.cityStats.update()
                        worldScreen.shouldUpdate = true
                        close()
                    }
                    addPuppetOption(puppetAction)
                }
            }
            AlertType.BulliedProtectedMinor -> {
                val involvedCivs = popupAlert.value.split('@')
                val bully = worldScreen.gameInfo.getCivilization(involvedCivs[0])
                val cityState = worldScreen.gameInfo.getCivilization(involvedCivs[1])
                val player = worldScreen.viewingCiv
                addLeaderName(bully)

                val text = if (bully.getDiplomacyManager(player).relationshipLevel() >= RelationshipLevel.Neutral) // Nice message
                    "I've been informed that my armies have taken tribute from [${cityState.civName}], a city-state under your protection.\nI assure you, this was quite unintentional, and I hope that this does not serve to drive us apart."
                else    // Nasty message
                    "We asked [${cityState.civName}] for a tribute recently and they gave in.\nYou promised to protect them from such things, but we both know you cannot back that up."
                addGoodSizedLabel(text).row()

                add(getCloseButton("You'll pay for this!", 'y') {
                    player.getDiplomacyManager(bully).sideWithCityState()
                }).row()
                add(getCloseButton("Very well.", 'n') {
                    if(cityState.cities.isEmpty())
                        player.addNotification("You have broken your Pledge to Protect [${cityState.civName}]!", cityState.civName)
                    else {
                        val capitalLocation = LocationAction(listOf(cityState.getCapital().location))
                        player.addNotification("You have broken your Pledge to Protect [${cityState.civName}]!", capitalLocation, cityState.civName)
                    }
                    cityState.removeProtectorCiv(player, forced = true)
                }).row()
            }
            AlertType.AttackedProtectedMinor -> {
                val involvedCivs = popupAlert.value.split('@')
                val attacker = worldScreen.gameInfo.getCivilization(involvedCivs[0])
                val cityState = worldScreen.gameInfo.getCivilization(involvedCivs[1])
                val player = worldScreen.viewingCiv
                addLeaderName(attacker)

                val text = if (attacker.getDiplomacyManager(player).relationshipLevel() >= RelationshipLevel.Neutral) // Nice message
                    "It's come to my attention that I may have attacked [${cityState.civName}], a city-state under your protection.\nWhile it was not my goal to be at odds with your empire, this was deemed a necessary course of action."
                else    // Nasty message
                    "I thought you might like to know that I've launched an invasion of one of your little pet states.\nThe lands of [${cityState.civName}] will make a fine addition to my own."
                addGoodSizedLabel(text).row()

                add(getCloseButton("You'll pay for this!", 'y') {
                    player.getDiplomacyManager(attacker).sideWithCityState()
                }).row()
                add(getCloseButton("Very well.", 'n') {
                    if(cityState.cities.isEmpty())
                        player.addNotification("You have broken your Pledge to Protect [${cityState.civName}]!", cityState.civName)
                    else {
                        val capitalLocation = LocationAction(listOf(cityState.getCapital().location))
                        player.addNotification("You have broken your Pledge to Protect [${cityState.civName}]!", capitalLocation, cityState.civName)
                    }
                    cityState.removeProtectorCiv(player, forced = true)
                }).row()
            }
            AlertType.RecapturedCivilian -> addRecapturedCivilianTable()
        }
    }

    private fun addRecapturedCivilianTable() {
        val position = Vector2().fromString(popupAlert.value)
        val tile = worldScreen.gameInfo.tileMap[position]
        val capturedUnit = tile.civilianUnit // This has got to be it
        if (capturedUnit == null) { // the unit disappeared somehow? maybe a modded action?
            close()
            return
        }
        val originalOwner = worldScreen.gameInfo.getCivilization(capturedUnit.originalOwner!!)
        val captor = worldScreen.viewingCiv

        addGoodSizedLabel("Return [${capturedUnit.name}] to [${originalOwner.civName}]?")
        addSeparator()
        addGoodSizedLabel("The [${capturedUnit.name}] we liberated originally belonged to [${originalOwner.civName}]. They will be grateful if we return it to them.").row()
        val responseTable = Table()
        responseTable.defaults()
            .pad(0f, 30f) // Small buttons, plenty of pad so we don't fat-finger it
        responseTable.add(getCloseButton("Yes", 'y') {
            // Return it to original owner
            val unitName = capturedUnit.baseUnit.name
            capturedUnit.destroy()
            val closestCity =
                originalOwner.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(tile) }
            if (closestCity != null) {
                // Attempt to place the unit near their nearest city
                originalOwner.placeUnitNearTile(closestCity.location, unitName)
            }

            if (originalOwner.isCityState()) {
                originalOwner.getDiplomacyManager(captor).addInfluence(45f)
            } else if (originalOwner.isMajorCiv()) {
                // No extra bonus from doing it several times
                originalOwner.getDiplomacyManager(captor)
                    .setModifier(DiplomaticModifiers.ReturnedCapturedUnits, 20f)
            }
        })
        responseTable.add(getCloseButton("No", 'n') {
            // Take it for ourselves
            // Settlers become workers at this point
            if (capturedUnit.hasUnique(UniqueType.FoundCity)) {
                capturedUnit.destroy()
                // This is so that future checks which check if a unit has been captured are caught give the right answer
                //  For example, in postBattleMoveToAttackedTile
                capturedUnit.civInfo = captor
                captor.placeUnitNearTile(tile.position, Constants.worker)
            } else
                capturedUnit.capturedBy(captor)
        }).row()
        add(responseTable)
    }

    private fun addDestroyOption(destroyAction: () -> Unit) {
        add("Destroy".toTextButton().onClick(function = destroyAction)).row()
        keyPressDispatcher['d'] = destroyAction
        addGoodSizedLabel("Destroying the city instantly razes the city to the ground.").row()
    }

    private fun addAnnexOption(annexAction: () -> Unit) {
        add("Annex".toTextButton().onClick(function = annexAction)).row()
        keyPressDispatcher['a'] = annexAction
        addGoodSizedLabel("Annexed cities become part of your regular empire.").row()
        addGoodSizedLabel("Their citizens generate 2x the unhappiness, unless you build a courthouse.").row()
    }

    private fun addPuppetOption(puppetAction: () -> Unit) {
        add("Puppet".toTextButton().onClick(function = puppetAction) ).row()
        keyPressDispatcher['p'] = puppetAction
        addGoodSizedLabel("Puppeted cities do not increase your tech or policy cost, but their citizens generate 1.5x the regular unhappiness.").row()
        addGoodSizedLabel("You have no control over the the production of puppeted cities.").row()
        addGoodSizedLabel("Puppeted cities also generate 25% less Gold and Science.").row()
        addGoodSizedLabel("A puppeted city can be annexed at any time.").row()
    }

    private fun addLiberateOption(foundingCiv: String, liberateAction: () -> Unit) {
        val liberateText = "Liberate (city returns to [originalOwner])".fillPlaceholders(foundingCiv)
        add(liberateText.toTextButton().onClick(function = liberateAction)).row()
        keyPressDispatcher['l'] = liberateAction
        addGoodSizedLabel("Liberating a city returns it to its original owner, giving you a massive relationship boost with them!")
    }

    override fun close() {
        worldScreen.viewingCiv.popupAlerts.remove(popupAlert)
        super.close()
    }
}
