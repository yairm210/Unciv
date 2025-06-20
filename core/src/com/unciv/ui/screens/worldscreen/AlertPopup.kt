package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.battle.BattleUnitCapture
import com.unciv.logic.city.City
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.CivilopediaAction
import com.unciv.logic.civilization.DiplomacyAction
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.*
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.diplomacyscreen.LeaderIntroTable
import com.unciv.ui.screens.victoryscreen.VictoryScreen
import java.util.EnumSet

/**
 * [Popup] communicating events other than trade offers to the player.
 * (e.g. First Contact, Wonder built, Tech researched,...)
 *
 * **Opens itself at the end of instantiation!**
 *
 * (In rare cases, it chooses not to: Mods making a RecapturedCivilian not find the unit as it was illegal and removed after the actual capture)
 *
 * Called in [WorldScreen].update, which pulls them from viewingCiv.popupAlerts.
 *
 * @param worldScreen The parent screen
 * @param popupAlert The [PopupAlert] entry to present
 *
 * @see AlertType
 *
 * Attention developers: This is a Popup with `Scrollability.WithoutButtons`, and that means the
 * content area has two parts - one scrolls and the bottom not. Use Popup's normal `add` for stuff that
 * should go to the upper scrolling part and all typical closing buttons should *only* use Popup's
 * add*Button methods - for a good exception see `addCityConquered`.
 * That also means colspan is independent for top and bottom, and you need no row() between them.
 */
class AlertPopup(
    private val worldScreen: WorldScreen,
    private val popupAlert: PopupAlert
): Popup(worldScreen) {

    //region convenience getters
    private val music get() = UncivGame.Current.musicController
    private val gameInfo get() = worldScreen.gameInfo
    private val viewingCiv get() = worldScreen.viewingCiv
    private val stageWidth get() = worldScreen.stage.width
    private val stageHeight get() = worldScreen.stage.height
    private fun getCiv(civName: String) = gameInfo.getCivilization(civName)
    private fun getCity(cityId: String) = gameInfo.getCities().first { it.id == cityId }
    //endregion

    // This redirects all addCloseButton uses with only text and no action to accept the space key
    private fun addCloseButton(text: String = Constants.close) =
        addCloseButton(text, KeyboardBinding.NextTurnAlternate, null)

    init {
        var shouldOpen = true

        // This makes the buttons fill up available width. See comments in #9559.
        // To implement a middle ground, I would either simply replace growX() with minWidth(240f) or so,
        // or replace the Popup.equalizeLastTwoButtonWidths() function with something intelligent not
        // limited to two buttons.
        bottomTable.defaults().growX()

        when (popupAlert.type) {
            // Cities
            AlertType.CityConquered -> addCityConquered()
            AlertType.CityTraded -> addCityTraded()
            AlertType.DiplomaticMarriage -> addDiplomaticMarriage()
            // Demands and diplomacy
            AlertType.FirstContact -> addFirstContact()
            AlertType.WarDeclaration -> shouldOpen = addWarDeclaration()
            AlertType.BorderConflict -> shouldOpen = addBorderConflict()
            AlertType.TilesStolen -> shouldOpen = addTilesStolen()
            
            // demands
            AlertType.DemandToStopSettlingCitiesNear -> shouldOpen = addDemandToStopSettlingCitiesNear()
            AlertType.CitySettledNearOtherCivDespiteOurPromise -> shouldOpen = addCitySettledNearOtherCivDespiteOurPromise()
            AlertType.DemandToStopSpreadingReligion -> shouldOpen = addDemandToStopSpreadingReligion()
            AlertType.ReligionSpreadDespiteOurPromise -> shouldOpen = addReligionSpreadDespiteOurPromise()
            AlertType.DemandToStopSpyingOnUs -> shouldOpen = addDemandToStopSendingSpiesToUs()
            AlertType.SpyingOnUsDespiteOurPromise -> shouldOpen = addSpyingOnUsDespiteOurPromise()
            
            
            AlertType.DeclarationOfFriendship -> shouldOpen = addDeclarationOfFriendship()
            AlertType.BulliedProtectedMinor, AlertType.AttackedProtectedMinor, AlertType.AttackedAllyMinor -> 
                shouldOpen = addBulliedOrAttackedProtectedOrAlliedMinor()
            AlertType.Defeated -> addDefeated()
            // We did stuff
            AlertType.WonderBuilt -> addWonderBuilt()
            AlertType.TechResearched -> addTechResearched()
            AlertType.GoldenAge -> addGoldenAge()
            AlertType.StartIntro -> addStartIntro()
            AlertType.RecapturedCivilian -> shouldOpen = addRecapturedCivilian()
            AlertType.GameHasBeenWon -> addGameHasBeenWon()
            AlertType.Event -> shouldOpen = addEvent()
        }
        if (shouldOpen) open()
        else viewingCiv.popupAlerts.remove(popupAlert)
    }

    //region AlertType handlers

    private fun addBorderConflict(): Boolean {
        val civInfo = getCiv(popupAlert.value)
        if (civInfo.isDefeated()) return false
        addLeaderName(civInfo)
        addGoodSizedLabel("Remove your troops in our border immediately!")
        addCloseButton("Sorry.", KeyboardBinding.Confirm)
        addCloseButton("Never!", KeyboardBinding.Cancel)
        return true
    }
    
    private fun addTilesStolen(): Boolean {
        val civInfo = getCiv(popupAlert.value)
        if (civInfo.isDefeated()) return false
        addLeaderName(civInfo)
        addGoodSizedLabel("Those lands were not yours to take. This has not gone unnoticed.")
        addCloseButton()
        return true
    }

    private fun addBulliedOrAttackedProtectedOrAlliedMinor(): Boolean {
        val involvedCivs = popupAlert.value.split('@')
        val bullyOrAttacker = getCiv(involvedCivs[0])
        if (bullyOrAttacker.isDefeated()) return false
        val cityState = getCiv(involvedCivs[1])
        val player = viewingCiv
        addLeaderName(bullyOrAttacker)

        val isAtLeastNeutral = bullyOrAttacker.getDiplomacyManager(player)!!.isRelationshipLevelGE(RelationshipLevel.Neutral)
        val text = when {
            popupAlert.type == AlertType.BulliedProtectedMinor && isAtLeastNeutral ->  // Nice message
                "I've been informed that my armies have taken tribute from [${cityState.civName}], a city-state under your protection.\nI assure you, this was quite unintentional, and I hope that this does not serve to drive us apart."
            popupAlert.type == AlertType.BulliedProtectedMinor ->  // Nasty message
                "We asked [${cityState.civName}] for a tribute recently and they gave in.\nYou promised to protect them from such things, but we both know you cannot back that up."
            isAtLeastNeutral ->  // Nice message
                "It's come to my attention that I may have attacked [${cityState.civName}].\nWhile it was not my goal to be at odds with your empire, this was deemed a necessary course of action."
            else ->  // Nasty message
                "I thought you might like to know that I've launched an invasion of one of your little pet states.\nThe lands of [${cityState.civName}] will make a fine addition to my own."
        }
        addGoodSizedLabel(text).row()
        
        if (!player.isAtWarWith(bullyOrAttacker)) {
            addCloseButton("THIS MEANS WAR!", KeyboardBinding.Confirm) {
            player.getDiplomacyManager(bullyOrAttacker)!!.sideWithCityState()
            val warReason = if (popupAlert.type == AlertType.AttackedAllyMinor) WarType.AlliedCityStateWar else WarType.ProtectedCityStateWar
            player.getDiplomacyManager(bullyOrAttacker)!!.declareWar(DeclareWarReason(warReason, cityState))
            cityState.getDiplomacyManager(player)!!.influence += 20f // You went to war for us!!
        }.row()}

        addCloseButton("You'll pay for this!", KeyboardBinding.Confirm) {
            player.getDiplomacyManager(bullyOrAttacker)!!.sideWithCityState()
        }.row()

        addCloseButton("Very well.", KeyboardBinding.Cancel) {
            player.addNotification("You have broken your Pledge to Protect [${cityState.civName}]!",
                cityState.cityStateFunctions.getNotificationActions(), NotificationCategory.Diplomacy, cityState.civName)
            cityState.cityStateFunctions.removeProtectorCiv(player, forced = true)
        }.row()
        
        return true
    }

    private fun addCityConquered() {
        val city = getCity(popupAlert.value)
        addQuestionAboutTheCity(city.name)
        val conqueringCiv = gameInfo.getCurrentPlayerCivilization()

        if (city.foundingCiv != ""
                && city.civ.civName != city.foundingCiv // can't liberate if the city actually belongs to those guys
                && conqueringCiv.civName != city.foundingCiv) { // or belongs originally to us
            addLiberateOption(city, conqueringCiv)
            addSeparator()
        }

        if (conqueringCiv.isOneCityChallenger()) {
            addDestroyOption {
                city.puppetCity(conqueringCiv)
                city.destroyCity()
            }
        } else {
            val mayAnnex = !conqueringCiv.hasUnique(UniqueType.MayNotAnnexCities)
            addAnnexOption(city, mayAnnex = mayAnnex) {
                city.puppetCity(conqueringCiv)
            }
            addSeparator()

            addPuppetOption(mayAnnex = mayAnnex) {
                city.puppetCity(conqueringCiv)
            }
            addSeparator()

            addRazeOption(city, mayAnnex = mayAnnex, conqueringCiv)
        }
    }

    private fun addCitySettledNearOtherCivDespiteOurPromise(): Boolean {
        val otherciv = getCiv(popupAlert.value)
        if (otherciv.isDefeated()) return false
        addLeaderName(otherciv)
        addGoodSizedLabel("We noticed your new city near our borders, despite your promise. This will have....implications.").row()
        addCloseButton("Very well.")
        return true
    }

    private fun addCityTraded() {
        val city = getCity(popupAlert.value)
        addQuestionAboutTheCity(city.name)
        val conqueringCiv = gameInfo.getCurrentPlayerCivilization()

        if (!conqueringCiv.isAtWarWith(getCiv(city.foundingCiv))) {
            addLiberateOption(city, conqueringCiv)
            addSeparator()
        }
        addCloseButton("Keep it").row()
    }

    private fun addDeclarationOfFriendship(): Boolean {
        val otherciv = getCiv(popupAlert.value)
        if (otherciv.isDefeated()) return false
        val playerDiploManager = viewingCiv.getDiplomacyManager(otherciv)!!
        addLeaderName(otherciv)
        addGoodSizedLabel(
                if (otherciv.nation.declaringFriendship.isNotEmpty()) otherciv.nation.declaringFriendship else "My friend, shall we declare our friendship to the world?"
        ).row()
        addCloseButton("Declare Friendship ([30] turns)", KeyboardBinding.Confirm) {
            playerDiploManager.signDeclarationOfFriendship()
        }.row()
        addCloseButton("We are not interested.", KeyboardBinding.Cancel) {
            playerDiploManager.otherCivDiplomacy().setFlag(DiplomacyFlags.DeclinedDeclarationOfFriendship, 20)
        }.row()
        val music = UncivGame.Current.musicController
        music.playVoice("${otherciv.nation.name}.declaringFriendship")
        return true
    }

    private fun addDefeated() {
        val civInfo = getCiv(popupAlert.value)
        addLeaderName(civInfo)
        addGoodSizedLabel(civInfo.nation.defeated).row()
        addCloseButton("Farewell.")
        music.chooseTrack(civInfo.civName, MusicMood.Defeat, EnumSet.of(MusicTrackChooserFlags.SuffixMustMatch))
        music.playVoice("${civInfo.civName}.defeated")
    }

    private fun addDemandToStopSettlingCitiesNear(): Boolean {
        val otherciv = getCiv(popupAlert.value)
        if (otherciv.isDefeated()) return false
        val playerDiploManager = viewingCiv.getDiplomacyManager(otherciv)!!
        addLeaderName(otherciv)
        addGoodSizedLabel("Please don't settle new cities near us.").row()
        addCloseButton("Very well, we shall look for new lands to settle.", KeyboardBinding.Confirm) {
            playerDiploManager.agreeToDemand(Demand.DoNotSettleNearUs)
        }.row()
        addCloseButton("We shall do as we please.", KeyboardBinding.Cancel) {
            playerDiploManager.refuseDemand(Demand.DoNotSettleNearUs)
        }
        return true
    }

    private fun addDemandToStopSpreadingReligion(): Boolean {
        val otherciv = getCiv(popupAlert.value)
        if (otherciv.isDefeated()) return false
        val playerDiploManager = viewingCiv.getDiplomacyManager(otherciv)!!
        addLeaderName(otherciv)
        addGoodSizedLabel("Please don't spread religion to us.").row()
        addCloseButton("Very well, we shall spread our faith elsewhere.", KeyboardBinding.Confirm) {
            playerDiploManager.agreeToDemand(Demand.DoNotSpreadReligion)
        }.row()
        addCloseButton("We shall do as we please.", KeyboardBinding.Cancel) {
            playerDiploManager.refuseDemand(Demand.DoNotSpreadReligion)
        }
        return true
    }

    private fun addReligionSpreadDespiteOurPromise(): Boolean {
        val otherciv = getCiv(popupAlert.value)
        if (otherciv.isDefeated()) return false
        addLeaderName(otherciv)
        addGoodSizedLabel("We noticed you have continued spreading your faith, despite your promise. This will have....consequences.").row()
        addCloseButton("Very well.")
        return true
    }
    private fun addDemandToStopSendingSpiesToUs(): Boolean {
        val otherciv = getCiv(popupAlert.value)
        if (otherciv.isDefeated()) return false
        val playerDiploManager = viewingCiv.getDiplomacyManager(otherciv)!!
        addLeaderName(otherciv)
        addGoodSizedLabel("Stop spying on us.").row()
        addCloseButton("We see our people are not welcome in your lands... we will take our attention elsewhere.", KeyboardBinding.Confirm) {
            playerDiploManager.agreeToDemand(Demand.DontSpyOnUs)
        }.row()
        addCloseButton("I'll do what's necessary for my empire to survive.", KeyboardBinding.Cancel) {
            playerDiploManager.refuseDemand(Demand.DontSpyOnUs)
        }
        return true
    }
    
    private fun addSpyingOnUsDespiteOurPromise(): Boolean {
        val otherciv = getCiv(popupAlert.value)
        if (otherciv.isDefeated()) return false
        addLeaderName(otherciv)
        addGoodSizedLabel("Take back your spy and your broken promises.").row()
        addCloseButton("Very well.")
        return true
    }
    


    private fun addDiplomaticMarriage() {
        val city = getCity(popupAlert.value)
        addGoodSizedLabel(city.name.tr() + ": " + "What would you like to do with the city?".tr(), Constants.headingFontSize) // Add name because there might be several cities
            .padBottom(20f).row()
        val marryingCiv = gameInfo.getCurrentPlayerCivilization()

        if (marryingCiv.isOneCityChallenger()) {
            addDestroyOption {
                city.destroyCity(overrideSafeties = true)
            }
        } else {
            val mayAnnex = !marryingCiv.hasUnique(UniqueType.MayNotAnnexCities)
            addAnnexOption(city, mayAnnex) {}
            addSeparator()

            addPuppetOption(mayAnnex) {
                city.isPuppet = true
                city.cityStats.update()
            }
        }
    }

    private fun addFirstContact() {
        val civInfo = getCiv(popupAlert.value)
        val nation = civInfo.nation
        addLeaderName(civInfo)
        music.chooseTrack(civInfo.civName, MusicMood.themeOrPeace, MusicTrackChooserFlags.setSpecific)
        music.playVoice("${civInfo.civName}.introduction")
        if (civInfo.isCityState) {
            addGoodSizedLabel("We have encountered the City-State of [${nation.name}]!").row()
            addCloseButton("Excellent!")
        } else {
            addGoodSizedLabel(nation.introduction).row()
            addCloseButton("A pleasure to meet you.")
        }
    }

    private fun addGameHasBeenWon() {
        val victoryData = gameInfo.victoryData!!
        addGoodSizedLabel("[${victoryData.winningCiv}] has won a [${victoryData.victoryType}] Victory!").row()
        addButton("Victory status") { close(); worldScreen.game.pushScreen(VictoryScreen(worldScreen)) }.row()
        addCloseButton()
    }

    private fun addGoldenAge() {
        addGoodSizedLabel("GOLDEN AGE")
        addSeparator()
        addGoodSizedLabel("Your citizens have been happy with your rule for so long that the empire enters a Golden Age!").row()
        addCloseButton()
        music.chooseTrack(viewingCiv.civName, MusicMood.Golden, MusicTrackChooserFlags.setSpecific)
    }

    /** @return false to skip opening this Popup, as we're running in the initialization phase before the Popup is open */
    private fun addRecapturedCivilian(): Boolean {
        val position = Vector2().fromString(popupAlert.value)
        val tile = gameInfo.tileMap[position]
        val capturedUnit = tile.civilianUnit  // This has got to be it
            ?: return false // the unit disappeared somehow? maybe a modded action?
        val originalOwner = getCiv(capturedUnit.originalOwner!!)
        if (originalOwner.isDefeated()) return false
        val captor = viewingCiv

        addGoodSizedLabel("Return [${capturedUnit.name}] to [${originalOwner.civName}]?")
        addSeparator()
        addGoodSizedLabel("The [${capturedUnit.name}] we liberated originally belonged to [${originalOwner.civName}]. They will be grateful if we return it to them.").row()

        bottomTable.defaults().pad(0f, 30f) // Small buttons, plenty of pad so we don't fat-finger it

        addCloseButton(Constants.yes, KeyboardBinding.Confirm) {
            // Return it to original owner
            val unitName = capturedUnit.baseUnit.name
            capturedUnit.destroy()
            val closestCity = originalOwner.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(tile) }

            if (closestCity != null) {
                // Attempt to place the unit near their nearest city
                originalOwner.units.placeUnitNearTile(closestCity.location, unitName)
            }

            if (originalOwner.isCityState) {
                originalOwner.getDiplomacyManagerOrMeet(captor).addInfluence(45f)
            } else if (originalOwner.isMajorCiv()) {
                // No extra bonus from doing it several times
                originalOwner.getDiplomacyManagerOrMeet(captor)
                    .setModifier(DiplomaticModifiers.ReturnedCapturedUnits, 20f)
            }
            val notificationSequence = sequence {
                yield(LocationAction(tile.position))
                if (closestCity != null)
                    yield(LocationAction(closestCity.location))
                yield(DiplomacyAction(captor.civName))
                yield(CivilopediaAction("Tutorial/Barbarians"))
            }
            originalOwner.addNotification("Your captured [${unitName}] has been returned by [${captor.civName}]", notificationSequence, NotificationCategory.Diplomacy, NotificationIcon.Trade, unitName, captor.civName)
        }
        addCloseButton(Constants.no, KeyboardBinding.Cancel) {
            // Take it for ourselves
            BattleUnitCapture.captureOrConvertToWorker(capturedUnit, captor)
        }
        return true
    }

    private fun addStartIntro() {
        val civInfo = viewingCiv
        addLeaderName(civInfo)
        addGoodSizedLabel(civInfo.nation.startIntroPart1).row()
        addGoodSizedLabel(civInfo.nation.startIntroPart2).row()
        addCloseButton("Let's begin!")

        // Since there's introduction text, play the startIntroPart1 voice hook with the nation's theme.
        val music = UncivGame.Current.musicController
        music.chooseTrack(civInfo.nation.name, MusicMood.themeOrPeace, MusicTrackChooserFlags.setSpecific)
        music.playVoice("${civInfo.nation.name}.startIntroPart1")
    }

    private fun addTechResearched() {
        val tech = gameInfo.ruleset.technologies[popupAlert.value]!!
        addGoodSizedLabel(tech.name)
        addSeparator()
        val centerTable = Table()
        centerTable.add(tech.quote.toLabel().apply { wrap = true }).width(stageWidth / 3)
        centerTable.add(ImageGetter.getTechIconPortrait(tech.name, 100f)).pad(20f)
        val descriptionScroll = ScrollPane(tech.getDescription(viewingCiv).toLabel().apply { wrap = true })
        centerTable.add(descriptionScroll).width(stageWidth / 3).maxHeight(stageHeight / 2)
        add(centerTable).row()
        addCloseButton()
        music.chooseTrack(tech.name, MusicMood.Researched, MusicTrackChooserFlags.setSpecific)
    }

    private fun addWarDeclaration(): Boolean {
        val civInfo = getCiv(popupAlert.value)
        // technically they already declared war, but if they're dead it'll be strange that they talk to us
        if (civInfo.isDefeated()) return false
        addLeaderName(civInfo)
        addGoodSizedLabel(civInfo.nation.declaringWar).row()
        bottomTable.defaults().pad(0f, 5f)
        addCloseButton("You'll pay for this!")
        addCloseButton("Very well.")
        music.chooseTrack(civInfo.civName, MusicMood.War, MusicTrackChooserFlags.setSpecific)
        music.playVoice("${civInfo.civName}.declaringWar")
        return true
    }

    private fun addWonderBuilt() {
        val wonder = gameInfo.ruleset.buildings[popupAlert.value]!!
        addGoodSizedLabel(wonder.name)
        addSeparator()
        if(ImageGetter.wonderImageExists(wonder.name)) {    // Wonder Graphic exists
            if(stageHeight * 3 > stageWidth * 4) {    // Portrait
                add(ImageGetter.getWonderImage(wonder.name))
                    .width(stageWidth / 1.5f)
                    .height(stageWidth / 3)
                    .row()
            }
            else {  // Landscape (or squareish)
                add(ImageGetter.getWonderImage(wonder.name))
                    .width(stageWidth / 2.5f)
                    .height(stageWidth / 5)
                    .row()
            }
        } else {    // Fallback
            add(ImageGetter.getConstructionPortrait(wonder.name, 100f)).pad(20f).row()
        }

        val centerTable = Table()
        centerTable.add(wonder.quote.toLabel().apply { wrap = true }).width(stageWidth / 3).pad(10f)
        centerTable.add(wonder.getShortDescription()
            .toLabel().apply { wrap = true }).width(stageWidth / 3).pad(10f)
        add(centerTable).row()
        addCloseButton()
        music.chooseTrack(wonder.name, MusicMood.Wonder, MusicTrackChooserFlags.setSpecific)
    }

    //endregion
    //region Helpers

    private fun addLeaderName(civInfo: Civilization) {
        add(LeaderIntroTable(civInfo))
        addSeparator()
    }

    private fun addQuestionAboutTheCity(cityName: String) {
        addGoodSizedLabel("What would you like to do with the city of [$cityName]?",
            Constants.headingFontSize, hideIcons = true).padBottom(20f).row()
    }

    private fun addDestroyOption(destroyAction: () -> Unit) {
        val button = "Destroy".toTextButton()
        button.onActivation {
            destroyAction()
            close()
        }
        button.keyShortcuts.add('d')
        add(button).row()
        addGoodSizedLabel("Destroying the city instantly razes the city to the ground.").row()
    }

    private fun addAnnexOption(city: City, mayAnnex: Boolean, annexAction: () -> Unit) {
        val button = "Annex".toTextButton()
        button.apply {
            if (!mayAnnex) disable() else {
                button.onActivation {
                    annexAction()
                    city.annexCity()
                    close()
                }
                button.keyShortcuts.add('a')
            }
        }
        add(button).row()
        if (mayAnnex) {
            addGoodSizedLabel("Annexed cities become part of your regular empire.").row()
            addGoodSizedLabel("Their citizens generate 2x the unhappiness, unless you build a courthouse.").row()
        } else {
            addGoodSizedLabel("Your civilization may not annex this city.").row()
        }

    }

    private fun addPuppetOption(mayAnnex: Boolean, puppetAction: () -> Unit) {
        val button = "Puppet".toTextButton()
        button.onActivation {
            puppetAction()
            close()
        }
        button.keyShortcuts.add('p')
        add(button).row()
        addGoodSizedLabel("Puppeted cities do not increase your tech or policy cost.").row()
        addGoodSizedLabel("You have no control over the the production of puppeted cities.").row()
        addGoodSizedLabel("Puppeted cities also generate 25% less Science and Culture.").row()
        if (mayAnnex) addGoodSizedLabel("A puppeted city can be annexed at any time.").row()
    }

    private fun addLiberateOption(city: City, conqueringCiv: Civilization) {
        val button = "Liberate (city returns to [originalOwner])".fillPlaceholders(city.foundingCiv).toTextButton()
        button.onActivation {
            city.liberateCity(conqueringCiv)
            close()
        }
        button.keyShortcuts.add('l')
        add(button).row()
        addGoodSizedLabel("Liberating a city returns it to its original owner, giving you a massive relationship boost with them!")
    }

    private fun addRazeOption(city: City, mayAnnex: Boolean, conqueringCiv: Civilization) {
        val canRaze = city.canBeDestroyed(justCaptured = true)
        val button = "Raze".toTextButton()
        button.apply {
            if (!canRaze) disable()
            else {
                onActivation {
                    city.puppetCity(conqueringCiv)
                    if (mayAnnex) { city.annexCity() }
                    city.isBeingRazed = true
                    close()
                }
                keyShortcuts.add('r')
            }
        }
        add(button).row()
        if (canRaze) {
            if (mayAnnex) {
                addGoodSizedLabel("Razing the city annexes it, and starts burning the city to the ground.").row()
            } else {
                addGoodSizedLabel("Razing the city puppets it, and starts burning the city to the ground.").row()
            }
            addGoodSizedLabel("The population will gradually dwindle until the city is destroyed.").row()
        } else {
            addGoodSizedLabel("Original capitals and holy cities cannot be razed.").row()
        }
    }

    /** Returns if event was triggered correctly */
    private fun addEvent(): Boolean {
        // The event string is in the format "eventName" + (Constants.stringSplitCharacter + "unitId=1234")?
        // We explicitly specify that this is a unitId, to enable us to add other context info in the future - for example city id
        val splitString = popupAlert.value.split(Constants.stringSplitCharacter)
        val eventName = splitString[0]
        var unit: MapUnit? = null
        for (i in 1 until splitString.size) {
            if (splitString[i].startsWith("unitId=")){
                val unitId = splitString[i].substringAfter("unitId=").toInt()
                unit = viewingCiv.units.getUnitById(unitId)
            }
        }
        
        
        val event = gameInfo.ruleset.events[eventName] ?: return false
        val render = RenderEvent(event, worldScreen, unit) { close() }
        if (!render.isValid) return false
        add(render).pad(0f).row()
        return true
    }

    //endregion

    override fun close() {
        viewingCiv.popupAlerts.remove(popupAlert)
        worldScreen.shouldUpdate = true
        super.close()
    }
}
