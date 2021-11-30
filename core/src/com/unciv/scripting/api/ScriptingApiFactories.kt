package com.unciv.scripting.api

//import com.unciv.MainMenuScreen
//import com.unciv.ui.mapeditor.MapEditorScreen
//import com.unciv.logic.map.TileMap
import com.badlogic.gdx.math.Vector2


// **THE BELOW CODE WAS AUTOMATICALLY GENERATED WITH A SCRIPT**
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.*
import com.unciv.logic.*
import com.unciv.logic.automation.*
import com.unciv.logic.battle.*
import com.unciv.logic.city.*
import com.unciv.logic.civilization.*
import com.unciv.logic.civilization.RuinsManager.*
import com.unciv.logic.civilization.diplomacy.*
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers.*
import com.unciv.logic.map.*
import com.unciv.logic.map.mapgenerator.*
import com.unciv.logic.trade.*
import com.unciv.logic.trade.TradeType.*
import com.unciv.models.*
import com.unciv.models.metadata.*
import com.unciv.models.ruleset.*
import com.unciv.models.ruleset.tech.*
import com.unciv.models.ruleset.tile.*
import com.unciv.models.ruleset.unique.*
import com.unciv.models.ruleset.unique.UniqueType.*
import com.unciv.models.ruleset.unit.*
import com.unciv.models.simulation.*
import com.unciv.models.tilesets.*
import com.unciv.models.translations.*
import com.unciv.scripting.*
import com.unciv.scripting.ScriptingScope.*
import com.unciv.scripting.api.*
import com.unciv.scripting.protocol.*
import com.unciv.ui.*
import com.unciv.ui.audio.*
import com.unciv.ui.cityscreen.*
import com.unciv.ui.civilopedia.*
import com.unciv.ui.consolescreen.*
import com.unciv.ui.map.*
import com.unciv.ui.mapeditor.*
import com.unciv.ui.mapeditor.MapEditorMenuPopup.*
import com.unciv.ui.newgamescreen.*
import com.unciv.ui.overviewscreen.*
import com.unciv.ui.pickerscreens.*
import com.unciv.ui.saves.*
import com.unciv.ui.tilegroups.*
import com.unciv.ui.trade.*
import com.unciv.ui.tutorials.*
import com.unciv.ui.utils.*
import com.unciv.ui.victoryscreen.*
import com.unciv.ui.worldscreen.*
import com.unciv.ui.worldscreen.WorldMapHolder.*
import com.unciv.ui.worldscreen.bottombar.*
import com.unciv.ui.worldscreen.mainmenu.*
import com.unciv.ui.worldscreen.unit.*
import java.io.*
import java.util.*
import kotlin.math.*
import kotlin.reflect.full.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
// **THE ABOVE CODE WAS AUTOMATICALLY GENERATED WITH A SCRIPT**


// Honestly, see if you can just instantiate them by string/name instead.
// Class.forName("com.unciv.etc").newInstance(), or Class.forName("com.unciv.etc").kotlin, or something.

/**
 * For use in ScriptingScope. Allows interpreted scripts to make new instances of Kotlin/JVM classes.
 */
object ScriptingApiFactories {
    //This, and possible ApiHelpers itself, need better nested namespaces.
    val Game = object {
    }
    val Math = object {
    }
    val Rulesets = object {
    }
    val Kotlin = object {
    }
    val Gui = object {
        fun MainMenuScreen() = com.unciv.MainMenuScreen()
        fun MapEditorScreen(map: TileMap) = com.unciv.ui.mapeditor.MapEditorScreen(map)
    }
    //Class.forName("java.lang.String").kotlin.primaryConstructor.call
    fun test(qualName: String) = Class.forName(qualName)
    fun test2(qualName: String) = Class.forName(qualName).kotlin //Not accessible. Probably extension/compiler-made?
    fun instanceFromQualname(qualName: String, args: List<Any?>) = Class.forName(qualName).kotlin.primaryConstructor!!.call(*args.toTypedArray())
    // TODO: Use generalized InstanceMethodDispatcher to find right callable in KClass.constructors if no primary constructor.

    // apiHelpers.Factories.instanceFromQualname('java.lang.String', [])
    // apiHelpers.Factories.instanceFromQualname('com.unciv.logic.map.MapUnit', [])
    // apiHelpers.Factories.instanceFromQualname('com.unciv.logic.city.CityStats', [civInfo.cities[0]])
    // Fails: apiHelpers.Factories.instanceFromQualname('com.badlogic.gdx.math.Vector2', [1.5, 2.5])

    // Refer: https://stackoverflow.com/questions/40672880/creating-a-new-instance-of-a-kclass

    // See https://stackoverflow.com/questions/59936471/kotlin-reflect-package-and-get-all-classes. Build structured map of all classes?

    // apiHelpers.registeredInstances['x'] = apiHelpers.Factories.test2('com.badlogic.gdx.math.Vector2')
    // apiHelpers.registeredInstances['x'].constructors[1].call(apiHelpers.Factories.arrayOf([1, 2]))
    // apiHelpers.registeredInstances['y'] = apiHelpers.Factories.test('com.badlogic.gdx.math.Vector2')
    fun arrayOf(elements: Collection<Any?>): Array<*> = elements.toTypedArray()
    fun arrayOfAny(elements: Collection<Any>): Array<Any> = elements.toTypedArray()
    fun arrayOfString(elements: Collection<String>): Array<String> = elements.toTypedArray()
    fun Vector2(x: Number, y: Number) = com.badlogic.gdx.math.Vector2(x.toFloat(), y.toFloat())
//    fun MapUnit() = "NotImplemented"
//    fun Technology() = "NotImplemented"

// **THE BELOW CODE WAS AUTOMATICALLY GENERATED WITH A SCRIPT**
fun JsonParser() = com.unciv.JsonParser()
fun MainMenuScreen() = com.unciv.MainMenuScreen()
fun UncivGame(parameters: UncivGameParameters) = com.unciv.UncivGame(parameters=parameters)
fun UncivGameParameters(version: String, crashReportSender: CrashReportSender?, cancelDiscordEvent: (() -> Unit)?, fontImplementation: NativeFontImplementation?, consoleMode: Boolean, customSaveLocationHelper: CustomSaveLocationHelper?, limitOrientationsHelper: LimitOrientationsHelper?) = com.unciv.UncivGameParameters(version=version, crashReportSender=crashReportSender, cancelDiscordEvent=cancelDiscordEvent, fontImplementation=fontImplementation, consoleMode=consoleMode, customSaveLocationHelper=customSaveLocationHelper, limitOrientationsHelper=limitOrientationsHelper)
fun BarbarianManager() = com.unciv.logic.BarbarianManager()
fun GameInfo() = com.unciv.logic.GameInfo()
fun UncivShowableException(missingMods: String) = com.unciv.logic.UncivShowableException(missingMods=missingMods)
fun BarbarianAutomation(civInfo: CivilizationInfo) = com.unciv.logic.automation.BarbarianAutomation(civInfo=civInfo)
fun ConstructionAutomation(cityConstructions: CityConstructions) = com.unciv.logic.automation.ConstructionAutomation(cityConstructions=cityConstructions)
fun BattleDamageModifier(vs: String, modificationAmount: Float) = com.unciv.logic.battle.BattleDamageModifier(vs=vs, modificationAmount=modificationAmount)
fun CityCombatant(city: CityInfo) = com.unciv.logic.battle.CityCombatant(city=city)
fun MapUnitCombatant(unit: MapUnit) = com.unciv.logic.battle.MapUnitCombatant(unit=unit)
fun CityConstructions() = com.unciv.logic.city.CityConstructions()
fun CityExpansionManager() = com.unciv.logic.city.CityExpansionManager()
fun CityInfo() = com.unciv.logic.city.CityInfo()
fun CityInfoConquestFunctions(city: CityInfo) = com.unciv.logic.city.CityInfoConquestFunctions(city=city)
fun CityInfoReligionManager() = com.unciv.logic.city.CityInfoReligionManager()
fun CityStats(cityInfo: CityInfo) = com.unciv.logic.city.CityStats(cityInfo=cityInfo)
fun PopulationManager() = com.unciv.logic.city.PopulationManager()
fun CapitalConnectionsFinder(civInfo: CivilizationInfo) = com.unciv.logic.civilization.CapitalConnectionsFinder(civInfo=civInfo)
fun CityStateFunctions(civInfo: CivilizationInfo) = com.unciv.logic.civilization.CityStateFunctions(civInfo=civInfo)
fun CivConstructions() = com.unciv.logic.civilization.CivConstructions()
fun CivInfoStats(civInfo: CivilizationInfo) = com.unciv.logic.civilization.CivInfoStats(civInfo=civInfo)
fun CivInfoTransientUpdater(civInfo: CivilizationInfo) = com.unciv.logic.civilization.CivInfoTransientUpdater(civInfo=civInfo)
fun CivilizationInfo() = com.unciv.logic.civilization.CivilizationInfo()
fun GoldenAgeManager() = com.unciv.logic.civilization.GoldenAgeManager()
fun GreatPersonManager() = com.unciv.logic.civilization.GreatPersonManager()
fun PolicyManager() = com.unciv.logic.civilization.PolicyManager()
fun PopupAlert() = com.unciv.logic.civilization.PopupAlert()
fun QuestManager() = com.unciv.logic.civilization.QuestManager()
fun ReligionManager() = com.unciv.logic.civilization.ReligionManager()
fun RuinsManager() = com.unciv.logic.civilization.RuinsManager.RuinsManager()
fun TechManager() = com.unciv.logic.civilization.TechManager()
fun VictoryManager() = com.unciv.logic.civilization.VictoryManager()
fun DiplomacyManager() = com.unciv.logic.civilization.diplomacy.DiplomacyManager()
fun BFS(startingPoint: TileInfo, predicate: (TileInfo) -> Boolean) = com.unciv.logic.map.BFS(startingPoint=startingPoint, predicate=predicate)
fun MapSizeNew() = com.unciv.logic.map.MapSizeNew()
fun MapUnit() = com.unciv.logic.map.MapUnit()
fun TileMap() = com.unciv.logic.map.TileMap()
fun UnitMovementAlgorithms(unit: MapUnit) = com.unciv.logic.map.UnitMovementAlgorithms(unit=unit)
fun UnitPromotions() = com.unciv.logic.map.UnitPromotions()
fun MapGenerator(ruleset: Ruleset) = com.unciv.logic.map.mapgenerator.MapGenerator(ruleset=ruleset)
fun MapLandmassGenerator(ruleset: Ruleset, randomness: MapGenerationRandomness) = com.unciv.logic.map.mapgenerator.MapLandmassGenerator(ruleset=ruleset, randomness=randomness)
fun MapRegions(ruleset: Ruleset) = com.unciv.logic.map.mapgenerator.MapRegions(ruleset=ruleset)
fun NaturalWonderGenerator(ruleset: Ruleset, randomness: MapGenerationRandomness) = com.unciv.logic.map.mapgenerator.NaturalWonderGenerator(ruleset=ruleset, randomness=randomness)
fun RiverGenerator(tileMap: TileMap, randomness: MapGenerationRandomness) = com.unciv.logic.map.mapgenerator.RiverGenerator(tileMap=tileMap, randomness=randomness)
fun Trade() = com.unciv.logic.trade.Trade()
fun TradeEvaluation() = com.unciv.logic.trade.TradeEvaluation()
fun TradeLogic(ourCivilization: CivilizationInfo, otherCivilization: CivilizationInfo) = com.unciv.logic.trade.TradeLogic(ourCivilization=ourCivilization, otherCivilization=otherCivilization)
fun TradeOffersList() = com.unciv.logic.trade.TradeOffersList()
fun AttackableTile(tileToAttackFrom: TileInfo, tileToAttack: TileInfo, movementLeftAfterMovingToAttackTile: Float) = com.unciv.models.AttackableTile(tileToAttackFrom=tileToAttackFrom, tileToAttack=tileToAttack, movementLeftAfterMovingToAttackTile=movementLeftAfterMovingToAttackTile)
fun Religion() = com.unciv.models.Religion()
fun GameParameters() = com.unciv.models.metadata.GameParameters()
fun GameSettings() = com.unciv.models.metadata.GameSettings()
fun GameSetupInfo(gameParameters: GameParameters, mapParameters: MapParameters) = com.unciv.models.metadata.GameSetupInfo(gameParameters=gameParameters, mapParameters=mapParameters)
fun Player(chosenCiv: String) = com.unciv.models.metadata.Player(chosenCiv=chosenCiv)
fun Belief() = com.unciv.models.ruleset.Belief()
fun Building() = com.unciv.models.ruleset.Building()
fun Difficulty() = com.unciv.models.ruleset.Difficulty()
fun Era() = com.unciv.models.ruleset.Era()
fun Nation() = com.unciv.models.ruleset.Nation()
fun PolicyBranch() = com.unciv.models.ruleset.PolicyBranch()
fun Quest() = com.unciv.models.ruleset.Quest()
fun RuinReward() = com.unciv.models.ruleset.RuinReward()
fun TechColumn() = com.unciv.models.ruleset.tech.TechColumn()
fun Technology() = com.unciv.models.ruleset.tech.Technology()
fun Terrain() = com.unciv.models.ruleset.tile.Terrain()
fun TileImprovement() = com.unciv.models.ruleset.tile.TileImprovement()
fun TileResource() = com.unciv.models.ruleset.tile.TileResource()
fun Unique(text: String, sourceObjectType: UniqueTarget?, sourceObjectName: String?) = com.unciv.models.ruleset.unique.Unique(text=text, sourceObjectType=sourceObjectType, sourceObjectName=sourceObjectName)
fun BaseUnit() = com.unciv.models.ruleset.unit.BaseUnit()
fun Promotion() = com.unciv.models.ruleset.unit.Promotion()
fun UnitType() = com.unciv.models.ruleset.unit.UnitType()
fun MutableInt(value: Int) = com.unciv.models.simulation.MutableInt(value=value)
fun SimulationStep(gameInfo: GameInfo) = com.unciv.models.simulation.SimulationStep(gameInfo=gameInfo)
fun TileSetConfig() = com.unciv.models.tilesets.TileSetConfig()
fun TranslationEntry(entry: String) = com.unciv.models.translations.TranslationEntry(entry=entry)
fun Translations() = com.unciv.models.translations.Translations()
fun ScriptingScope(civInfo: CivilizationInfo?, gameInfo: GameInfo?, uncivGame: UncivGame?, worldScreen: WorldScreen?, mapEditorScreen: MapEditorScreen?) = com.unciv.scripting.ScriptingScope(civInfo=civInfo, gameInfo=gameInfo, uncivGame=uncivGame, worldScreen=worldScreen, mapEditorScreen=mapEditorScreen)
fun ApiHelpers(scriptingScope: ScriptingScope) = com.unciv.scripting.ScriptingScope.ApiHelpers(scriptingScope=scriptingScope)
fun ScriptingApiInstanceRegistry() = com.unciv.scripting.api.ScriptingApiInstanceRegistry()
fun ScriptingRawReplManager(scriptingScope: ScriptingScope, blackbox: Blackbox) = com.unciv.scripting.protocol.ScriptingRawReplManager(scriptingScope=scriptingScope, blackbox=blackbox)
fun SubprocessBlackbox(processCmd: Array<String>) = com.unciv.scripting.protocol.SubprocessBlackbox(processCmd=processCmd)
fun AddMultiplayerGameScreen(backScreen: MultiplayerScreen) = com.unciv.ui.AddMultiplayerGameScreen(backScreen=backScreen)
fun EditMultiplayerGameInfoScreen(gameInfo: GameInfoPreview?, gameName: String, backScreen: MultiplayerScreen) = com.unciv.ui.EditMultiplayerGameInfoScreen(gameInfo=gameInfo, gameName=gameName, backScreen=backScreen)
fun LanguagePickerScreen() = com.unciv.ui.LanguagePickerScreen()
fun MultiplayerScreen(previousScreen: BaseScreen) = com.unciv.ui.MultiplayerScreen(previousScreen=previousScreen)
fun MusicController() = com.unciv.ui.audio.MusicController()
fun MusicTrackController(volume: Float) = com.unciv.ui.audio.MusicTrackController(volume=volume)
fun CityConstructionsTable(cityScreen: CityScreen) = com.unciv.ui.cityscreen.CityConstructionsTable(cityScreen=cityScreen)
fun CityInfoTable(cityScreen: CityScreen) = com.unciv.ui.cityscreen.CityInfoTable(cityScreen=cityScreen)
fun CityReligionInfoTable(religionManager: CityInfoReligionManager, showMajority: Boolean) = com.unciv.ui.cityscreen.CityReligionInfoTable(religionManager=religionManager, showMajority=showMajority)
fun CityScreen(city: CityInfo, selectedConstruction: IConstruction?, selectedTile: TileInfo?) = com.unciv.ui.cityscreen.CityScreen(city=city, selectedConstruction=selectedConstruction, selectedTile=selectedTile)
fun CityScreenCityPickerTable(cityScreen: CityScreen) = com.unciv.ui.cityscreen.CityScreenCityPickerTable(cityScreen=cityScreen)
fun CityScreenTileTable(cityScreen: CityScreen) = com.unciv.ui.cityscreen.CityScreenTileTable(cityScreen=cityScreen)
fun CityStatsTable(cityScreen: CityScreen) = com.unciv.ui.cityscreen.CityStatsTable(cityScreen=cityScreen)
fun CityTileGroup(city: CityInfo, tileInfo: TileInfo, tileSetStrings: TileSetStrings) = com.unciv.ui.cityscreen.CityTileGroup(city=city, tileInfo=tileInfo, tileSetStrings=tileSetStrings)
fun ConstructionInfoTable(cityScreen: CityScreen) = com.unciv.ui.cityscreen.ConstructionInfoTable(cityScreen=cityScreen)
fun SpecialistAllocationTable(cityScreen: CityScreen) = com.unciv.ui.cityscreen.SpecialistAllocationTable(cityScreen=cityScreen)
fun YieldGroup() = com.unciv.ui.cityscreen.YieldGroup()
fun CivilopediaScreen(ruleset: Ruleset, previousScreen: BaseScreen, category: CivilopediaCategories, link: String) = com.unciv.ui.civilopedia.CivilopediaScreen(ruleset=ruleset, previousScreen=previousScreen, category=category, link=link)
fun FormattedLine(text: String, link: String, icon: String, extraImage: String, imageSize: Float, size: Int, header: Int, indent: Int, padding: Float, color: String, separator: Boolean, starred: Boolean, centered: Boolean, iconCrossed: Boolean) = com.unciv.ui.civilopedia.FormattedLine(text=text, link=link, icon=icon, extraImage=extraImage, imageSize=imageSize, size=size, header=header, indent=indent, padding=padding, color=color, separator=separator, starred=starred, centered=centered, iconCrossed=iconCrossed)
fun ConsoleScreen(scriptingState: ScriptingState, closeAction: () -> Unit) = com.unciv.ui.consolescreen.ConsoleScreen(scriptingState=scriptingState, closeAction=closeAction)
fun EditorMapHolder(mapEditorScreen: MapEditorScreen, tileMap: TileMap) = com.unciv.ui.mapeditor.EditorMapHolder(mapEditorScreen=mapEditorScreen, tileMap=tileMap)
fun GameParametersScreen(mapEditorScreen: MapEditorScreen) = com.unciv.ui.mapeditor.GameParametersScreen(mapEditorScreen=mapEditorScreen)
fun MapEditorMenuPopup(mapEditorScreen: MapEditorScreen) = com.unciv.ui.mapeditor.MapEditorMenuPopup(mapEditorScreen=mapEditorScreen)
fun MapEditorRulesetPopup(mapEditorScreen: MapEditorScreen) = com.unciv.ui.mapeditor.MapEditorMenuPopup.MapEditorRulesetPopup(mapEditorScreen=mapEditorScreen)
fun MapEditorOptionsTable(mapEditorScreen: MapEditorScreen) = com.unciv.ui.mapeditor.MapEditorOptionsTable(mapEditorScreen=mapEditorScreen)
fun MapEditorScreen() = com.unciv.ui.mapeditor.MapEditorScreen()
fun NewMapScreen(mapParameters: MapParameters) = com.unciv.ui.mapeditor.NewMapScreen(mapParameters=mapParameters)
fun SaveAndLoadMapScreen(mapToSave: TileMap?, save: Boolean, previousScreen: BaseScreen) = com.unciv.ui.mapeditor.SaveAndLoadMapScreen(mapToSave=mapToSave, save=save, previousScreen=previousScreen)
fun GameOptionsTable(previousScreen: IPreviousScreen, isPortrait: Boolean, updatePlayerPickerTable: (desiredCiv:String)->Unit) = com.unciv.ui.newgamescreen.GameOptionsTable(previousScreen=previousScreen, isPortrait=isPortrait, updatePlayerPickerTable=updatePlayerPickerTable)
fun MapOptionsTable(newGameScreen: NewGameScreen) = com.unciv.ui.newgamescreen.MapOptionsTable(newGameScreen=newGameScreen)
fun MapParametersTable(mapParameters: MapParameters, isEmptyMapAllowed: Boolean) = com.unciv.ui.newgamescreen.MapParametersTable(mapParameters=mapParameters, isEmptyMapAllowed=isEmptyMapAllowed)
fun ModCheckboxTable(mods: LinkedHashSet<String>, baseRuleset: String, screen: BaseScreen, isPortrait: Boolean, onUpdate: (String) -> Unit) = com.unciv.ui.newgamescreen.ModCheckboxTable(mods=mods, baseRuleset=baseRuleset, screen=screen, isPortrait=isPortrait, onUpdate=onUpdate)
fun NationTable(nation: Nation, width: Float, minHeight: Float, ruleset: Ruleset?) = com.unciv.ui.newgamescreen.NationTable(nation=nation, width=width, minHeight=minHeight, ruleset=ruleset)
fun NewGameScreen(previousScreen: BaseScreen, _gameSetupInfo: GameSetupInfo?) = com.unciv.ui.newgamescreen.NewGameScreen(previousScreen=previousScreen, _gameSetupInfo=_gameSetupInfo)
fun PlayerPickerTable(previousScreen: IPreviousScreen, gameParameters: GameParameters, blockWidth: Float) = com.unciv.ui.newgamescreen.PlayerPickerTable(previousScreen=previousScreen, gameParameters=gameParameters, blockWidth=blockWidth)
fun CityOverviewTable(viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen) = com.unciv.ui.overviewscreen.CityOverviewTable(viewingPlayer=viewingPlayer, overviewScreen=overviewScreen)
fun DiplomacyOverviewTable(viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen) = com.unciv.ui.overviewscreen.DiplomacyOverviewTable(viewingPlayer=viewingPlayer, overviewScreen=overviewScreen)
fun EmpireOverviewScreen(viewingPlayer: CivilizationInfo, defaultPage: String) = com.unciv.ui.overviewscreen.EmpireOverviewScreen(viewingPlayer=viewingPlayer, defaultPage=defaultPage)
fun ReligionOverviewTable(viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen) = com.unciv.ui.overviewscreen.ReligionOverviewTable(viewingPlayer=viewingPlayer, overviewScreen=overviewScreen)
fun ResourcesOverviewTable(viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen) = com.unciv.ui.overviewscreen.ResourcesOverviewTable(viewingPlayer=viewingPlayer, overviewScreen=overviewScreen)
fun StatsOverviewTable(viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen) = com.unciv.ui.overviewscreen.StatsOverviewTable(viewingPlayer=viewingPlayer, overviewScreen=overviewScreen)
fun TradesOverviewTable(viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen) = com.unciv.ui.overviewscreen.TradesOverviewTable(viewingPlayer=viewingPlayer, overviewScreen=overviewScreen)
fun UnitOverviewTable(viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen) = com.unciv.ui.overviewscreen.UnitOverviewTable(viewingPlayer=viewingPlayer, overviewScreen=overviewScreen)
fun WonderOverviewTable(viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen) = com.unciv.ui.overviewscreen.WonderOverviewTable(viewingPlayer=viewingPlayer, overviewScreen=overviewScreen)
fun DiplomaticVotePickerScreen(votingCiv: CivilizationInfo) = com.unciv.ui.pickerscreens.DiplomaticVotePickerScreen(votingCiv=votingCiv)
fun DiplomaticVoteResultScreen(votesCast: HashMap<String, String>, viewingCiv: CivilizationInfo) = com.unciv.ui.pickerscreens.DiplomaticVoteResultScreen(votesCast=votesCast, viewingCiv=viewingCiv)
fun GreatPersonPickerScreen(civInfo: CivilizationInfo) = com.unciv.ui.pickerscreens.GreatPersonPickerScreen(civInfo=civInfo)
fun ImprovementPickerScreen(tileInfo: TileInfo, unit: MapUnit, onAccept: ()->Unit) = com.unciv.ui.pickerscreens.ImprovementPickerScreen(tileInfo=tileInfo, unit=unit, onAccept=onAccept)
fun ModManagementOptions(modManagementScreen: ModManagementScreen) = com.unciv.ui.pickerscreens.ModManagementOptions(modManagementScreen=modManagementScreen)
fun ModManagementScreen(previousInstalledMods: HashMap<String, ModUIData>?, previousOnlineMods: HashMap<String, ModUIData>?) = com.unciv.ui.pickerscreens.ModManagementScreen(previousInstalledMods=previousInstalledMods, previousOnlineMods=previousOnlineMods)
fun PantheonPickerScreen(choosingCiv: CivilizationInfo, gameInfo: GameInfo) = com.unciv.ui.pickerscreens.PantheonPickerScreen(choosingCiv=choosingCiv, gameInfo=gameInfo)
fun PolicyPickerScreen(worldScreen: WorldScreen, civInfo: CivilizationInfo) = com.unciv.ui.pickerscreens.PolicyPickerScreen(worldScreen=worldScreen, civInfo=civInfo)
fun PromotionPickerScreen(unit: MapUnit) = com.unciv.ui.pickerscreens.PromotionPickerScreen(unit=unit)
fun ReligiousBeliefsPickerScreen(choosingCiv: CivilizationInfo, gameInfo: GameInfo, beliefsToChoose: Counter<BeliefType>, pickIconAndName: Boolean) = com.unciv.ui.pickerscreens.ReligiousBeliefsPickerScreen(choosingCiv=choosingCiv, gameInfo=gameInfo, beliefsToChoose=beliefsToChoose, pickIconAndName=pickIconAndName)
fun TechButton(techName: String, techManager: TechManager, isWorldScreen: Boolean) = com.unciv.ui.pickerscreens.TechButton(techName=techName, techManager=techManager, isWorldScreen=isWorldScreen)
fun LoadGameScreen(previousScreen: BaseScreen) = com.unciv.ui.saves.LoadGameScreen(previousScreen=previousScreen)
fun SaveGameScreen(gameInfo: GameInfo) = com.unciv.ui.saves.SaveGameScreen(gameInfo=gameInfo)
fun CityButton(city: CityInfo, tileGroup: WorldTileGroup) = com.unciv.ui.tilegroups.CityButton(city=city, tileGroup=tileGroup)
fun TileGroupIcons(tileGroup: TileGroup) = com.unciv.ui.tilegroups.TileGroupIcons(tileGroup=tileGroup)
fun TileSetStrings() = com.unciv.ui.tilegroups.TileSetStrings()
fun WorldTileGroup(worldScreen: WorldScreen, tileInfo: TileInfo, tileSetStrings: TileSetStrings) = com.unciv.ui.tilegroups.WorldTileGroup(worldScreen=worldScreen, tileInfo=tileInfo, tileSetStrings=tileSetStrings)
fun DiplomacyScreen(viewingCiv: CivilizationInfo) = com.unciv.ui.trade.DiplomacyScreen(viewingCiv=viewingCiv)
fun LeaderIntroTable(civInfo: CivilizationInfo, hello: String) = com.unciv.ui.trade.LeaderIntroTable(civInfo=civInfo, hello=hello)
fun OfferColumnsTable(tradeLogic: TradeLogic, screen: DiplomacyScreen, onChange: () -> Unit) = com.unciv.ui.trade.OfferColumnsTable(tradeLogic=tradeLogic, screen=screen, onChange=onChange)
fun OffersListScroll(persistenceID: String, onOfferClicked: (TradeOffer) -> Unit) = com.unciv.ui.trade.OffersListScroll(persistenceID=persistenceID, onOfferClicked=onOfferClicked)
fun TradeTable(otherCivilization: CivilizationInfo, stage: DiplomacyScreen) = com.unciv.ui.trade.TradeTable(otherCivilization=otherCivilization, stage=stage)
fun TutorialController(screen: BaseScreen) = com.unciv.ui.tutorials.TutorialController(screen=screen)
fun TutorialRender(screen: BaseScreen) = com.unciv.ui.tutorials.TutorialRender(screen=screen)
fun AskNumberPopup(screen: BaseScreen, label: String, icon: IconCircleGroup, defaultText: String, amountButtons: List<Int>, bounds: IntRange, errorText: String, validate: (input: Int) -> Boolean, actionOnOk: (input: Int) -> Unit) = com.unciv.ui.utils.AskNumberPopup(screen=screen, label=label, icon=icon, defaultText=defaultText, amountButtons=amountButtons, bounds=bounds, errorText=errorText, validate=validate, actionOnOk=actionOnOk)
fun AskTextPopup(screen: BaseScreen, label: String, icon: IconCircleGroup, defaultText: String, errorText: String, maxLength: Int, validate: (input: String) -> Boolean, actionOnOk: (input: String) -> Unit) = com.unciv.ui.utils.AskTextPopup(screen=screen, label=label, icon=icon, defaultText=defaultText, errorText=errorText, maxLength=maxLength, validate=validate, actionOnOk=actionOnOk)
fun ExitGamePopup(screen: BaseScreen, force: Boolean) = com.unciv.ui.utils.ExitGamePopup(screen=screen, force=force)
fun ExpanderTab(title: String, fontSize: Int, icon: Actor?, startsOutOpened: Boolean, defaultPad: Float, headerPad: Float, expanderWidth: Float, persistenceID: String?, onChange: (() -> Unit)?, initContent: ((Table) -> Unit)?) = com.unciv.ui.utils.ExpanderTab(title=title, fontSize=fontSize, icon=icon, startsOutOpened=startsOutOpened, defaultPad=defaultPad, headerPad=headerPad, expanderWidth=expanderWidth, persistenceID=persistenceID, onChange=onChange, initContent=initContent)
fun IconCircleGroup(size: Float, actor: Actor, resizeActor: Boolean, color: Color) = com.unciv.ui.utils.IconCircleGroup(size=size, actor=actor, resizeActor=resizeActor, color=color)
fun TabbedPager(minimumWidth: Float, maximumWidth: Float, minimumHeight: Float, maximumHeight: Float, headerFontSize: Int, headerFontColor: Color, highlightColor: Color, backgroundColor: Color, headerPadding: Float, capacity: Int) = com.unciv.ui.utils.TabbedPager(minimumWidth=minimumWidth, maximumWidth=maximumWidth, minimumHeight=minimumHeight, maximumHeight=maximumHeight, headerFontSize=headerFontSize, headerFontColor=headerFontColor, highlightColor=highlightColor, backgroundColor=backgroundColor, headerPadding=headerPadding, capacity=capacity)
fun ToastPopup(message: String, screen: BaseScreen, time: Long) = com.unciv.ui.utils.ToastPopup(message=message, screen=screen, time=time)
fun UncivSlider(min: Float, max: Float, step: Float, vertical: Boolean, plusMinus: Boolean, initial: Float, sound: UncivSound, getTipText: ((Float) -> String)?, onChange: ((Float) -> Unit)?) = com.unciv.ui.utils.UncivSlider(min=min, max=max, step=step, vertical=vertical, plusMinus=plusMinus, initial=initial, sound=sound, getTipText=getTipText, onChange=onChange)
fun UnitGroup(unit: MapUnit, size: Float) = com.unciv.ui.utils.UnitGroup(unit=unit, size=size)
fun WrappableLabel(text: String, expectedWidth: Float, fontColor: Color, fontSize: Int) = com.unciv.ui.utils.WrappableLabel(text=text, expectedWidth=expectedWidth, fontColor=fontColor, fontSize=fontSize)
fun VictoryScreen(worldScreen: WorldScreen) = com.unciv.ui.victoryscreen.VictoryScreen(worldScreen=worldScreen)
fun AlertPopup(worldScreen: WorldScreen, popupAlert: PopupAlert) = com.unciv.ui.worldscreen.AlertPopup(worldScreen=worldScreen, popupAlert=popupAlert)
fun Minimap(mapHolder: WorldMapHolder, minimapSize: Int) = com.unciv.ui.worldscreen.Minimap(mapHolder=mapHolder, minimapSize=minimapSize)
fun NotificationsScroll(worldScreen: WorldScreen) = com.unciv.ui.worldscreen.NotificationsScroll(worldScreen=worldScreen)
fun PlayerReadyScreen(gameInfo: GameInfo, currentPlayerCiv: CivilizationInfo) = com.unciv.ui.worldscreen.PlayerReadyScreen(gameInfo=gameInfo, currentPlayerCiv=currentPlayerCiv)
fun TradePopup(worldScreen: WorldScreen) = com.unciv.ui.worldscreen.TradePopup(worldScreen=worldScreen)
fun WorldMapHolder(worldScreen: WorldScreen, tileMap: TileMap) = com.unciv.ui.worldscreen.WorldMapHolder(worldScreen=worldScreen, tileMap=tileMap)
fun MoveHereButtonDto(unitToTurnsToDestination: HashMap<MapUnit, Int>, tileInfo: TileInfo) = com.unciv.ui.worldscreen.WorldMapHolder.MoveHereButtonDto(unitToTurnsToDestination=unitToTurnsToDestination, tileInfo=tileInfo)
fun SwapWithButtonDto(unit: MapUnit, tileInfo: TileInfo) = com.unciv.ui.worldscreen.WorldMapHolder.SwapWithButtonDto(unit=unit, tileInfo=tileInfo)
fun WorldScreen(gameInfo: GameInfo, viewingCiv: CivilizationInfo) = com.unciv.ui.worldscreen.WorldScreen(gameInfo=gameInfo, viewingCiv=viewingCiv)
fun WorldScreenTopBar(worldScreen: WorldScreen) = com.unciv.ui.worldscreen.WorldScreenTopBar(worldScreen=worldScreen)
fun BattleTable(worldScreen: WorldScreen) = com.unciv.ui.worldscreen.bottombar.BattleTable(worldScreen=worldScreen)
fun TileInfoTable(viewingCiv: CivilizationInfo) = com.unciv.ui.worldscreen.bottombar.TileInfoTable(viewingCiv=viewingCiv)
fun OptionsPopup(previousScreen: BaseScreen) = com.unciv.ui.worldscreen.mainmenu.OptionsPopup(previousScreen=previousScreen)
fun WorldScreenCommunityPopup(worldScreen: WorldScreen) = com.unciv.ui.worldscreen.mainmenu.WorldScreenCommunityPopup(worldScreen=worldScreen)
fun WorldScreenMenuPopup(worldScreen: WorldScreen) = com.unciv.ui.worldscreen.mainmenu.WorldScreenMenuPopup(worldScreen=worldScreen)
fun IdleUnitButton(unitTable: UnitTable, tileMapHolder: WorldMapHolder, previous: Boolean) = com.unciv.ui.worldscreen.unit.IdleUnitButton(unitTable=unitTable, tileMapHolder=tileMapHolder, previous=previous)
fun UnitActionsTable(worldScreen: WorldScreen) = com.unciv.ui.worldscreen.unit.UnitActionsTable(worldScreen=worldScreen)
fun UnitTable(worldScreen: WorldScreen) = com.unciv.ui.worldscreen.unit.UnitTable(worldScreen=worldScreen)
// **THE ABOVE CODE WAS AUTOMATICALLY GENERATED WITH A SCRIPT**

}

