package com.unciv.logic.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.city.CityInfo;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.models.linq.Linq;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Terrain;
import com.unciv.models.gamebasics.TileImprovement;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.FullStats;

import java.text.DecimalFormat;

public class TileInfo
{
    public transient TileMap tileMap;

    public MapUnit unit;
    public Vector2 position;
    public String baseTerrain;
    public String terrainFeature;
    public String resource;
    public String improvement;
    public String improvementInProgress;
    public String owner; // owning civ name
    public String workingCity; // Working City name
    public RoadStatus roadStatus = RoadStatus.None;
    public boolean explored=false;
    public int turnsToImprovement;

    public Terrain getBaseTerrain(){return GameBasics.Terrains.get(baseTerrain);}

    public CivilizationInfo getOwner(){
        if(owner==null) return null;
        return tileMap.gameInfo.civilizations.first(new Predicate<CivilizationInfo>() {
            @Override
            public boolean evaluate(CivilizationInfo arg0) {
                return arg0.civName.equals(owner);
            }
        });
    }

    public CityInfo getCity(){
        if(workingCity == null) return null;
        return getOwner().cities.first(new Predicate<CityInfo>() {
        @Override
        public boolean evaluate(CityInfo arg0) {
            return arg0.name.equals(workingCity);
        }
    });}

    public Terrain getTerrainFeature(){return terrainFeature ==null ? null : GameBasics.Terrains.get(terrainFeature);}

    public Terrain getLastTerrain() {
        return terrainFeature == null ? getBaseTerrain() : getTerrainFeature();
    }

    public TileResource getTileResource(){return resource ==null ? null : GameBasics.TileResources.get(resource);}

    public boolean isCityCenter(){return getCity()!=null && position.equals(getCity().cityLocation);}

    public TileImprovement getTileImprovement(){return improvement ==null ? null : GameBasics.TileImprovements.get(improvement);}


    public FullStats getTileStats(CivilizationInfo observingCiv){return getTileStats(getCity(),observingCiv);}

    public FullStats getTileStats(CityInfo city, CivilizationInfo observingCiv)
    {
        FullStats stats = new FullStats(getBaseTerrain());

        if(terrainFeature !=null){
            Terrain terrainFeature = getTerrainFeature();
            if(terrainFeature.overrideStats) stats = new FullStats(terrainFeature);
            else stats.add(terrainFeature);
        }

        TileResource resource = getTileResource();
        if (hasViewableResource(observingCiv))
        {
            stats.add(resource); // resource base
            if(resource.building !=null && city!=null && city.cityConstructions.isBuilt(resource.building))
            {
                stats.add(resource.GetBuilding().resourceBonusStats); // resource-specific building (eg forge, stable) bonus
            }
        }

        TileImprovement improvement = getTileImprovement();
        if (improvement != null)
        {
            if (resource != null && resource.improvement.equals(improvement.name))
                stats.add(resource.improvementStats); // resource-specifc improvement
            else stats.add(improvement); // basic improvement

            if (observingCiv.tech.isResearched(improvement.improvingTech)) stats.add(improvement.improvingTechStats); // eg Chemistry for mines
            if(improvement.name.equals("Trading post") && city.civInfo.policies.isAdopted("Free Thought"))
                stats.science+=1;
            if(new Linq<String>("Academy","Landmark","Manufactory","Customs House").contains(improvement.name)
                    && observingCiv.policies.isAdopted("Freedom Complete"))
                stats.add(improvement); // again, for the double effect
        }

        if (isCityCenter()) {
            if (stats.food < 2) stats.food = 2;
            if (stats.production < 1) stats.production = 1;
        }

        if (stats.production < 0) stats.production = 0;

        if("Jungle".equals(terrainFeature) && city!=null
                && city.getBuildingUniques().contains("JunglesProvideScience")) stats.science+=2;
        if(stats.gold!=0 && observingCiv.goldenAges.isGoldenAge())
            stats.gold++;

        return stats;
    }

    public boolean canBuildImprovement(TileImprovement improvement, CivilizationInfo civInfo)
    {
        if(isCityCenter() || improvement.name.equals(this.improvement)) return false;
        Terrain topTerrain = terrainFeature ==null ? getBaseTerrain() : getTerrainFeature();
        if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired)) return false;
        if (improvement.terrainsCanBeBuiltOn.contains(topTerrain.name)) return true;
        if(improvement.name.equals("Road") && this.roadStatus== RoadStatus.None) return true;
        if(improvement.name.equals("Railroad") && this.roadStatus != RoadStatus.Railroad) return true;
        if (topTerrain.unbuildable) return false;

        return hasViewableResource(civInfo) && getTileResource().improvement.equals(improvement.name);
    }

    public void startWorkingOnImprovement(TileImprovement improvement, CivilizationInfo civInfo)
    {
        improvementInProgress = improvement.name;
        turnsToImprovement = improvement.getTurnsToBuild(civInfo);
    }

    public void stopWorkingOnImprovement()
    {
        improvementInProgress = null;
    }

    public void nextTurn()
    {
        if(unit !=null) {
            unit.doPostTurnAction(this);
            unit.currentMovement = unit.maxMovement;
            unit.doPreTurnAction(this);
        }
    }

    public String toString() {
        StringBuilder SB = new StringBuilder();
        if (isCityCenter()){SB.append(workingCity+",\r\n"+getCity().cityConstructions.getProductionForTileInfo());}
        SB.append(this.baseTerrain);
        if (terrainFeature != null) SB.append(",\r\n" + terrainFeature);
        if (hasViewableResource(tileMap.gameInfo.getPlayerCivilization())) SB.append(",\r\n" + resource);
        if (roadStatus!= RoadStatus.None && !isCityCenter()) SB.append(",\r\n" + roadStatus);
        if (improvement != null) SB.append(",\r\n" + improvement);
        if (improvementInProgress != null) SB.append(",\r\n" + improvementInProgress +" in "+this.turnsToImprovement +" turns");
        if (unit !=null) SB.append(",\r\n" + unit.name + "("+ new DecimalFormat("0.#").format(unit.currentMovement)+"/"+ unit.maxMovement+")");
        return SB.toString();
    }

    public boolean hasViewableResource(CivilizationInfo civInfo) {
        return resource != null && (getTileResource().revealedBy ==null || civInfo.tech.isResearched(getTileResource().revealedBy));
    }

    public boolean hasIdleUnit() {
        if (unit == null) return false;
        if (unit.currentMovement == 0) return false;
        return !(unit.name.equals("Worker") && improvementInProgress != null);
    }

    public void moveUnitToTile(TileInfo otherTile, float movementDistance){
        if(otherTile.unit!=null) return; // Fail.
        unit.currentMovement -= movementDistance;
        if(unit.currentMovement < 0.1) unit.currentMovement =0; // silly floats which are "almost zero"
        otherTile.unit = unit;
        unit = null;
    }

    public Linq<TileInfo> getNeighbors(){
        return tileMap.getTilesAtDistance(position,1);
    }

    public int getHeight(){
        int height=0;
        if(new Linq<String>("Forest","Jungle").contains(terrainFeature)) height+=1;
        if("Hill".equals(baseTerrain)) height+=2;
        return height;
    }
}