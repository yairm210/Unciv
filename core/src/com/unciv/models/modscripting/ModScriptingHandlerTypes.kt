@file:Suppress("RemoveExplicitTypeArguments")

package com.unciv.models.modscripting

import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.scripting.utils.StatelessMap
import com.unciv.ui.AddMultiplayerGameScreen
import com.unciv.ui.EditMultiplayerGameInfoScreen
import com.unciv.ui.LanguagePickerScreen
import com.unciv.ui.MultiplayerScreen
import com.unciv.ui.cityscreen.*
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.consolescreen.ConsoleScreen
import com.unciv.ui.mapeditor.*
import com.unciv.ui.newgamescreen.*
import com.unciv.ui.overviewscreen.*
import com.unciv.ui.pickerscreens.*
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.saves.SaveGameScreen
import com.unciv.ui.tilegroups.CityButton
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.trade.LeaderIntroTable
import com.unciv.ui.trade.OfferColumnsTable
import com.unciv.ui.trade.TradeTable
import com.unciv.ui.utils.*
import com.unciv.ui.victoryscreen.VictoryScreen
import com.unciv.ui.worldscreen.*
import com.unciv.ui.worldscreen.bottombar.BattleTable
import com.unciv.ui.worldscreen.bottombar.TileInfoTable
import com.unciv.ui.worldscreen.mainmenu.OptionsPopup
import com.unciv.ui.worldscreen.mainmenu.WorldScreenCommunityPopup
import com.unciv.ui.worldscreen.mainmenu.WorldScreenMenuPopup
import com.unciv.ui.worldscreen.unit.IdleUnitButton
import com.unciv.ui.worldscreen.unit.UnitActionsTable
import com.unciv.ui.worldscreen.unit.UnitTable
import kotlin.reflect.KType
//import kotlin.reflect.full.isSubtypeOf
//import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

// Dependency/feature stack:
//  ModScriptingHandlerTypes —> ModScriptingRunManager —> ModScriptingRegistrationHandler
//  com.unciv.scripting —> com.unciv.models.modscripting
// Later namespaces in each stack may use members and features from earlier namespaces, but earlier namespaces should have behaviour that is completely independent of any later items.
// The scripting execution model (com.unciv.scripting) only *runs* scripts— Anything to do with loading them should go in here (com.unciv.models.modscripting) instead.
// Likewise, ModScriptingHandlerTypes is only for defining handlerTypes, ModScriptingRunManager is only for using that during gameplay, and anything to do with parsing mod structures should go into the level of ModScriptingRegistrationHandler.

// Code generators setup: $ function GetScreens() ( shopt -s globstar && (grep -ohP '(?<!private class )(?<=class )[a-zA-Z]*Screen(?=([^a-zA-Z]))' core/**/*.kt | sort -u) ); function GetPopups() ( shopt -s globstar && (grep -ohP '(?<!private class )(?<=class )[a-zA-Z]*Popup(?=([^a-zA-Z]))' core/**/*.kt | sort -u) ); function MakeSingles() ( echo -e "${tab}// addSingleParamHandlers<$1>(\n$(for default in ${@:2}; do echo "$tab//$tab HandlerId.$default,"; done)\n$tab// )"; ); function HandlerBoilerplates() (export tab="    "; export defaults=(after_open before_close after_rebuild); while read name; do echo -e "handlerContext(ContextId.$name) {\n${tab}addInstantiationHandlers<$name>()\n$(MakeSingles $name ${defaults[@]})\n}"; done)

// TODO: Autogenerate handler type documentation from this, similarly to UniqueType documentation.

val HANDLER_DEFINITIONS = handlerDefinitions { // TODO: Definitely put this in its own package to preserve the namespace.
    handlerContext(ContextId.UncivGame) {
        addInstantiationHandlers<UncivGame>()
    }
    handlerContext(ContextId.GameInfo) {
        handler<Pair<GameInfo, CivilizationInfo?>>(HandlerId.after_instantiate){
            param<GameInfo>("gameInfo") { it.first }
            param<CivilizationInfo?>("civInfo") { it.second }
        }
        handler<Pair<GameInfo, CivilizationInfo?>>(HandlerId.before_discard) {
            param<GameInfo>("gameInfo") { it.first }
            param<CivilizationInfo?>("civInfo") { it.second }
        }
        addSingleParamHandlers<GameInfo>(
            HandlerId.after_open,
            HandlerId.before_close
        )
    }
//    handlerContext(ContextId.ConsoleScreen) {
//        addInstantiationHandlers<ConsoleScreen>()
//        addSingleParamHandlers<ConsoleScreen>(
//            HandlerId.after_open,
//            HandlerId.before_close
//        )
//    }


    // Boilerplate generator: $ GetScreens | HandlerBoilerplates
    handlerContext(ContextId.AddMultiplayerGameScreen) {
        addInstantiationHandlers<AddMultiplayerGameScreen>()
        // addSingleParamHandlers<AddMultiplayerGameScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.BaseScreen) {
        addInstantiationHandlers<BaseScreen>()
        // addSingleParamHandlers<BaseScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.CityScreen) {
        addInstantiationHandlers<CityScreen>()
        // addSingleParamHandlers<CityScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.CivilopediaScreen) {
        addInstantiationHandlers<CivilopediaScreen>()
        // addSingleParamHandlers<CivilopediaScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ConsoleScreen) {
        addInstantiationHandlers<ConsoleScreen>()
        // addSingleParamHandlers<ConsoleScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.DiplomacyScreen) {
        addInstantiationHandlers<DiplomacyScreen>()
        // addSingleParamHandlers<DiplomacyScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.DiplomaticVotePickerScreen) {
        addInstantiationHandlers<DiplomaticVotePickerScreen>()
        // addSingleParamHandlers<DiplomaticVotePickerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.DiplomaticVoteResultScreen) {
        addInstantiationHandlers<DiplomaticVoteResultScreen>()
        // addSingleParamHandlers<DiplomaticVoteResultScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.EditMultiplayerGameInfoScreen) {
        addInstantiationHandlers<EditMultiplayerGameInfoScreen>()
        // addSingleParamHandlers<EditMultiplayerGameInfoScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.EmpireOverviewScreen) {
        addInstantiationHandlers<EmpireOverviewScreen>()
        // addSingleParamHandlers<EmpireOverviewScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.GameParametersScreen) {
        addInstantiationHandlers<GameParametersScreen>()
        // addSingleParamHandlers<GameParametersScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.GreatPersonPickerScreen) {
        addInstantiationHandlers<GreatPersonPickerScreen>()
        // addSingleParamHandlers<GreatPersonPickerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ImprovementPickerScreen) {
        addInstantiationHandlers<ImprovementPickerScreen>()
        // addSingleParamHandlers<ImprovementPickerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.LanguagePickerScreen) {
        addInstantiationHandlers<LanguagePickerScreen>()
        // addSingleParamHandlers<LanguagePickerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.LoadGameScreen) {
        addInstantiationHandlers<LoadGameScreen>()
        // addSingleParamHandlers<LoadGameScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.MainMenuScreen) {
        addInstantiationHandlers<MainMenuScreen>()
        // addSingleParamHandlers<MainMenuScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.MapEditorScreen) {
        addInstantiationHandlers<MapEditorScreen>()
        // addSingleParamHandlers<MapEditorScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ModManagementScreen) {
        addInstantiationHandlers<ModManagementScreen>()
        // addSingleParamHandlers<ModManagementScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.MultiplayerScreen) {
        addInstantiationHandlers<MultiplayerScreen>()
        // addSingleParamHandlers<MultiplayerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.NewGameScreen) {
        addInstantiationHandlers<NewGameScreen>()
        // addSingleParamHandlers<NewGameScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.NewMapScreen) {
        addInstantiationHandlers<NewMapScreen>()
        // addSingleParamHandlers<NewMapScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.PantheonPickerScreen) {
        addInstantiationHandlers<PantheonPickerScreen>()
        // addSingleParamHandlers<PantheonPickerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.PickerScreen) {
        addInstantiationHandlers<PickerScreen>()
        // addSingleParamHandlers<PickerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.PlayerReadyScreen) {
        addInstantiationHandlers<PlayerReadyScreen>()
        // addSingleParamHandlers<PlayerReadyScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.PolicyPickerScreen) {
        addInstantiationHandlers<PolicyPickerScreen>()
        // addSingleParamHandlers<PolicyPickerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.PromotionPickerScreen) {
        addInstantiationHandlers<PromotionPickerScreen>()
        // addSingleParamHandlers<PromotionPickerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ReligiousBeliefsPickerScreen) {
        addInstantiationHandlers<ReligiousBeliefsPickerScreen>()
        // addSingleParamHandlers<ReligiousBeliefsPickerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.SaveAndLoadMapScreen) {
        addInstantiationHandlers<SaveAndLoadMapScreen>()
        // addSingleParamHandlers<SaveAndLoadMapScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.SaveGameScreen) {
        addInstantiationHandlers<SaveGameScreen>()
        // addSingleParamHandlers<SaveGameScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.TechPickerScreen) {
        addInstantiationHandlers<TechPickerScreen>()
        // addSingleParamHandlers<TechPickerScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.VictoryScreen) {
        addInstantiationHandlers<VictoryScreen>()
        // addSingleParamHandlers<VictoryScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.WorldScreen) {
        addInstantiationHandlers<WorldScreen>()
        // addSingleParamHandlers<WorldScreen>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }


    // Boilerplate generator: $ GetPopups | HandlerBoilerplates
    handlerContext(ContextId.AlertPopup) {
        addInstantiationHandlers<AlertPopup>()
        // addSingleParamHandlers<AlertPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.AskNumberPopup) {
        addInstantiationHandlers<AskNumberPopup>()
        // addSingleParamHandlers<AskNumberPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.AskTextPopup) {
        addInstantiationHandlers<AskTextPopup>()
        // addSingleParamHandlers<AskTextPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ExitGamePopup) {
        addInstantiationHandlers<ExitGamePopup>()
        // addSingleParamHandlers<ExitGamePopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.MapEditorMainScreenPopup) {
        addInstantiationHandlers<MainMenuScreen.MapEditorMainScreenPopup>()
        // addSingleParamHandlers<MapEditorMainScreenPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.MapEditorMenuPopup) {
        addInstantiationHandlers<MapEditorMenuPopup>()
        // addSingleParamHandlers<MapEditorMenuPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.MapEditorRulesetPopup) {
        addInstantiationHandlers<MapEditorMenuPopup.MapEditorRulesetPopup>()
        // addSingleParamHandlers<MapEditorRulesetPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.OptionsPopup) {
        addInstantiationHandlers<OptionsPopup>()
        // addSingleParamHandlers<OptionsPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.Popup) {
        addInstantiationHandlers<Popup>()
        // addSingleParamHandlers<Popup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ToastPopup) {
        addInstantiationHandlers<ToastPopup>()
        // addSingleParamHandlers<ToastPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.TradePopup) {
        addInstantiationHandlers<TradePopup>()
        // addSingleParamHandlers<TradePopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.TradeThanksPopup) {
        addInstantiationHandlers<TradePopup.TradeThanksPopup>()
        // addSingleParamHandlers<TradeThanksPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.WorldScreenCommunityPopup) {
        addInstantiationHandlers<WorldScreenCommunityPopup>()
        // addSingleParamHandlers<WorldScreenCommunityPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.WorldScreenMenuPopup) {
        addInstantiationHandlers<WorldScreenMenuPopup>()
        // addSingleParamHandlers<WorldScreenMenuPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.YesNoPopup) {
        addInstantiationHandlers<YesNoPopup>()
        // addSingleParamHandlers<YesNoPopup>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }



    // Boilerplate generator: $ (export names=(); (for name in ${names[@]}; do echo "$name"; done) | HandlerBoilerplates)
    // Fill the parentheses inside names=() with a space-delimited list copied from HandlerId to use a Bash array.

    handlerContext(ContextId.BattleTable) {
        addInstantiationHandlers<BattleTable>()
        // addSingleParamHandlers<BattleTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.CityButton) {
        addInstantiationHandlers<CityButton>()
        // addSingleParamHandlers<CityButton>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.CityInfoTable) {
        addInstantiationHandlers<CityInfoTable>()
        // addSingleParamHandlers<CityInfoTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.CityOverviewTable) {
        addInstantiationHandlers<CityOverviewTable>()
        // addSingleParamHandlers<CityOverviewTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.CityReligionInfoTable) {
        addInstantiationHandlers<CityReligionInfoTable>()
        // addSingleParamHandlers<CityReligionInfoTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.CityScreenCityPickerTable) {
        addInstantiationHandlers<CityScreenCityPickerTable>()
        // addSingleParamHandlers<CityScreenCityPickerTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.CityScreenTileTable) {
        addInstantiationHandlers<CityScreenTileTable>()
        // addSingleParamHandlers<CityScreenTileTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.CityStatsTable) {
        addInstantiationHandlers<CityStatsTable>()
        // addSingleParamHandlers<CityStatsTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ConstructionInfoTable) {
        addInstantiationHandlers<ConstructionInfoTable>()
        // addSingleParamHandlers<ConstructionInfoTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.DiplomacyOverviewTable) {
        addInstantiationHandlers<DiplomacyOverviewTable>()
        // addSingleParamHandlers<DiplomacyOverviewTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ExpanderTab) {
        addInstantiationHandlers<ExpanderTab>()
        // addSingleParamHandlers<ExpanderTab>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.GameOptionsTable) {
        addInstantiationHandlers<GameOptionsTable>()
        // addSingleParamHandlers<GameOptionsTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.IdleUnitButton) {
        addInstantiationHandlers<IdleUnitButton>()
        // addSingleParamHandlers<IdleUnitButton>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.LanguageTable) {
        addInstantiationHandlers<LanguageTable>()
        // addSingleParamHandlers<LanguageTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.LeaderIntroTable) {
        addInstantiationHandlers<LeaderIntroTable>()
        // addSingleParamHandlers<LeaderIntroTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.MapEditorOptionsTable) {
        addInstantiationHandlers<MapEditorOptionsTable>()
        // addSingleParamHandlers<MapEditorOptionsTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.MapOptionsTable) {
        addInstantiationHandlers<MapOptionsTable>()
        // addSingleParamHandlers<MapOptionsTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.MapParametersTable) {
        addInstantiationHandlers<MapParametersTable>()
        // addSingleParamHandlers<MapParametersTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.Minimap) {
        addInstantiationHandlers<Minimap>()
        // addSingleParamHandlers<Minimap>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.MinimapHolder) {
        addInstantiationHandlers<MinimapHolder>()
        // addSingleParamHandlers<MinimapHolder>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ModCheckboxTable) {
        addInstantiationHandlers<ModCheckboxTable>()
        // addSingleParamHandlers<ModCheckboxTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.NationTable) {
        addInstantiationHandlers<NationTable>()
        // addSingleParamHandlers<NationTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.OfferColumnsTable) {
        addInstantiationHandlers<OfferColumnsTable>()
        // addSingleParamHandlers<OfferColumnsTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.PlayerPickerTable) {
        addInstantiationHandlers<PlayerPickerTable>()
        // addSingleParamHandlers<PlayerPickerTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ReligionOverviewTable) {
        addInstantiationHandlers<ReligionOverviewTable>()
        // addSingleParamHandlers<ReligionOverviewTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.ResourcesOverviewTable) {
        addInstantiationHandlers<ResourcesOverviewTable>()
        // addSingleParamHandlers<ResourcesOverviewTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.SpecialistAllocationTable) {
        addInstantiationHandlers<SpecialistAllocationTable>()
        // addSingleParamHandlers<SpecialistAllocationTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.StatsOverviewTable) {
        addInstantiationHandlers<StatsOverviewTable>()
        // addSingleParamHandlers<StatsOverviewTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.TabbedPager) {
        addInstantiationHandlers<TabbedPager>()
        // addSingleParamHandlers<TabbedPager>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.TechButton) {
        addInstantiationHandlers<TechButton>()
        // addSingleParamHandlers<TechButton>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.TileInfoTable) {
        addInstantiationHandlers<TileInfoTable>()
        // addSingleParamHandlers<TileInfoTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.TradesOverviewTable) {
        addInstantiationHandlers<TradesOverviewTable>()
        // addSingleParamHandlers<TradesOverviewTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.TradeTable) {
        addInstantiationHandlers<TradeTable>()
        // addSingleParamHandlers<TradeTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.UncivSlider) {
        addInstantiationHandlers<UncivSlider>()
        // addSingleParamHandlers<UncivSlider>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.UnitActionsTable) {
        addInstantiationHandlers<UnitActionsTable>()
        // addSingleParamHandlers<UnitActionsTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.UnitOverviewTable) {
        addInstantiationHandlers<UnitOverviewTable>()
        // addSingleParamHandlers<UnitOverviewTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.UnitTable) {
        addInstantiationHandlers<UnitTable>()
        // addSingleParamHandlers<UnitTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.WonderOverviewTable) {
        addInstantiationHandlers<WonderOverviewTable>()
        // addSingleParamHandlers<WonderOverviewTable>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }
    handlerContext(ContextId.WorldScreenTopBar) {
        addInstantiationHandlers<WorldScreenTopBar>()
        // addSingleParamHandlers<WorldScreenTopBar>(
        //     HandlerId.after_open,
        //     HandlerId.before_close,
        //     HandlerId.after_rebuild,
        // )
    }

}


private inline fun <reified V> HandlerDefinitionsBuilderScope.HandlerContextBuilderScope
        .addSingleParamHandlers(vararg handlerTypes: HandlerId, paramName: String? = null)
{
    for (handlerType in handlerTypes) {
        handler<V>(handlerType) {
            param<V>(paramName ?: V::class.simpleName!!.replaceFirstChar { it.lowercase()[0] })
        }
    }
}

private inline fun <reified V> HandlerDefinitionsBuilderScope.HandlerContextBuilderScope
        .addInstantiationHandlers(paramName: String? = null)
{
    addSingleParamHandlers<V>(HandlerId.after_instantiate, HandlerId.before_discard, paramName = paramName)
}

val ALL_HANDLER_TYPES = HANDLER_DEFINITIONS.handlerContexts.values.asSequence()
    .map { it.handlerTypes.values }
    .flatten().toSet()


// TODO: Unit test to make sure all contexts IDs are used, and have at least one handler type.

// Enum of identifiers for a scripting handler context namespace.

// Names map directly to and deserialize directly from JSON keys in mod files.
enum class ContextId { // These are mostly so autocompletion, typodetection, usage search, etc will work.
    UncivGame,
    GameInfo,
    TileGroup,

    // Code generator: $ GetScreens | while read name; do echo "$name,"; done
    AddMultiplayerGameScreen,
    BaseScreen,
    CityScreen,
    CivilopediaScreen,
    ConsoleScreen,
    DiplomacyScreen,
    DiplomaticVotePickerScreen,
    DiplomaticVoteResultScreen,
    EditMultiplayerGameInfoScreen,
    EmpireOverviewScreen,
    GameParametersScreen,
    GreatPersonPickerScreen,
    ImprovementPickerScreen,
    LanguagePickerScreen,
    LoadGameScreen,
    MainMenuScreen,
    MapEditorScreen,
    ModManagementScreen,
    MultiplayerScreen,
    NewGameScreen,
    NewMapScreen,
    PantheonPickerScreen,
    PickerScreen,
    PlayerReadyScreen,
    PolicyPickerScreen,
    PromotionPickerScreen,
    ReligiousBeliefsPickerScreen,
    SaveAndLoadMapScreen,
    SaveGameScreen,
    TechPickerScreen,
    VictoryScreen,
    WorldScreen,


    // Code generator: $ GetPopups | while read name; do echo "$name,"; done
    AlertPopup,
    AskNumberPopup,
    AskTextPopup,
    ExitGamePopup,
    MapEditorMainScreenPopup,
    MapEditorMenuPopup,
    MapEditorRulesetPopup,
    NationPickerPopup,
    OptionsPopup,
    PassPopup,
    Popup,
    ToastPopup,
    TradePopup,
    TradeThanksPopup,
    WorldScreenCommunityPopup,
    WorldScreenMenuPopup,
    YesNoPopup,

    // From "Type Hierarchy" browser for Table in Android Studio:
    BattleTable,
    CityButton,
    CityInfoTable,
    CityOverviewTable,
    CityReligionInfoTable,
    CityScreenCityPickerTable,
    CityScreenTileTable,
    CityStatsTable,
    ConstructionInfoTable,
    DiplomacyOverviewTable,
    ExpanderTab,
    GameOptionsTable,
    IconTable,
    IdleUnitButton,
    InfluenceTable,
    LanguageTable,
    LeaderIntroTable,
    MapEditorOptionsTable,
    MapOptionsTable,
    MapParametersTable,
    Minimap,
    MinimapHolder,
    ModCheckboxTable,
    NationTable,
    OfferColumnsTable,
    PlayerPickerTable,
    ReligionOverviewTable,
    ResourcesOverviewTable,
    SpecialistAllocationTable,
    StatsOverviewTable,
    TabbedPager,
    TechButton,
    TileInfoTable,
    TradesOverviewTable,
    TradeTable,
    UncivSlider,
    UnitActionsTable,
    UnitOverviewTable,
    UnitTable,
    WonderOverviewTable,
    WorldScreenTopBar,
}

// Enum of identifiers for names of script handlers.

// Each identifier can be used in more than one context namespace, but does not have to be implemented in all contexts, and may not be defined in a single context more than once.

// Names map directly to and deserialize directly from JSON keys in mod files.
@Suppress("EnumEntryName", "SpellCheckingInspection") // Handler names are not Kotlin code, but more like JSON keys in mod configuration files.
enum class HandlerId {
    // After creation and initialization of a new object with the same name as the context.
    after_instantiate,
    // Before destruction of each object with the same name as the context. Not guaranteed to be run, and not guaranteed to be run consistently even if run.
    before_discard,
    // After data represented by object is exposed as context to player, including if such happens multiple times.
    after_open,
    // After data represented by object is replaced as working context GUI to player, including if such happens multiple times.
    before_close,
    after_rebuild,
    before_gamesave,
    after_turnstart,
    after_unitmove,
    after_cityconstruction,
    after_cityfound,
    after_techfinished,
    after_policyadopted,
    before_turnend,
    after_click,
    after_modload,
    before_modunload,

    after_update, // E.G. WorldMapHolder, drawing cirles and arrows.
}


typealias Params = Map<String, Any?>?
typealias ParamGetter = (Any?) -> Params
// Some handlerTypes may have parameters that should be set universally; Others may use or simply pass on parameters from where they're called.

typealias HandlerContex = Map<HandlerId, HandlerType>
typealias HandlerDefs = Map<ContextId, HandlerContex>

interface test {
    var a: HandlerDefs
}

fun test(a: test){
    a.a
}

interface HandlerDefinitions: StatelessMap<ContextId, HandlerContext> {
    val handlerContexts: Map<ContextId, HandlerContext>
    override fun get(key: ContextId): HandlerContext {
        val handlerContext = handlerContexts[key]
        if (handlerContext == null) {
            val exc = NullPointerException("Unknown HandlerContext $key!")
            println(exc.stringifyException())
            throw exc
        }
        return handlerContext
    }
    operator fun get(contextKey: ContextId, handlerKey: HandlerId) = get(contextKey).get(handlerKey)
}

interface HandlerContext: StatelessMap<HandlerId, HandlerType> {
    val name: ContextId
    val handlerTypes: Map<HandlerId, HandlerType>
    override fun get(key: HandlerId): HandlerType {
        val handlerType = handlerTypes[key]
        if (handlerType == null) {
            val exc = NullPointerException("Unknown HandlerType $key for $name context!")
            println(exc.stringifyException())
            throw exc
        }
        return handlerType
    }
}

interface HandlerType {
    val name: HandlerId
    val paramTypes: Map<String, KType>?
    val paramGetter: ParamGetter
//    fun checkParamsValid(checkParams: Params): Boolean { // If this becomes a major performance sink, it could be disabled in release builds. But
//        if (paramTypes == null || checkParams == null) {
//            return checkParams == paramTypes
//        }
//        if (paramTypes!!.keys != checkParams.keys) {
//            return false
//        }
//        return checkParams.all { (k, v) ->
//            if (v == null) {
//                paramTypes!![k]!!.isMarkedNullable
//            } else {
//                //v::class.starProjectedType.isSubtypeOf(paramTypes!![k]!!)
//                // In FunctionDispatcher I compare the .jvmErasure KClass instead of the erased type.
//                // Right. Erased/star-projected types probably aren't subclasses of the argumented types from typeOf(). Could implement custom typeOfInstance that looks for most specific common element in allSuperTypes for collection contents, but sounds excessive and expensive.
//                v::class.isSubclassOf(paramTypes!![k]!!.jvmErasure)
//            }
//        }
//    }
}


@DslMarker
private annotation class HandlerBuilder

// Type-safe builder function for the root hierarchy of all handler context and handler type definitions.

// @return A two-depth nested Map-like HandlerDefinitions object, which indexes handler types first by ContextId and then by HandlerId.
private fun handlerDefinitions(init: HandlerDefinitionsBuilderScope.() -> Unit): HandlerDefinitions {
    val unconfigured = HandlerDefinitionsBuilderScope()
    unconfigured.init()
    return object: HandlerDefinitions {
        override val handlerContexts = unconfigured.handlerContexts.toMap()
    }
}

// Type-safe builder scope for the root hierarchy of all handler context and handler type definitions.
@HandlerBuilder
private class HandlerDefinitionsBuilderScope {

    // Type-safe builder method for a handler context namespace.

    // @param name The name of this context.
    fun handlerContext(name: ContextId, init: HandlerContextBuilderScope.() -> Unit) {
        val unconfigured = HandlerContextBuilderScope(name)
        unconfigured.init()
        handlerContexts[name] = object: HandlerContext {
            override val name = unconfigured.name
            override val handlerTypes = unconfigured.handlers.toMap()
        }
    }

    // Type-safe builder scope for a handler context namespace.

    // @param name Name of the context.
    @HandlerBuilder
    class HandlerContextBuilderScope(val name: ContextId) {

        // Type-safe builder method for a handler type.

        // @param V The type that may be provided as an argument when running this handler type.
        // @param name The name of this handler type.
        fun <V> handler(name: HandlerId, init: HandlerTypeBuilderScope<V>.() -> Unit) {
            val unconfigured = HandlerTypeBuilderScope<V>(name)
            unconfigured.init()
            handlers[name] = object: HandlerType {
                override val name = name
                val context = this@HandlerContextBuilderScope
                override val paramTypes = unconfigured.paramTypes.toMap()
                override val paramGetter: ParamGetter = { given: Any? -> unconfigured.paramGetters.entries.associate { it.key to it.value(given as V) } }
                override fun toString() = "HandlerType:${context.name}/${name}"
            }
        }

        // Type-safe builder scope for a handler type.

        // @param V The type that may be provided as an argument when running this handler type.
        // @param name Name of the handler type.
        @HandlerBuilder
        class HandlerTypeBuilderScope<V>(val name: HandlerId) {

            // Type-safe builder method for a parameter that a handler type accepts and then sets in a Map accessible by the scripting API while the handler type is running.

            // @param R The type that this value is to be set to in the script's execution context.
            // @param name The key of this parameter in the scripting-accessible Map.
            // @param getter A function that returns the value of this parameter in the scripting-accessible Map, when given the argument passed to this handler type.
            @OptIn(ExperimentalStdlibApi::class)
            inline fun <reified R> param(name: String, noinline getter: (V) -> R = { it as R }) {
                paramTypes[name] = typeOf<R>()
                paramGetters[name] = getter
            }

            val paramTypes = mutableMapOf<String, KType>()
            val paramGetters = mutableMapOf<String, (V) -> Any?>()

        }

        val handlers = mutableMapOf<HandlerId, HandlerType>()


    }

    val handlerContexts = mutableMapOf<ContextId, HandlerContext>()

}
