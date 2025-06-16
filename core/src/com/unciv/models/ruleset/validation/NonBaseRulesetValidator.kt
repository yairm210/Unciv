package com.unciv.models.ruleset.validation

import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.images.PortraitPromotion

/**
 *  This implementation of [RulesetValidator] **cannot** rely on [ruleset] being complete.
 *
 *  No base ruleset is loaded, therefore references cannot be checked,
 *  and we can only really detect ruleset-invariant errors in uniques.
 */
internal class NonBaseRulesetValidator(
    ruleset: Ruleset,
    tryFixUnknownUniques: Boolean
) : RulesetValidator(ruleset, tryFixUnknownUniques) {

    override fun getErrorListInternal(): RulesetErrorList {
        val lines = RulesetErrorList(ruleset)

        addModOptionsErrors(lines)
        addGlobalUniqueErrors(lines, false)

        addUnitErrorsRulesetInvariant(lines)
        addTechErrorsRulesetInvariant(lines)
        addTechColumnErrorsRulesetInvariant(lines)
        addBuildingErrorsRulesetInvariant(lines)
        addNationErrorsRulesetInvariant(lines)
        addPromotionErrorsRulesetInvariant(lines)
        addResourceErrorsRulesetInvariant(lines)

        initTextureNamesCache(lines)

        // Tileset tests - e.g. json configs complete and parseable
        checkTilesetSanity(lines)  // relies on textureNamesCache
        checkCivilopediaText(lines)  // relies on textureNamesCache
        checkFileNames(lines)

        return lines
    }

    private fun addBuildingErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (building in ruleset.buildings.values) {
            addBuildingErrorRulesetInvariant(building, lines)
            uniqueValidator.checkUniques(building, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addNationErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (nation in ruleset.nations.values) {
            addNationErrorRulesetInvariant(nation, lines)
            uniqueValidator.checkUniques(nation, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addPromotionErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (promotion in ruleset.unitPromotions.values) {
            uniqueValidator.checkUniques(promotion, lines, false, tryFixUnknownUniques)
            checkContrasts(promotion.innerColorObject ?: PortraitPromotion.defaultInnerColor,
                promotion.outerColorObject ?: PortraitPromotion.defaultOuterColor, promotion, lines)
            addPromotionErrorRulesetInvariant(promotion, lines)
        }
    }

    private fun addResourceErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (resource in ruleset.tileResources.values) {
            uniqueValidator.checkUniques(resource, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addTechErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (tech in ruleset.technologies.values) {
            if (tech.row < 1) lines.add("Tech ${tech.name} has a row value below 1: ${tech.row}", sourceObject = tech)
            uniqueValidator.checkUniques(tech, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addUnitErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (unit in ruleset.units.values) {
            checkUnitRulesetInvariant(unit, lines)
            uniqueValidator.checkUniques(unit, lines, false, tryFixUnknownUniques)
        }
    }
}
