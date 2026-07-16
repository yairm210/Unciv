package com.unciv.models.ruleset.nation

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.models.ImmutableColor
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.objectdescriptions.NationDescriptions.getCivilopediaTextLinesImpl
import yairm210.purity.annotations.Readonly

class Nation : RulesetObject() {
    var leaderName = ""

    /**
     * Retrieves a display name for the nation's leader, considering the provided title (untranslated).
     *
     * @param [title] Optional title to apply to the leader. For example: `[leaderName] the Great`
     */
    @Readonly fun getLeaderDisplayName(title: String = ""): String = when {
        isCityState || isSpectator -> name
        title.isEmpty() -> "[$leaderName] of [$name]"
        else -> "[${title.fillPlaceholders(leaderName)}] of [$name]"
    }

    val style = ""
    @Readonly fun getStyleOrCivName() = style.ifEmpty { name }

    var cityStateType: String? = null
    var preferredVictoryType: String = Constants.neutralVictoryType

    /// The following all have audio hooks to play corresponding leader
    /// voice clips - named <civName>.<fieldName>, e.g. "America.defeated.ogg"
    /** Shown for AlertType.WarDeclaration, when other Civs declare war on a player */
    var declaringWar = ""
    /** Shown in DiplomacyScreen when a player declares war */
    var attacked = ""
    /** Shown for AlertType.Defeated */
    var defeated = ""
    /** Shown for MajorCivDiplomacyTable.getDenounceButton */
    var denounced = ""

    /** Popup message from the leader that issued a denouncement. */
    var neutralDenouncing = ""
    /** Popup message from the leader that issued a denouncement, when the relationship is poor. */
    var hateDenouncing = ""
    /** Shown for Declaration of Friendship */
    var declaringFriendship = ""
    /** Popup message from the leader that accepted or rejected a demand */
    var acceptingDemand = ""
    var neutralRejectingDemand = ""
    var hateRejectingDemand = ""
    /** Shown for AlertType.FirstContact */
    var introduction = ""
    /** Shown in TradePopup when other Civs initiate trade with a player */
    var tradeRequest = ""
    /** Shown in DiplomacyScreen when a player contacts another major civ with RelationshipLevel.Afraid or better */
    var neutralHello = ""
    /** Shown in DiplomacyScreen when a player contacts another major civ with RelationshipLevel.Enemy or worse */
    var hateHello = ""

    lateinit var outerColor: List<Int>
    var uniqueName = ""
    var uniqueText = ""
    var innerColor: List<Int>? = null
    var startBias = ArrayList<String>()
    var personality: String? = null

    var startIntroPart1 = ""
    var startIntroPart2 = ""

    /* Properties present in json but not yet implemented:
    var adjective = ArrayList<String>()
     */

    var spyNames = ArrayList<String>()

    var favoredReligion: String? = null

    var cities: ArrayList<String> = arrayListOf()

    override fun getUniqueTarget() = UniqueTarget.Nation

    @Transient
    private var outerColorObject:ImmutableColor = ImmutableColor(Color.WHITE) // Not lateinit for unit tests
    fun getOuterColor(): ImmutableColor = outerColorObject

    @Transient
    private var innerColorObject: ImmutableColor = ImmutableColor(Color.BLACK) // Not lateinit for unit tests

    fun getInnerColor(): ImmutableColor = innerColorObject

    val isCityState by lazy { cityStateType != null }
    val isMajorCiv by lazy { !isBarbarian && !isCityState && !isSpectator }
    val isBarbarian by lazy { name == Constants.barbarians }
    val isSpectator by lazy { name == Constants.spectator }

    // This is its own transient because we'll need to check this for every tile-to-tile movement which is harsh
    @Transient
    var forestsAndJunglesAreRoads = false

    // Same for Inca unique
    @Transient
    var ignoreHillMovementCost = false

    fun setTransients() {
        fun safeColorFromRGB(rgb: List<Int>) = ImmutableColor(if (rgb.size >= 3) colorFromRGB(rgb) else Color.PURPLE)

        outerColorObject = safeColorFromRGB(outerColor)

        innerColorObject = if (innerColor == null) ImmutableColor(ImageGetter.CHARCOAL)
                           else safeColorFromRGB(innerColor!!)

        forestsAndJunglesAreRoads = uniqueMap.hasUnique(UniqueType.ForestsAndJunglesAreRoads)
        ignoreHillMovementCost = uniqueMap.hasUnique(UniqueType.IgnoreHillMovementCost)
    }


    override fun makeLink() = "Nation/$name"
    override fun getSortGroup(ruleset: Ruleset) = when {
        isCityState -> 1
        isBarbarian -> 9
        else -> 0
    }
    override fun getSubCategory(ruleset: Ruleset): String? = when {
        isCityState -> "City-States"
        isBarbarian -> "Other"
        else -> "Civilizations"
    }

    override fun getCivilopediaTextLines(ruleset: Ruleset) = getCivilopediaTextLinesImpl(ruleset)

    @Readonly
    fun matchesFilter(filter: String, state: GameContext? = null, multiFilter: Boolean = true): Boolean {
        // Todo: Add 'multifilter=false' option to Multifilter itself to cut down on duplicate code
        return if (multiFilter) MultiFilter.multiFilter(filter, {
            matchesSingleFilter(filter) ||
                state != null && hasTagUnique(it, state) ||
                state == null && hasTagUnique(it)
        })
        else matchesSingleFilter(filter) ||
            state != null && hasTagUnique(filter, state) ||
            state == null && hasTagUnique(filter)
    }

    @Readonly
    private fun matchesSingleFilter(filter: String): Boolean {
        // All cases are compile-time constants, for performance
        return when (filter) {
            "All", "all" -> true
            "Major" -> isMajorCiv
            Constants.cityStates, "City-State" -> isCityState
            else -> filter == name
        }
    }
}
