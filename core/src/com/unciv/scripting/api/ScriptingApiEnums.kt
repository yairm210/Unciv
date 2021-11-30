package com.unciv.scripting.api

// **THE BELOW CODE WAS AUTOMATICALLY GENERATED WITH A SCRIPT**
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.battle.CombatAction
import com.unciv.logic.city.CityFlags
import com.unciv.logic.city.RejectionReason
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CityStatePersonality
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.Proximity
import com.unciv.logic.civilization.ReligionState
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapUnit.DoubleMovementTerrainTarget
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileMap.AssignContinentsMode
import com.unciv.logic.map.mapgenerator.MapRegions.ImpactType
import com.unciv.logic.map.mapgenerator.RiverGenerator.RiverCoordinate.BottomRightOrLeft
import com.unciv.logic.trade.TradeType
import com.unciv.logic.trade.TradeType.TradeTypeNumberType
import com.unciv.models.Tutorial
import com.unciv.models.UnitActionType
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.metadata.LocaleCode
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.models.ruleset.QuestName
import com.unciv.models.ruleset.QuestType
import com.unciv.models.ruleset.Ruleset.RulesetErrorSeverity
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unique.UniqueType.UniqueComplianceErrorSeverity
import com.unciv.models.ruleset.unit.UnitLayer
import com.unciv.models.ruleset.unit.UnitMovementType
import com.unciv.models.stats.Stat
import com.unciv.scripting.ScriptingBackendType
import com.unciv.scripting.protocol.ScriptingProtocol.KnownFlag
import com.unciv.scripting.reflection.Reflection.PathElementType
import com.unciv.scripting.utils.ScriptingApiExposure
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.audio.MusicTrackController.State
import com.unciv.ui.civilopedia.CivilopediaCategories
import com.unciv.ui.civilopedia.FormattedLine.IconDisplay
import com.unciv.ui.civilopedia.FormattedLine.LinkType
import com.unciv.ui.consolescreen.ConsoleScreen.SetTextCursorMode
import com.unciv.ui.pickerscreens.ModManagementOptions.SortType
import com.unciv.ui.utils.UncivTooltip.TipState
import com.unciv.ui.victoryscreen.RankingType
// **THE ABOVE CODE WAS AUTOMATICALLY GENERATED WITH A SCRIPT**


inline fun <reified T: Enum<T>> enumToMap() = enumValues<T>().associateBy{ it.name }

//Could try automatically finding all enums instead.

/**
 * For use in ScriptingScope. Allows interpreted scripts to access Unciv Enum constants.
 *
 * Currently exposes enum values as maps.
 */
object ScriptingApiEnums {
// **THE BELOW CODE WAS AUTOMATICALLY GENERATED WITH A SCRIPT**
    val ThreatLevel = enumToMap<com.unciv.logic.automation.ThreatLevel>()
    val CombatAction = enumToMap<com.unciv.logic.battle.CombatAction>()
    val CityFlags = enumToMap<com.unciv.logic.city.CityFlags>()
    val RejectionReason = enumToMap<com.unciv.logic.city.RejectionReason>()
    val AlertType = enumToMap<com.unciv.logic.civilization.AlertType>()
    val CityStatePersonality = enumToMap<com.unciv.logic.civilization.CityStatePersonality>()
    val CityStateType = enumToMap<com.unciv.logic.civilization.CityStateType>()
    val CivFlags = enumToMap<com.unciv.logic.civilization.CivFlags>()
    val PlayerType = enumToMap<com.unciv.logic.civilization.PlayerType>()
    val Proximity = enumToMap<com.unciv.logic.civilization.Proximity>()
    val ReligionState = enumToMap<com.unciv.logic.civilization.ReligionState>()
    val DiplomacyFlags = enumToMap<com.unciv.logic.civilization.diplomacy.DiplomacyFlags>()
    val DiplomaticModifiers = enumToMap<com.unciv.logic.civilization.diplomacy.DiplomaticModifiers>()
    val DiplomaticStatus = enumToMap<com.unciv.logic.civilization.diplomacy.DiplomaticStatus>()
    val RelationshipLevel = enumToMap<com.unciv.logic.civilization.diplomacy.RelationshipLevel>()
    val MapSize = enumToMap<com.unciv.logic.map.MapSize>()
    val DoubleMovementTerrainTarget = enumToMap<com.unciv.logic.map.MapUnit.DoubleMovementTerrainTarget>()
    val RoadStatus = enumToMap<com.unciv.logic.map.RoadStatus>()
    val AssignContinentsMode = enumToMap<com.unciv.logic.map.TileMap.AssignContinentsMode>()
    val ImpactType = enumToMap<com.unciv.logic.map.mapgenerator.MapRegions.ImpactType>()
    val BottomRightOrLeft = enumToMap<com.unciv.logic.map.mapgenerator.RiverGenerator.RiverCoordinate.BottomRightOrLeft>()
    val TradeType = enumToMap<com.unciv.logic.trade.TradeType>()
    val TradeTypeNumberType = enumToMap<com.unciv.logic.trade.TradeType.TradeTypeNumberType>()
    val Tutorial = enumToMap<com.unciv.models.Tutorial>()
    val UnitActionType = enumToMap<com.unciv.models.UnitActionType>()
    val BaseRuleset = enumToMap<com.unciv.models.metadata.BaseRuleset>()
    val GameSpeed = enumToMap<com.unciv.models.metadata.GameSpeed>()
    val LocaleCode = enumToMap<com.unciv.models.metadata.LocaleCode>()
    val BeliefType = enumToMap<com.unciv.models.ruleset.BeliefType>()
    val PolicyBranchType = enumToMap<com.unciv.models.ruleset.Policy.PolicyBranchType>()
    val QuestName = enumToMap<com.unciv.models.ruleset.QuestName>()
    val QuestType = enumToMap<com.unciv.models.ruleset.QuestType>()
    val RulesetErrorSeverity = enumToMap<com.unciv.models.ruleset.Ruleset.RulesetErrorSeverity>()
    val VictoryType = enumToMap<com.unciv.models.ruleset.VictoryType>()
    val ResourceType = enumToMap<com.unciv.models.ruleset.tile.ResourceType>()
    val TerrainType = enumToMap<com.unciv.models.ruleset.tile.TerrainType>()
    val UniqueFlag = enumToMap<com.unciv.models.ruleset.unique.UniqueFlag>()
    val UniqueParameterType = enumToMap<com.unciv.models.ruleset.unique.UniqueParameterType>()
    val UniqueTarget = enumToMap<com.unciv.models.ruleset.unique.UniqueTarget>()
    val UniqueType = enumToMap<com.unciv.models.ruleset.unique.UniqueType>()
    val UniqueComplianceErrorSeverity = enumToMap<com.unciv.models.ruleset.unique.UniqueType.UniqueComplianceErrorSeverity>()
    val UnitLayer = enumToMap<com.unciv.models.ruleset.unit.UnitLayer>()
    val UnitMovementType = enumToMap<com.unciv.models.ruleset.unit.UnitMovementType>()
    val Stat = enumToMap<com.unciv.models.stats.Stat>()
    val ScriptingBackendType = enumToMap<com.unciv.scripting.ScriptingBackendType>()
    val KnownFlag = enumToMap<com.unciv.scripting.protocol.ScriptingProtocol.KnownFlag>()
    val PathElementType = enumToMap<com.unciv.scripting.reflection.Reflection.PathElementType>()
    val ScriptingApiExposure = enumToMap<com.unciv.scripting.utils.ScriptingApiExposure>()
    val MusicTrackChooserFlags = enumToMap<com.unciv.ui.audio.MusicTrackChooserFlags>()
    val State = enumToMap<com.unciv.ui.audio.MusicTrackController.State>()
    val CivilopediaCategories = enumToMap<com.unciv.ui.civilopedia.CivilopediaCategories>()
    val IconDisplay = enumToMap<com.unciv.ui.civilopedia.FormattedLine.IconDisplay>()
    val LinkType = enumToMap<com.unciv.ui.civilopedia.FormattedLine.LinkType>()
    val SetTextCursorMode = enumToMap<com.unciv.ui.consolescreen.ConsoleScreen.SetTextCursorMode>()
    val SortType = enumToMap<com.unciv.ui.pickerscreens.ModManagementOptions.SortType>()
    val TipState = enumToMap<com.unciv.ui.utils.UncivTooltip.TipState>()
    val RankingType = enumToMap<com.unciv.ui.victoryscreen.RankingType>()
// **THE ABOVE CODE WAS AUTOMATICALLY GENERATED WITH A SCRIPT**
}
