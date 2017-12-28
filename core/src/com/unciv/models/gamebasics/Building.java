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

import java.util.ArrayList;

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

    public FullStats getStats(){
        FullStats stats = new FullStats(this);
        LinqCollection<String> policies = CivilizationInfo.current().policies;
        if (policies.contains("Organized Religion") &&
                new LinqCollection<String>("Monument","Temple","Monastery").contains(name))
            stats.happiness+=1;

        if (policies.contains("Free Religion") &&
                new LinqCollection<String>("Monument","Temple","Monastery").contains(name))
            stats.culture+=1;

        if (policies.contains("Entrepreneurship") &&
                new LinqCollection<String>("Mint","Market","Bank","Stock Market").contains(name))
            stats.science+=1;

        if (policies.contains("Humanism") &&
                new LinqCollection<String>("University","Observatory","Public School").contains(name))
            stats.science+=1;

        if (policies.contains("Theocracy") && name.equals("Temple"))
            percentStatBonus = new FullStats(){{gold=10;}};

        if (policies.contains("Free Thought") && name.equals("University"))
            percentStatBonus.science=50;

        if (policies.contains("Rationalism Complete") && !isWonder && stats.science>0)
            stats.gold+=1;

        if (policies.contains("Constitution") && isWonder)
            stats.culture+=2;

        return stats;
    }

    public String getDescription() {
        return getDescription(false);
    }

    public String getDescription(boolean forBuildingPickerScreen) {
        FullStats stats = getStats();
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
            }).select(new LinqCollection.Func<TileResource, String>() {
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
    public int getProductionCost() {
        if(!isWonder && culture!=0 && CivilizationInfo.current().policies.contains("Piety"))
            return (int) (cost*0.85);
        return cost;
    }

    public int getGoldCost(){
        double cost = Math.pow(30 * getProductionCost(),0.75) * (1 + hurryCostModifier/100);
        if(CivilizationInfo.current().policies.contains("Mercantilism")) cost*=0.75;
        if(CivilizationInfo.current().policies.contains("Patronage")) cost*=0.5;
        return (int)( cost / 10 ) * 10;
    }
    
    public boolean isBuildable(CityConstructions construction){
        CivilizationInfo civInfo = UnCivGame.Current.civInfo;
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
                UnCivGame.Current.setScreen(new VictoryScreen());
            return;
        }
        constructions.builtBuildings.add(name);
        if (providesFreeBuilding != null && !constructions.builtBuildings.contains(providesFreeBuilding))
            constructions.builtBuildings.add(providesFreeBuilding);
        if (freeTechs != 0) UnCivGame.Current.civInfo.tech.freeTechs += freeTechs;
        if("EmpireEntersGoldenAge".equals(unique)) CivilizationInfo.current().enterGoldenAge();
        if("FreeGreatArtistAppears".equals(unique)) CivilizationInfo.current().addGreatPerson("Great Artist");
        if("WorkerConstruction".equals(unique)){
            CivilizationInfo.current().tileMap.placeUnitNearTile(constructions.cityLocation,"Worker");
            CivilizationInfo.current().tileMap.placeUnitNearTile(constructions.cityLocation,"Worker");
        }
    }
}


