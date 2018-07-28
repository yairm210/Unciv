//package com.unciv.ui
//
//import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
//import com.badlogic.gdx.scenes.scene2d.ui.Table
//import com.badlogic.gdx.scenes.scene2d.ui.TextButton
//import com.unciv.UnCivGame
//import com.unciv.logic.civilization.CivilizationInfo
//import com.unciv.logic.civilization.DiplomaticStatus
//import com.unciv.ui.utils.CameraStageBaseScreen
//import com.unciv.ui.utils.addClickListener
//import com.unciv.ui.utils.tr
//
//
//class DiplomacyScreen : CameraStageBaseScreen(){
//
//    val civInfo = UnCivGame.Current.gameInfo.getPlayerCivilization()
//    var otherCivilization: CivilizationInfo? = null
//    val diplomacyTable = Table()
//
//    init {
//        val closeButton = TextButton("Close".tr(), skin)
//        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
//        closeButton.y = stage.height - closeButton.height - 5
//        stage.addActor(closeButton)
//
//
//        val civPickerTable = Table().apply { defaults().pad(5f) }
//        for(otherCivName in civInfo.diplomacy.keys){
//            val chooseCiv = TextButton(otherCivName,skin)
//            chooseCiv.addClickListener {
//                otherCivilization = civInfo.gameInfo.civilizations.first{it.civName==otherCivName}
//                updateDiplomacyTable()
//            }
//            civPickerTable.add().row()
//        }
//
//        val splitPane = SplitPane(diplomacyTable,civPickerTable,true, skin)
//        splitPane.setSplitAmount(0.8f)
//        stage.addActor(splitPane)
//    }
//
//    private fun updateDiplomacyTable() {
//        diplomacyTable.clear()
//        if(otherCivilization==null) return
//        val otherCivDiplomacyManager = civInfo.diplomacy[otherCivilization!!.civName]!!
//
//        if(otherCivDiplomacyManager.status==DiplomaticStatus.Peace) {
//            val tradeButton = TextButton("Trade".tr(), skin)
//            tradeButton .addClickListener {
//                UnCivGame.Current.screen = TradeScreen(otherCivilization!!)
//            }
//
//            val declareWarButton = TextButton("Declare War".tr(), skin)
//            declareWarButton.addClickListener {
//                civInfo.diplomacy[otherCivilization!!.civName]!!.declareWar()
//                updateDiplomacyTable()
//            }
//        }
//
//        else{
//            val declareWarButton = TextButton("Negotiate Peace".tr(), skin)
//            declareWarButton.addClickListener {
//                UnCivGame.Current.screen = TradeScreen(otherCivilization!!)
//            }
//        }
//        diplomacyTable.add()
//    }
//
//}
