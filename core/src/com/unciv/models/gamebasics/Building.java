package com.unciv.models.gamebasics;

import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.city.CityInfo;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.city.CityConstructions;
import com.unciv.logic.city.IConstruction;
import com.unciv.logic.map.TileInfo;
import com.unciv.ui.ScienceVictoryScreen;
import com.unciv.ui.UnCivGame;
import com.unciv.ui.VictoryScreen;
import com.unciv.ui.pickerscreens.PolicyPickerScreen;
import com.unciv.models.linq.Linq;
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
    public Linq requiredNearbyImprovedResources; // City can only be built if one of these resources is nearby - it must be improved!
    public String cannotBeBuiltWith;

    // Uniques
    public String providesFreeBuilding;
    public int freeTechs;
    public String unique; // for wonders which have individual functions that are totally unique


    /**
     * The bonus stats that a resource gets when this building is built
     */
    public FullStats resourceBonusStats;

    public FullStats getStats(Linq<String> adoptedPolicies){
        FullStats stats = this.clone();
        if (adoptedPolicies.contains("Organized Religion") &&
                new Linq<String>("Monument","Temple","Monastery").contains(name))
            stats.happiness+=1;

        if (adoptedPolicies.contains("Free Religion") &&
                new Linq<String>("Monument","Temple","Monastery").contains(name))
            stats.culture+=1;

        if (adoptedPolicies.contains("Entrepreneurship") &&
                new Linq<String>("Mint","Market","Bank","Stock Market").contains(name))
            stats.science+=1;

        if (adoptedPolicies.contains("Humanism") &&
                new Linq<String>("University","Observatory","Public School").contains(name))
            stats.science+=1;

        if (adoptedPolicies.contains("Theocracy") && name.equals("Temple"))
            percentStatBonus = new FullStats(){{gold=10;}};

        if (adoptedPolicies.contains("Free Thought") && name.equals("University"))
            percentStatBonus.science=50;

        if (adoptedPolicies.contains("Rationalism Complete") && !isWonder && stats.science>0)
            stats.gold+=1;

        if (adoptedPolicies.contains("Constitution") && isWonder)
            stats.culture+=2;

        return stats;
    }

    public String getDescription() {
        return getDescription(false,new Linq<String>());
    }

    public String getDescription(boolean forBuildingPickerScreen, Linq<String> adoptedPolicies) {
        FullStats stats = getStats(adoptedPolicies);
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
        if(description!=null) stringBuilder.append(description + "\r\n");
        if(!stats.toString().equals(""))
        stringBuilder.append(stats+"\r\n");
        if(this.percentStatBonus!=null){
            if(this.percentStatBonus.production!=0) stringBuilder.append("+"+(int)this.percentStatBonus.production+"% production\r\n");
            if(this.percentStatBonus.gold!=0) stringBuilder.append("+"+(int)this.percentStatBonus.gold+"% gold\r\n");
            if(this.percentStatBonus.science!=0) stringBuilder.append("+"+(int)this.percentStatBonus.science+"% science\r\n");
            if(this.percentStatBonus.food!=0) stringBuilder.append("+"+(int)this.percentStatBonus.food+"% food\r\n");
            if(this.percentStatBonus.culture!=0) stringBuilder.append("+"+(int)this.percentStatBonus.culture+"% culture\r\n");
        }
        if(this.greatPersonPoints!=null){
            if(this.greatPersonPoints.production!=0) stringBuilder.append("+"+(int)this.greatPersonPoints.production+" Great Engineer points\r\n");
            if(this.greatPersonPoints.gold!=0) stringBuilder.append("+"+(int)this.greatPersonPoints.gold+" Great Merchant points\r\n");
            if(this.greatPersonPoints.science!=0) stringBuilder.append("+"+(int)this.greatPersonPoints.science+" Great Scientist points\r\n");
            if(this.greatPersonPoints.culture!=0) stringBuilder.append("+"+(int)this.greatPersonPoints.culture+" Great Artist points\r\n");
        }
        if(resourceBonusStats!=null){
            String resources = StringUtils.join(",",GameBasics.TileResources.linqValues().where(new Predicate<TileResource>() {
                @Override
                public boolean evaluate(TileResource arg0) {
                    return name.equals(arg0.building);
                }
            }).select(new Linq.Func<TileResource, String>() {
                @Override
                public String GetBy(TileResource arg0) {
                    return arg0.name;
                }
            })) ;
            stringBuilder.append(resources+" provide "+resourceBonusStats+" \r\n");
        }
        if (maintainance != 0)
            stringBuilder.append("Maintainance cost: " + maintainance + " gold\r\n");
        return stringBuilder.toString();
    }

    @Override
    public int getProductionCost(Linq<String> adoptedPolicies) {
        if(!isWonder && culture!=0 && adoptedPolicies.contains("Piety"))
            return (int) (cost*0.85);
        return cost;
    }

    public int getGoldCost(Linq<String> adoptedPolicies) {
        double cost = Math.pow(30 * getProductionCost(adoptedPolicies),0.75) * (1 + hurryCostModifier/100);
        if(adoptedPolicies.contains("Mercantilism")) cost*=0.75;
        if(adoptedPolicies.contains("Patronage")) cost*=0.5;
        return (int)( cost / 10 ) * 10;
    }
    
    public boolean isBuildable(CityConstructions construction){
        CivilizationInfo civInfo = construction.cityInfo.civInfo;
        if(construction.isBuilt(name)) return false;
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
                !civInfo.gameInfo.tileMap.getTilesInDistance(construction.cityInfo.cityLocation,1).any(new Predicate<TileInfo>() {
                    @Override
                    public boolean evaluate(TileInfo arg0) {
                        return arg0.baseTerrain.equals("Desert");
                    }
                }))
            return false;
        if(requiredResource!=null &&
                !civInfo.getCivResources().keySet().contains(GameBasics.TileResources.get(requiredResource)))
            return false; // Only checks if exists, doesn't check amount - todo


        if(requiredNearbyImprovedResources!=null) {
            boolean containsResourceWithImprovement = construction.cityInfo.getTilesInRange()
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

        if("SpaceshipPart".equals(unique)){
            if(!civInfo.getBuildingUniques().contains("ApolloProgram")) return false;
            if(civInfo.scienceVictory.unconstructedParts().get(name) ==0) return false; // Don't need to build any more of these!
        }
        return true;
    }

    @Override
    public void postBuildEvent(CityConstructions constructions) {
        CivilizationInfo civInfo = constructions.cityInfo.civInfo;
        if("ApolloProgram".equals(unique)) UnCivGame.Current.setScreen(new ScienceVictoryScreen(civInfo));
        if("SpaceshipPart".equals(unique)) {
            civInfo.scienceVictory.currentParts.add(name, 1);
            UnCivGame.Current.setScreen(new ScienceVictoryScreen(civInfo));
            if(civInfo.scienceVictory.unconstructedParts().isEmpty())
                UnCivGame.Current.setScreen(new VictoryScreen());
            return;
        }
        constructions.builtBuildings.add(name);
        if (providesFreeBuilding != null && !constructions.builtBuildings.contains(providesFreeBuilding))
            constructions.builtBuildings.add(providesFreeBuilding);
        if (freeTechs != 0) civInfo.tech.freeTechs += freeTechs;
        if("EmpireEntersGoldenAge".equals(unique)) civInfo.goldenAges.enterGoldenAge();
        if("FreeGreatArtistAppears".equals(unique)) civInfo.addGreatPerson("Great Artist");
        if("WorkerConstruction".equals(unique)){
            civInfo.placeUnitNearTile(constructions.cityInfo.cityLocation,"Worker");
            civInfo.placeUnitNearTile(constructions.cityInfo.cityLocation,"Worker");
        }
        if("FreeSocialPolicy".equals(unique)){
            civInfo.policies.freePolicies++;
            UnCivGame.Current.setScreen(new PolicyPickerScreen(civInfo));
        }
    }
}


