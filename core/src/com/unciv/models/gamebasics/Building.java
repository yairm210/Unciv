package com.unciv.models.gamebasics;

import com.badlogic.gdx.utils.Predicate;
import com.unciv.civinfo.CityInfo;
import com.unciv.civinfo.CivilizationInfo;
import com.unciv.civinfo.CityConstructions;
import com.unciv.civinfo.IConstruction;
import com.unciv.civinfo.TileInfo;
import com.unciv.game.UnCivGame;
import com.unciv.game.VictoryScreen;
import com.unciv.models.LinqCollection;
import com.unciv.models.stats.FullStats;
import com.unciv.models.stats.NamedStats;

public class Building extends NamedStats implements IConstruction, ICivilopedia {
    public String description;
    public String requiredTech;

    public Technology GetRequiredTech() {
        return GameBasics.Technologies.get(requiredTech);
    }

    public int cost;
    public int maintainance = 0;
    public FullStats percentStatBonus;
    public FullStats specialistSlots;
    public FullStats greatPersonPoints;
    public int hurryCostModifier; // Extra cost percentage when purchasing
    public boolean isWonder = false;
    public String requiredBuilding;
    public String requiredBuildingInAllCities;
    public String requiredResource; // A strategic resource that will be consumed by this building
    public LinqCollection requiredNearbyImprovedResources; // City can only be built if one of these resources is nearby - it must be improved!
    public String cannotBeBuiltWith;

    // Uniques
    public String providesFreeBuilding;
    public int freeTechs;
    public String unique; // for wonders which have individual functions that are totally unique


    /**
     * The bonus stats that a resource gets when this building is built
     */
    public FullStats resourceBonusStats;

    public String getDescription() {
        return getDescription(false);
    }

    public String getDescription(boolean forBuildingPickerScreen) {
        FullStats stats = new FullStats(this);
        StringBuilder stringBuilder = new StringBuilder();
        if (!forBuildingPickerScreen) stringBuilder.append("Cost: " + cost + "\r\n");
        if (isWonder) stringBuilder.append("Wonder\r\n");
        if (!forBuildingPickerScreen && requiredTech != null)
            stringBuilder.append("Requires " + requiredTech + " to be researched\r\n");
        if (!forBuildingPickerScreen && requiredBuilding != null)
            stringBuilder.append("Requires a " + requiredBuilding + " to be built in this city\r\n");
        if (!forBuildingPickerScreen && requiredBuildingInAllCities != null)
            stringBuilder.append("Requires a " + requiredBuildingInAllCities + " to be built in all cities\r\n");
        if (providesFreeBuilding != null)
            stringBuilder.append("Provides a free " + providesFreeBuilding + " in this city\r\n");
        if (maintainance != 0)
            stringBuilder.append("Maintainance cost: " + maintainance + " gold\r\n");
        stringBuilder.append(description + "\r\n" + stats);
        if(this.percentStatBonus!=null){
            if(this.percentStatBonus.production!=0) stringBuilder.append("\r\n+"+this.percentStatBonus.production+" production");
            if(this.percentStatBonus.gold!=0) stringBuilder.append("\r\n+"+this.percentStatBonus.gold+" gold");
            if(this.percentStatBonus.science!=0) stringBuilder.append("\r\n+"+this.percentStatBonus.science+" science");
            if(this.percentStatBonus.food!=0) stringBuilder.append("\r\n+"+this.percentStatBonus.food+" food");
            if(this.percentStatBonus.culture!=0) stringBuilder.append("\r\n+"+this.percentStatBonus.culture+" culture");
        }
        return stringBuilder.toString();
    }

    @Override
    public int getProductionCost() {
        return cost;
    }

    public int getGoldCost(){
        return (int)( Math.pow(30 * cost,0.75) * (1 + hurryCostModifier/100) / 10 ) * 10;
    }
    
    public boolean isBuildable(CityConstructions construction){
        CivilizationInfo civInfo = UnCivGame.Current.civInfo;
        if(construction.isBuilt(name)) return false;
        if(requiredNearbyImprovedResources!=null) {
            boolean containsResourceWithImprovement = construction.getCity().getTilesInRange()
                    .any(new Predicate<TileInfo>() {
                        @Override
                        public boolean evaluate(TileInfo tile) {
                            return tile.resource != null
                                    && requiredNearbyImprovedResources.contains(tile.resource)
                                    && tile.getTileResource().improvement.equals(tile.improvement);
                        }
                    });
            if(!containsResourceWithImprovement) return false;
        }

        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech)) return false;
        if (isWonder && civInfo.cities
                .any(new Predicate<CityInfo>() {
                    @Override
                    public boolean evaluate(CityInfo arg0) {
                        CityConstructions CB = arg0.cityConstructions;
                        return CB.isBuilding(name) || CB.isBuilt(name);
                    }
                }) ) return false;
        if (requiredBuilding != null && !construction.isBuilt(requiredBuilding)) return false;
        if (requiredBuildingInAllCities != null ||
                civInfo.cities.any(new Predicate<CityInfo>() {
                    @Override
                    public boolean evaluate(CityInfo arg0) {
                        return arg0.cityConstructions.isBuilt(requiredBuildingInAllCities);
                    }
                }) ) return false;
        if(cannotBeBuiltWith != null && construction.isBuilt(cannotBeBuiltWith)) return false;
        if("MustBeNextToDesert".equals(unique) &&
                !civInfo.tileMap.getTilesInDistance(construction.cityLocation,1).any(new Predicate<TileInfo>() {
                    @Override
                    public boolean evaluate(TileInfo arg0) {
                        return arg0.baseTerrain.equals("Desert");
                    }
                }))
            return false;
        if(requiredResource!=null &&
                !civInfo.getCivResources().keySet().contains(GameBasics.TileResources.get(requiredResource)))
            return false; // Only checks if exists, doesn't check amount - todo

        if("SpaceshipPart".equals(unique)){
            if(!civInfo.getBuildingUniques().contains("ApolloProgram")) return false;
            if(civInfo.scienceVictory.requiredParts.get(name)==0) return false; // Don't need to build any more of these!
        }
        return true;
    }

    @Override
    public void postBuildEvent(CityConstructions constructions) {
        if("SpaceshipPart".equals(unique)) {
            CivilizationInfo.current().scienceVictory.currentParts.add(name, 1);
            if(CivilizationInfo.current().scienceVictory.unconstructedParts().isEmpty())
                UnCivGame.Current.setScreen(new VictoryScreen(UnCivGame.Current));
            return;
        }
        constructions.builtBuildings.add(name);
        if (providesFreeBuilding != null && !constructions.builtBuildings.contains(providesFreeBuilding))
            constructions.builtBuildings.add(providesFreeBuilding);
        if (freeTechs != 0) UnCivGame.Current.civInfo.tech.freeTechs += freeTechs;
        if("EmpireEntersGoldenAge".equals(unique)) CivilizationInfo.current().enterGoldenAge();
    }
}


