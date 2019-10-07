package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.PopupTable

class AlertPopup(val worldScreen: WorldScreen, val popupAlert: PopupAlert): PopupTable(worldScreen){
    fun getCloseButton(text: String, action: (() -> Unit)?=null): TextButton {
        val button = TextButton(text.tr(), skin)
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
                addGoodSizedLabel(civInfo.getTranslatedNation().declaringWar).row()
                val responseTable = Table()
                responseTable.add(getCloseButton("You'll pay for this!"))
                responseTable.add(getCloseButton("Very well."))
                add(responseTable)
            }
            AlertType.Defeated -> {
                val civInfo = worldScreen.gameInfo.getCivilization(popupAlert.value)
                addLeaderName(civInfo)
                addGoodSizedLabel(civInfo.getTranslatedNation().defeated).row()
                add(getCloseButton("Farewell."))
            }
            AlertType.FirstContact -> {
                val civInfo = worldScreen.gameInfo.getCivilization(popupAlert.value)
                val translatedNation = civInfo.getTranslatedNation()
                if (civInfo.isCityState()) {
                    addLeaderName(civInfo)
                    addGoodSizedLabel("We have encountered the City-State of [${translatedNation.getNameTranslation()}]!").row()
                    add(getCloseButton("Excellent!"))
                } else {
                    addLeaderName(civInfo)
                    addGoodSizedLabel(translatedNation.introduction).row()
                    add(getCloseButton("A pleasure to meet you."))
                }
            }
            AlertType.CityConquered -> {
                val city = worldScreen.gameInfo.civilizations.flatMap { it.cities }.first { it.name == popupAlert.value}
                addGoodSizedLabel("What would you like to do with the city?").row()
                val conqueringCiv = worldScreen.gameInfo.currentPlayerCiv
                if (city.foundingCiv != ""
                        && city.civInfo.civName != city.foundingCiv // can't liberate if the city actually belongs to those guys
                        && conqueringCiv.civName != city.foundingCiv) { // or belongs originally to us
                    add(TextButton("Liberate".tr(), skin).onClick {
                        city.liberateCity(conqueringCiv)
                        worldScreen.shouldUpdate=true
                        close()
                    }).row()
                }
                add(TextButton("Annex".tr(), skin).onClick {
                    city.puppetCity(conqueringCiv)
                    city.annexCity()
                    worldScreen.shouldUpdate=true
                    close()
                }).row()
                add(TextButton("Puppet City".tr(), skin).onClick {
                    city.puppetCity(conqueringCiv)
                    worldScreen.shouldUpdate=true
                    close()
                }).row()
                add(TextButton("Raze".tr(), skin).onClick {
                    city.puppetCity(conqueringCiv)
                    city.annexCity()
                    city.isBeingRazed = true
                    worldScreen.shouldUpdate=true
                    close()
                })
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
                val wonder = GameBasics.Buildings[popupAlert.value]!!
                addGoodSizedLabel(wonder.name)
                addSeparator()
                val centerTable = Table()
                centerTable.add(wonder.quote.toLabel().apply { setWrap(true) }).width(worldScreen.stage.width/3)
                centerTable.add(ImageGetter.getConstructionImage(wonder.name).surroundWithCircle(100f)).pad(20f)
                centerTable.add(wonder.getShortDescription().toLabel().apply { setWrap(true) }).width(worldScreen.stage.width/3)
                add(centerTable).row()
                add(getCloseButton("Close"))
            }
            AlertType.TechResearched -> {
                val tech = GameBasics.Technologies[popupAlert.value]!!
                addGoodSizedLabel(tech.name)
                addSeparator()
                val centerTable = Table()
                centerTable.add(tech.quote.toLabel().apply { setWrap(true) }).width(worldScreen.stage.width/3)
                centerTable.add(ImageGetter.getTechIconGroup(tech.name,100f)).pad(20f)
                centerTable.add(tech.description.toLabel().apply { setWrap(true) }).width(worldScreen.stage.width/3)
                add(centerTable).row()
                add(getCloseButton("Close"))
            }
            AlertType.GoldenAge -> {
                addGoodSizedLabel("GOLDEN AGE")
                addSeparator()
                addGoodSizedLabel("Your citizens have been happy with your rule for so long that the empire enters a Golden Age!").row()
                add(getCloseButton("Close"))
            }
        }
        open()
        worldScreen.alertPopupIsOpen = true
    }

    override fun close(){
        worldScreen.viewingCiv.popupAlerts.remove(popupAlert)
        worldScreen.alertPopupIsOpen = false
        super.close()
    }
}