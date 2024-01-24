package com.unciv.models.ruleset.construction

import com.unciv.logic.city.CityConstructions
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed

interface IConstruction : INamed {
    fun isBuildable(cityConstructions: CityConstructions): Boolean
    fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean
    /** Gets *per turn* resource requirements - does not include immediate costs for stockpiled resources.
     * Uses [stateForConditionals] to determine which civ or city this is built for*/
    fun getResourceRequirementsPerTurn(stateForConditionals: StateForConditionals? = null): Counter<String>
    fun requiresResource(resource: String, stateForConditionals: StateForConditionals? = null): Boolean
    /** We can't call this getMatchingUniques because then it would conflict with IHasUniques */
    fun getMatchingUniquesNotConflicting(uniqueType: UniqueType) = sequenceOf<Unique>()
}
