package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;
import com.unciv.models.LinqHashMap;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.ResourceType;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.FullStats;

import java.util.ArrayList;

public class CityInfo {
    public final Vector2 cityLocation;
    public String name;

    public CityBuildings cityBuildings;
    public int cultureStored;
    private int tilesClaimed;
    public int population = 1;
    public int foodStored = 0;

    private TileMap getTileMap(){return UnCivGame.Current.civInfo.tileMap; }

    public TileInfo getTile(){return getTileMap().get(cityLocation);}
    public LinqCollection<TileInfo> getTilesInRange(){
        return getTileMap().getTilesInDistance(cityLocation,3).where(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return UnCivGame.Current.civInfo.civName.equals(arg0.owner);
            }
        });
    }

    private String[] CityNames = new String[]{"Assur", "Ninveh", "Nimrud", "Kar-Tukuli-Ninurta", "Dur-Sharrukin"};

    public CityInfo(){
        cityLocation = Vector2.Zero;
    }  // for json parsing, we need to have a default constructor

    public int getCultureToNextTile(){
        // This one has conflicting sources -
        // http://civilization.wikia.com/wiki/Mathematics_of_Civilization_V says it's 20+(10(t-1))^1.1
        // https://www.reddit.com/r/civ/comments/58rxkk/how_in_gods_name_do_borders_expand_in_civ_vi/ has it
        //   (per game XML files) at 6*(t+0.4813)^1.3
        // The second seems to be more based, so I'll go with that
        double a = 6*Math.pow(tilesClaimed+1.4813,1.3);
        if(CivilizationInfo.current().getBuildingUniques().contains("NewTileCostReduction")) a *= 0.75; //Speciality of Angkor Wat
        return (int)Math.round(a);
    }

    CityInfo(CivilizationInfo civInfo, Vector2 cityLocation) {
        name = CityNames[civInfo.cities.size()];
        this.cityLocation = cityLocation;
        cityBuildings = new CityBuildings(this);

        for(TileInfo tileInfo : civInfo.tileMap.getTilesInDistance(cityLocation,1)) {
            tileInfo.owner = civInfo.civName;
        }

        TileInfo tile = getTile();
        tile.workingCity = this.name;
        tile.roadStatus = RoadStatus.Railroad;
        if("Forest".equals(tile.terrainFeature) || "Jungle".equals(tile.terrainFeature) || "Marsh".equals(tile.terrainFeature))
            tile.terrainFeature=null;

        autoAssignWorker();
        civInfo.cities.add(this);
    }

    public LinqHashMap<TileResource,Integer> getCityResources(){
        LinqHashMap<TileResource,Integer> cityResources = new LinqHashMap<TileResource, Integer>();

        for (TileInfo tileInfo : getTilesInRange()) {
            TileResource resource = tileInfo.getTileResource();
            if (resource != null && (resource.improvement.equals(tileInfo.improvement) || tileInfo.isCityCenter())){
                if(cityResources.containsKey(resource)) cityResources.put(resource,cityResources.get(resource)+1);
                else cityResources.put(resource,1);
            }
        }
        // Remove resources required by buildings
        for(Building building : cityBuildings.getBuiltBuildings()){
            if(building.requiredResource!=null){
                TileResource resource = GameBasics.TileResources.get(building.requiredResource);
                if(cityResources.containsKey(resource)) cityResources.put(resource,cityResources.get(resource)-1);
                else cityResources.put(resource,-1);
            }
        }
        return cityResources;
    }

    private int getWorkingPopulation() {
        return getTilesInRange().count(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return name.equals(arg0.workingCity);
            }
        })-1; // 1 is the city center
    }

    public int getFreePopulation() {
        return population - getWorkingPopulation();
    }

    public boolean hasNonWorkingPopulation() {
        return getFreePopulation() > 0;
    }

    public FullStats getCityStats() {
        FullStats stats = new FullStats();
        stats.science += population;

        // Working ppl
        for (TileInfo cell : getTilesInRange())
            if (name.equals(cell.workingCity) || cell.isCityCenter())
                stats.add(cell.getTileStats());

        //idle ppl
        stats.production += getFreePopulation();
        stats.food -= population * 2;

        if(!isCapital() && isConnectedToCapital()) { // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
            double goldFromTradeRoute = CivilizationInfo.current().getCapital().population * 0.15
                    + population * 1.1 - 1;
            if(CivilizationInfo.current().getBuildingUniques().contains("TradeRouteGoldIncrease")) goldFromTradeRoute*=1.25; // Machu Pichu speciality
            stats.gold += goldFromTradeRoute;
        }

        stats.add(cityBuildings.getStats());

        FullStats statPercentBonuses = cityBuildings.getStatPercentBonuses();
        stats.food*=1+statPercentBonuses.food/100;
        stats.gold*=1+statPercentBonuses.gold/100;
        stats.production*=1+statPercentBonuses.production/100;
        stats.science*=1+statPercentBonuses.science/100;
        stats.culture*=1+statPercentBonuses.culture/100;

        stats.gold-=cityBuildings.getMaintainanceCosts(); // this is AFTER the bonus calculation!
        if(CivilizationInfo.current().getHappinessForNextTurn() < 0)
            stats.food /= 4; // Reduce excess food to 1/4

        return stats;
    }

    public float getCityHappiness(){ // needs to be a separate function because we need to know the global happiness state
        // in order to determine how much food is produced in a city!
        float happiness = -3 - population; // -3 happiness per city and -1 per population
        return happiness + (int)cityBuildings.getStats().happiness;
    }

    void nextTurn() {
        FullStats stats = getCityStats();

        if (cityBuildings.currentBuilding.equals(CityBuildings.Settler) && stats.food > 0) {
            stats.production += stats.food;
            stats.food = 0;
        }

        foodStored += stats.food;
        if (foodStored < 0) // starvation!
        {
            population--;
            foodStored = 0;
            CivilizationInfo.current().notifications.add(name+" is starving!");
        }
        if (foodStored >= foodToNextPopulation()) // growth!
        {
            foodStored -= foodToNextPopulation();
            if(getBuildingUniques().contains("FoodCarriesOver")) foodStored+=0.4f*foodToNextPopulation(); // Aqueduct special
            population++;
            autoAssignWorker();
            CivilizationInfo.current().notifications.add(name+" has grown!");
        }

        cityBuildings.nextTurn(stats);

        cultureStored+=stats.culture;
        if(cultureStored>=getCultureToNextTile()){
            addNewTile();
            CivilizationInfo.current().notifications.add(name+" has expanded its borders!");
        }
    }

    private void addNewTile(){
        cultureStored -= getCultureToNextTile();
        tilesClaimed++;
        LinqCollection<Vector2> possibleNewTileVectors = new LinqCollection<Vector2>();

        for (int i = 2; i <4 ; i++) {
            LinqCollection<TileInfo> tiles = getTileMap().getTilesInDistance(cityLocation,i);
            tiles = tiles.where(new Predicate<TileInfo>() {
                @Override
                public boolean evaluate(TileInfo arg0) {
                    return arg0.owner == null;
                }
            });
            if(tiles.size()==0) continue;

            TileInfo TileChosen=null;
            double TileChosenRank=0;
            for(TileInfo tile : tiles){
                double rank = rankTile(tile);
                if(rank>TileChosenRank){
                    TileChosenRank = rank;
                    TileChosen = tile;
                }
            }
            TileChosen.owner = UnCivGame.Current.civInfo.civName;
            return;
        }
    }

    private void autoAssignWorker() {
        double maxValue = 0;
        TileInfo toWork = null;
        for (TileInfo tileInfo : getTilesInRange()) {
            if (tileInfo.workingCity !=null) continue;
            FullStats stats = tileInfo.getTileStats();

            double value = stats.food + stats.production * 0.5;
            if (value > maxValue) {
                maxValue = value;
                toWork = tileInfo;
            }
        }

        if(toWork!=null) // This is when we've run out of tiles!
            toWork.workingCity = name;
    }

    private double rankTile(TileInfo tile){
        FullStats stats = tile.getTileStats();
        double rank=0;
        if(stats.food <2) rank+=stats.food;
        else rank += 2 + (stats.food -2)/2; // 1 point for each food up to 2, from there on half a point
        rank+=stats.gold /2;
        rank+=stats.production;
        rank+=stats.science;
        rank+=stats.culture;
        if(tile.improvement ==null) rank+=0.5; // improvement potential!
        return rank;
    }

    private boolean isCapital(){ return CivilizationInfo.current().getCapital() == this; }

    private boolean isConnectedToCapital(){
        TileInfo capitalTile = CivilizationInfo.current().getCapital().getTile();
        LinqCollection<TileInfo> tilesReached = new LinqCollection<TileInfo>();
        LinqCollection<TileInfo> tilesToCheck = new LinqCollection<TileInfo>();
        tilesToCheck.add(getTile());
        while(!tilesToCheck.isEmpty()){
            LinqCollection<TileInfo> newTiles = new LinqCollection<TileInfo>();
            for(TileInfo tile : tilesToCheck)
                for (TileInfo maybeNewTile : getTileMap().getTilesInDistance(tile.position,1))
                    if(!tilesReached.contains(maybeNewTile) && !tilesToCheck.contains(maybeNewTile) && !newTiles.contains(maybeNewTile))
                        newTiles.add(maybeNewTile);

            if(newTiles.contains(capitalTile)) return true;
            tilesReached.addAll(tilesToCheck);
            tilesToCheck = newTiles;
        }
        return false;
    }

    public int foodToNextPopulation()
    {
        // civ v math,civilization.wikia
        return 15 + 6 * (population - 1) + (int)Math.floor(Math.pow(population - 1, 1.8f));
    }

    public LinqCollection<String> getBuildingUniques(){
        return cityBuildings.getBuiltBuildings().select(new LinqCollection.Func<Building, String>() {
            @Override
            public String GetBy(Building arg0) {
                return arg0.unique;
            }
        }).unique();
    }

}