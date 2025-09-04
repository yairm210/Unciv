package com.unciv.logic.battle

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.CivilopediaAction
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.screens.worldscreen.bottombar.BattleTable
import yairm210.purity.annotations.Readonly
import kotlin.math.ulp
import kotlin.random.Random

object Nuke {


    /**
     *  Checks whether [nuke] is allowed to nuke [targetTile]
     *  - Not if we would need to declare war on someone we can't.
     *  - Disallow nuking the tile the nuke is in, as per Civ5 (but not nuking your own tiles/units otherwise)
     *
     *  Both [BattleTable.simulateNuke] and [AirUnitAutomation.automateNukes] check range, so that check is omitted here.
     */
    @Readonly
    fun mayUseNuke(nuke: MapUnitCombatant, targetTile: Tile): Boolean {
        val attackerCiv = nuke.getCivInfo()
        val launchTile = nuke.getTile()
        
        if (launchTile == targetTile) return false
        if (!targetTile.isExplored(attackerCiv)) return false
        // Can only nuke in unit's range, visibility (line of sight) doesn't matter
        if (launchTile.aerialDistanceTo(targetTile) > nuke.unit.getRange()) return false

        var canNuke = true
        
        @Readonly
        fun checkDefenderCiv(defenderCiv: Civilization?) {
            if (defenderCiv == null) return
            // Allow nuking yourself! (Civ5 source: CvUnit::isNukeVictim)
            if (defenderCiv == attackerCiv || defenderCiv.isDefeated()) return
            if (defenderCiv.isBarbarian) return
            // Gleaned from Civ5 source - this disallows nuking unknown civs even in invisible tiles
            // https://github.com/Gedemon/Civ5-DLL/blob/master/CvGameCoreDLL_Expansion1/CvUnit.cpp#L5056
            // https://github.com/Gedemon/Civ5-DLL/blob/master/CvGameCoreDLL_Expansion1/CvTeam.cpp#L986
            if (attackerCiv.getDiplomacyManager(defenderCiv)?.canAttack() == true) return
            canNuke = false
        }

        val blastRadius = nuke.unit.getNukeBlastRadius()
        for (tile in targetTile.getTilesInDistance(blastRadius)) {
            checkDefenderCiv(tile.getOwner())
            checkDefenderCiv(Battle.getMapCombatantOfTile(tile)?.getCivInfo())
        }
        return canNuke
    }

    @Suppress("FunctionName")   // Yes we want this name to stand out
    fun NUKE(attacker: MapUnitCombatant, targetTile: Tile) {
        val attackingCiv = attacker.getCivInfo()
        val nukeStrength = attacker.unit.getMatchingUniques(UniqueType.NuclearWeapon)
            .firstOrNull()?.params?.get(0)?.toInt() ?: return

        val blastRadius = attacker.unit.getMatchingUniques(UniqueType.BlastRadius)
            .firstOrNull()?.params?.get(0)?.toInt() ?: 2

        val hitTiles = targetTile.getTilesInDistance(blastRadius)

        val (hitCivsTerritory, notifyDeclaredWarCivs) =
            declareWarOnHitCivs(attackingCiv, hitTiles, attacker, targetTile)

        addNukeNotifications(targetTile, attacker, notifyDeclaredWarCivs, attackingCiv, hitCivsTerritory)

        if (attacker.isDefeated()) return

        attacker.unit.attacksSinceTurnStart.add(Vector2(targetTile.position))

        for (tile in hitTiles) {
            // Handle complicated effects
            doNukeExplosionForTile(attacker, tile, nukeStrength, targetTile == tile)
        }


        // Instead of postBattleAction() just destroy the unit, all other functions are not relevant
        if (attacker.unit.hasUnique(UniqueType.SelfDestructs)) attacker.unit.destroy()

        // It's unclear whether using nukes results in a penalty with all civs, or only affected civs.
        // For now I'll make it give a diplomatic penalty to all known civs, but some testing for this would be appreciated
        for (civ in attackingCiv.getKnownCivs()) {
            civ.getDiplomacyManager(attackingCiv)!!.setModifier(DiplomaticModifiers.UsedNuclearWeapons, -50f)
        }

        if (!attacker.isDefeated()) {
            attacker.unit.attacksThisTurn += 1
        }
    }

    private fun addNukeNotifications(
        targetTile: Tile,
        attacker: MapUnitCombatant,
        notifyDeclaredWarCivs: ArrayList<Civilization>,
        attackingCiv: Civilization,
        hitCivsTerritory: ArrayList<Civilization>
    ) {
        val nukeNotificationAction = sequenceOf(LocationAction(targetTile.position), CivilopediaAction("Units/" + attacker.getName()))

        // If the nuke has been intercepted and destroyed then it fails to detonate
        if (attacker.isDefeated()) {
            // Notify attacker that they are now at war for the attempt
            for (defendingCiv in notifyDeclaredWarCivs)
                attackingCiv.addNotification(
                    "After an attempted attack by our [${attacker.getName()}], [${defendingCiv}] has declared war on us!",
                    nukeNotificationAction,
                    Notification.NotificationCategory.Diplomacy,
                    defendingCiv.civName,
                    NotificationIcon.War,
                    attacker.getName()
                )
            return
        }

        // Notify attacker that they are now at war
        for (defendingCiv in notifyDeclaredWarCivs)
            attackingCiv.addNotification(
                "After being hit by our [${attacker.getName()}], [${defendingCiv}] has declared war on us!",
                nukeNotificationAction, NotificationCategory.Diplomacy, defendingCiv.civName, NotificationIcon.War, attacker.getName()
            )

        // Message all other civs
        for (otherCiv in attackingCiv.gameInfo.civilizations) {
            if (!otherCiv.isAlive() || otherCiv == attackingCiv) continue
            if (hitCivsTerritory.contains(otherCiv))
                otherCiv.addNotification(
                    "A(n) [${attacker.getName()}] from [${attackingCiv.civName}] has exploded in our territory!",
                    nukeNotificationAction, NotificationCategory.War, attackingCiv.civName, NotificationIcon.War, attacker.getName()
                )
            else if (otherCiv.knows(attackingCiv))
                otherCiv.addNotification(
                    "A(n) [${attacker.getName()}] has been detonated by [${attackingCiv.civName}]!",
                    nukeNotificationAction, NotificationCategory.War, attackingCiv.civName, NotificationIcon.War, attacker.getName()
                )
            else
                otherCiv.addNotification(
                    "A(n) [${attacker.getName()}] has been detonated by [an unknown civilization]!",
                    nukeNotificationAction, NotificationCategory.War, NotificationIcon.War, attacker.getName()
                )
        }
    }

    private fun declareWarOnHitCivs(
        attackingCiv: Civilization,
        hitTiles: Sequence<Tile>,
        attacker: MapUnitCombatant,
        targetTile: Tile
    ): Pair<ArrayList<Civilization>, ArrayList<Civilization>> {
        // Declare war on the owners of all hit tiles
        val notifyDeclaredWarCivs = ArrayList<Civilization>()
        fun tryDeclareWar(civSuffered: Civilization) {
            if (civSuffered != attackingCiv
                && civSuffered.knows(attackingCiv)
                && civSuffered.getDiplomacyManager(attackingCiv)!!.diplomaticStatus != DiplomaticStatus.War
            ) {
                attackingCiv.getDiplomacyManager(civSuffered)!!.declareWar()
                if (!notifyDeclaredWarCivs.contains(civSuffered)) notifyDeclaredWarCivs.add(
                    civSuffered
                )
            }
        }

        val hitCivsTerritory = ArrayList<Civilization>()
        for (hitCiv in hitTiles.mapNotNull { it.getOwner() }.distinct()) {
            hitCivsTerritory.add(hitCiv)
            tryDeclareWar(hitCiv)
        }

        // Declare war on all potentially hit units. They'll try to intercept the nuke before it drops
        for (civWhoseUnitWasAttacked in hitTiles
            .flatMap { it.getUnits() }
            .map { it.civ }.distinct()
            .filter { it != attackingCiv }) {
            tryDeclareWar(civWhoseUnitWasAttacked)
            if (attacker.unit.baseUnit.isAirUnit() && !attacker.isDefeated()) {
                AirInterception.tryInterceptAirAttack(
                    attacker,
                    targetTile,
                    civWhoseUnitWasAttacked,
                    null
                )
            }
        }
        return Pair(hitCivsTerritory, notifyDeclaredWarCivs)
    }

    private fun doNukeExplosionForTile(
        attacker: MapUnitCombatant,
        tile: Tile,
        nukeStrength: Int,
        isGroundZero: Boolean
    ) {
        // https://forums.civfanatics.com/resources/unit-guide-modern-future-units-g-k.25628/
        // https://www.carlsguides.com/strategy/civilization5/units/aircraft-nukes.ph
        // Testing done by Ravignir
        // original source code: GenerateNuclearExplosionDamage(), ApplyNuclearExplosionDamage()

        var damageModifierFromMissingResource = 1f
        val civResources = attacker.getCivInfo().getCivResourcesByName()
        for (resource in attacker.unit.getResourceRequirementsPerTurn().keys) {
            if (civResources[resource]!! < 0 && !attacker.getCivInfo().isBarbarian)
                damageModifierFromMissingResource *= 0.5f // I could not find a source for this number, but this felt about right
            // - Original Civ5 does *not* reduce damage from missing resource, from source inspection
        }

        var buildingModifier = 1f  // Strange, but in Civ5 a bunker mitigates damage to garrison, even if the city is destroyed by the nuke

        // Damage city and reduce its population
        val city = tile.getCity()
        if (city != null && tile.position == city.location) {
            buildingModifier = city.getAggregateModifier(UniqueType.GarrisonDamageFromNukes)
            doNukeExplosionDamageToCity(city, nukeStrength, damageModifierFromMissingResource)
            Battle.postBattleNotifications(attacker, CityCombatant(city), city.getCenterTile())
            Battle.destroyIfDefeated(city.civ, attacker.getCivInfo(), city.location)
        }

        // Damage and/or destroy units on the tile
        for (unit in tile.getUnits().toList()) { // toList so if it's destroyed there's no concurrent modification
            val damage = (when {
                isGroundZero || nukeStrength >= 2 -> 100
                // The following constants are NUKE_UNIT_DAMAGE_BASE / NUKE_UNIT_DAMAGE_RAND_1 / NUKE_UNIT_DAMAGE_RAND_2 in Civ5
                nukeStrength == 1 -> 30 + Random.Default.nextInt(40) + Random.Default.nextInt(40)
                // Level 0 does not exist in Civ5 (it treats units same as level 2)
                else -> 20 + Random.Default.nextInt(30)
            } * buildingModifier * damageModifierFromMissingResource + 1f.ulp).toInt()
            val defender = MapUnitCombatant(unit)
            if (unit.isCivilian()) {
                if (unit.health - damage <= 40) unit.destroy()  // Civ5: NUKE_NON_COMBAT_DEATH_THRESHOLD = 60
            } else {
                defender.takeDamage(damage)
            }
            Battle.postBattleNotifications(attacker, defender, defender.getTile())
            Battle.destroyIfDefeated(defender.getCivInfo(), attacker.getCivInfo())
        }

        // Pillage improvements, pillage roads, add fallout
        if (tile.isCityCenter()) return  // Never touch city centers - if they survived

        if (tile.terrainHasUnique(UniqueType.DestroyableByNukesChance)) {
            // Note: Safe from concurrent modification exceptions only because removeTerrainFeature
            // *replaces* terrainFeatureObjects and the loop will continue on the old one
            for (terrainFeature in tile.terrainFeatureObjects) {
                for (unique in terrainFeature.getMatchingUniques(UniqueType.DestroyableByNukesChance)) {
                    val chance = unique.params[0].toFloat() / 100f
                    if (!(chance > 0f && isGroundZero) && Random.Default.nextFloat() >= chance) continue
                    tile.removeTerrainFeature(terrainFeature.name)
                    applyPillageAndFallout(tile)
                }
            }
        } else if (isGroundZero || Random.Default.nextFloat() < 0.5f) {  // Civ5: NUKE_FALLOUT_PROB
            applyPillageAndFallout(tile)
        }
    }

    private fun applyPillageAndFallout(tile: Tile) {
        if (tile.getUnpillagedImprovement() != null && !tile.getTileImprovement()!!.hasUnique(
                UniqueType.Irremovable)) {
            if (tile.getTileImprovement()!!.hasUnique(UniqueType.Unpillagable)) {
                tile.removeImprovement()
            } else {
                tile.setPillaged()
            }
        }
        if (tile.getUnpillagedRoad() != RoadStatus.None)
            tile.setPillaged()
        if (tile.isWater || tile.isImpassible() || tile.terrainFeatures.contains("Fallout")) return
        tile.addTerrainFeature("Fallout")
    }

    /** @return the "protection" modifier from buildings (Bomb Shelter, UniqueType.PopulationLossFromNukes) */
    private fun doNukeExplosionDamageToCity(targetedCity: City, nukeStrength: Int, damageModifierFromMissingResource: Float) {
        // Original Capitals must be protected, `canBeDestroyed` is responsible for that check.
        // The `justCaptured = true` parameter is what allows other Capitals to suffer normally.
        if ((nukeStrength > 2 || nukeStrength > 1 && targetedCity.population.population < 5)
            && targetedCity.canBeDestroyed(true)) {
            targetedCity.destroyCity()
            return
        }

        val cityCombatant = CityCombatant(targetedCity)
        cityCombatant.takeDamage((cityCombatant.getHealth() * 0.5f * damageModifierFromMissingResource).toInt())

        // Difference to original: Civ5 rounds population loss down twice - before and after bomb shelters
        val populationLoss = (
            targetedCity.population.population *
                targetedCity.getAggregateModifier(UniqueType.PopulationLossFromNukes) *
                when (nukeStrength) {
                    0 -> 0f
                    1 -> (30 + Random.Default.nextInt(20) + Random.Default.nextInt(20)) / 100f
                    2 -> (60 + Random.Default.nextInt(10) + Random.Default.nextInt(10)) / 100f
                    else -> 1f  // hypothetical nukeStrength 3 -> always to 1 pop
                }
            ).toInt()
        targetedCity.population.addPopulation(-populationLoss)
    }

    @Readonly
    private fun City.getAggregateModifier(uniqueType: UniqueType): Float {
        var modifier = 1f
        for (unique in getMatchingUniques(uniqueType)) {
            if (!matchesFilter(unique.params[1])) continue
            modifier *= unique.params[0].toPercent()
        }
        return modifier
    }
}
