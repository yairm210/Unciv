package com.unciv.logic.city;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.map.RoadStatus;
import com.unciv.logic.map.TileInfo;
import com.unciv.logic.map.TileMap;
import com.unciv.models.linq.Linq;
import com.unciv.models.linq.LinqCounter;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.FullStats;

public class CityInfo {
    public transient CivilizationInfo civInfo;
    public Vector2 cityLocation;
    public String name;

    public PopulationManager population = new PopulationManager();
    public CityConstructions cityConstructions = new CityConstructions();
    public CityExpansionManager expansion = new CityExpansionManager();
    public CityStats cityStats = new CityStats();

    TileMap getTileMap(){return civInfo.gameInfo.tileMap; }

    public TileInfo getTile(){return getTileMap().get(cityLocation);}
    public Linq<TileInfo> getTilesInRange(){
        return getTileMap().getTilesInDistance(cityLocation,3).where(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return civInfo.civName.equals(arg0.owner);
            }
        });
    }

    private String[] CityNames = new String[]{
        "New Bark","Cherrygrove","Violet","Azalea","Goldenrod","Ecruteak","Olivine","Cianwood","Mahogany","Blackthorn",
        "Pallet","Viridian","Pewter","Cerulean","Vermillion","Lavender","Celadon","Fuchsia","Saffron","Cinnibar"};

    public CityInfo(){
        cityLocation = Vector2.Zero;
    }  // for json parsing, we need to have a default constructor


    public CityInfo(CivilizationInfo civInfo, Vector2 cityLocation) {

        this.civInfo =civInfo;
        setTransients();

        name = CityNames[civInfo.cities.size()];
        this.cityLocation = cityLocation;
        civInfo.cities.add(this);
        civInfo.gameInfo.addNotification(name+" has been founded!",cityLocation);
        if(civInfo.policies.isAdopted("Legalism") && civInfo.cities.size() <= 4) cityConstructions.addCultureBuilding();
        if(civInfo.cities.size()==1) {
            cityConstructions.builtBuildings.add("Palace");
            cityConstructions.currentConstruction = "Worker"; // Default for first city only!
        }

        for(TileInfo tileInfo : civInfo.gameInfo.tileMap.getTilesInDistance(cityLocation,1)) {
            tileInfo.owner = civInfo.civName;
        }

        TileInfo tile = getTile();
        tile.workingCity = this.name;
        tile.roadStatus = RoadStatus.Railroad;
        if(new Linq<String>("Forest","Jungle","Marsh").contains(tile.terrainFeature))
            tile.terrainFeature=null;

        population.autoAssignWorker();
        cityStats.update();
    }

    public void setTransients(){
        population.cityInfo = this;
        expansion.cityInfo = this;
        cityStats.cityInfo = this;
        cityConstructions.cityInfo=this;
    }

    public LinqCounter<TileResource> getCityResources(){
        LinqCounter<TileResource> cityResources = new LinqCounter<TileResource>();

        for (TileInfo tileInfo : getTilesInRange()) {
            TileResource resource = tileInfo.getTileResource();
            if (resource != null && (resource.improvement.equals(tileInfo.improvement) || tileInfo.isCityCenter()))
                cityResources.add(resource,1);
        }
        // Remove resources required by buildings
        for(Building building : cityConstructions.getBuiltBuildings()){
            if(building.requiredResource!=null){
                TileResource resource = GameBasics.TileResources.get(building.requiredResource);
                cityResources.add(resource,-1);
            }
        }
        return cityResources;
    }


    public void nextTurn() {
        FullStats stats = cityStats.currentCityStats;
        if (cityConstructions.currentConstruction.equals(CityConstructions.Settler) && stats.food > 0) {
            stats.production += stats.food;
            stats.food = 0;
        }

        population.nextTurn(stats.food);
        cityConstructions.nextTurn(stats);
        expansion.nextTurn(stats.culture);
    }

    double rankTile(TileInfo tile){
        FullStats stats = tile.getTileStats(this,civInfo);
        double rank=0;
        if(stats.food <= 2) rank+=stats.food;
        else rank += 2 + (stats.food -2)/2; // 1 point for each food up to 2, from there on half a point
        rank+=stats.gold /2;
        rank+=stats.production;
        rank+=stats.science;
        rank+=stats.culture;
        if(tile.improvement == null) rank+=0.5; // improvement potential!
        if(tile.resource!=null) rank+=1;
        return rank;
    }

    public Linq<String> getBuildingUniques(){
        return cityConstructions.getBuiltBuildings().select(new Linq.Func<Building, String>() {
            @Override
            public String GetBy(Building arg0) {
                return arg0.unique;
            }
        }).where(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                return arg0!=null;
            }
        });
    }

    public FullStats getGreatPersonPoints(){
        FullStats greatPersonPoints = population.getSpecialists().multiply(3);

        for(Building building : cityConstructions.getBuiltBuildings())
            if(building.greatPersonPoints!=null)
                greatPersonPoints.add(building.greatPersonPoints);

        if(civInfo.getBuildingUniques().contains("GreatPersonGenerationIncrease"))
            greatPersonPoints = greatPersonPoints.multiply(1.33f);
        if(civInfo.policies.isAdopted("Entrepreneurship"))
            greatPersonPoints.gold*=1.25;
        if(civInfo.policies.isAdopted("Freedom"))
            greatPersonPoints = greatPersonPoints.multiply(1.25f);

        return greatPersonPoints;
    }
}