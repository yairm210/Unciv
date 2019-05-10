package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.models.gamebasics.Nation
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.addSeparator
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel
import com.unciv.ui.worldscreen.optionstable.PopupTable

class AlertPopup(val worldScreen: WorldScreen, val popupAlert: PopupAlert): PopupTable(worldScreen){
    fun getCloseButton(text:String): TextButton {
        val button = TextButton(text.tr(), skin)
        button.onClick { close() }
        return button
    }

    fun addLeaderName(translatedNation: Nation){
        val otherCivLeaderName = translatedNation.getLeaderDisplayName()
        add(otherCivLeaderName.toLabel())
        addSeparator()
    }

    init {

        when(popupAlert.type){
            AlertType.WarDeclaration -> {
                val translatedNation = worldScreen.gameInfo.getCivilization(popupAlert.value).getTranslatedNation()
                addLeaderName(translatedNation)
                addGoodSizedLabel(translatedNation.declaringWar).row()
                val responseTable = Table()
                responseTable.add(getCloseButton("You'll pay for this!"))
                responseTable.add(getCloseButton("Very well."))
                add(responseTable)
            }
            AlertType.Defeated -> {
                val translatedNation = worldScreen.gameInfo.getCivilization(popupAlert.value).getTranslatedNation()
                addLeaderName(translatedNation)
                addGoodSizedLabel(translatedNation.defeated).row()
                add(getCloseButton("Farewell."))
            }
            AlertType.FirstContact -> {
                val civ = worldScreen.gameInfo.getCivilization(popupAlert.value)
                val translatedNation = civ.getTranslatedNation()
                if (civ.isCityState()) {
                    addLeaderName(translatedNation)
                    addGoodSizedLabel("Type : " + civ.getCityStateType()).row()
                    add(getCloseButton("A pleasure to meet you."))
                } else {
                    addLeaderName(translatedNation)
                    addGoodSizedLabel(translatedNation.introduction).row()
                    add(getCloseButton("A pleasure to meet you."))
                }
            }
            AlertType.CityConquered -> {
                addGoodSizedLabel("What would you like to do with the city?").row()
                add(getCloseButton("Annex")).row()
                add(TextButton("Raze", skin).onClick {
                    worldScreen.currentPlayerCiv.cities.first { it.name==popupAlert.value }.isBeingRazed=true
                    worldScreen.shouldUpdate=true
                    close()
                })
            }
            AlertType.BorderConflict -> {
                val translatedNation = worldScreen.gameInfo.getCivilization(popupAlert.value).getTranslatedNation()
                addLeaderName(translatedNation)
                addGoodSizedLabel("Remove your troops in our border immediately!").row()
                val responseTable = Table()
                responseTable.add(getCloseButton("Sorry."))
                responseTable.add(getCloseButton("Never!"))
                add(responseTable)
            }
        }
        open()
        isOpen = true
    }

    fun close(){
        worldScreen.currentPlayerCiv.popupAlerts.remove(popupAlert)
        isOpen = false
        remove()
    }

    companion object {
        var isOpen = false
    }
}