package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;
import com.unciv.models.LinqCounter;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.FullStats;

public class CityInfo {
    public final Vector2 cityLocation;
    public String name;

    public CityConstructions cityConstructions;
    public int cultureStored;
    private int tilesClaimed;
    public int population = 1;
    public int foodStored = 0;
    public FullStats specialists = new FullStats();

    public FullStats cityStats; // This is so we won't have to calculate this multiple times - takes a lot of time, especially on phones!

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

    private String[] CityNames = new String[]{
        "New Bark","Cherrygrove","Violet","Azalea","Goldenrod","Ecruteak","Olivine","Cianwood","Mahogany","Blackthorn",
        "Pallet","Viridian","Pewter","Cerulean","Vermillion","Lavender","Celadon","Fuchsia","Saffron","Cinnibar"};

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
        cityConstructions = new CityConstructions(this);
        if(civInfo.cities.size()==0) {
            cityConstructions.builtBuildings.add("Palace");
            cityConstructions.currentConstruction = "Worker"; // Default for first city only!
        }
        civInfo.cities.add(this);

        for(TileInfo tileInfo : civInfo.tileMap.getTilesInDistance(cityLocation,1)) {
            tileInfo.owner = civInfo.civName;
        }

        TileInfo tile = getTile();
        tile.workingCity = this.name;
        tile.roadStatus = RoadStatus.Railroad;
        if("Forest".equals(tile.terrainFeature) || "Jungle".equals(tile.terrainFeature) || "Marsh".equals(tile.terrainFeature))
            tile.terrainFeature=null;

        autoAssignWorker();
        updateCityStats();
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

    public int getFreePopulation() {
        int workingPopulation = getTilesInRange().count(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return name.equals(arg0.workingCity);
            }
        })-1; // 1 is the city center
        int specialistNum = (int) (specialists.science+specialists.production+specialists.culture+specialists.gold);
        return population - workingPopulation - specialistNum;
    }

    public boolean hasNonWorkingPopulation() {
        return getFreePopulation() > 0;
    }

    public void updateCityStats() {
        FullStats stats = new FullStats();
        stats.science += population;

        // Working ppl
        for (TileInfo cell : getTilesInRange())
            if (name.equals(cell.workingCity))
                stats.add(cell.getTileStats(this));

        // Specialists
        stats.culture+=specialists.culture*3;
        stats.production+=specialists.production*2;
        stats.science+=specialists.science*3;
        stats.gold+=specialists.gold*2;

        //idle ppl
        stats.production += getFreePopulation();

        CivilizationInfo civInfo = CivilizationInfo.current();
        if(!isCapital() && isConnectedToCapital(RoadStatus.Road)) {
            // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
            double goldFromTradeRoute = civInfo.getCapital().population * 0.15
                    + population * 1.1 - 1;
            if(civInfo.getBuildingUniques().contains("TradeRouteGoldIncrease")) goldFromTradeRoute*=1.25; // Machu Pichu speciality
            stats.gold += goldFromTradeRoute;
        }

        stats.add(cityConstructions.getStats());

        FullStats statPercentBonuses = cityConstructions.getStatPercentBonuses();
        if(isCapital() || isConnectedToCapital(RoadStatus.Railroad)) statPercentBonuses.production += 25;
        if(civInfo.isGoldenAge()) statPercentBonuses.production+=20;
        IConstruction currentConstruction = cityConstructions.getCurrentConstruction();
        if(currentConstruction instanceof Building && ((Building)currentConstruction).isWonder &&
                civInfo.getCivResources().containsKey(GameBasics.TileResources.get("Marble")))
            statPercentBonuses.production+=15;


        stats.production*=1+statPercentBonuses.production/100;  // So they get bonuses for production and gold/science
        if(cityConstructions.currentConstruction.equals("Gold")) stats.gold+=stats.production/4;
        if(cityConstructions.currentConstruction.equals("Science")) {
            if (civInfo.getBuildingUniques().contains("ScienceConversionIncrease"))
                stats.science += stats.production / 3;
            else stats.science += stats.production / 4;
        }

        stats.gold*=1+statPercentBonuses.gold/100;
        stats.science*=1+statPercentBonuses.science/100;
        stats.culture*=1+statPercentBonuses.culture/100;

        boolean isUnhappy = civInfo.getHappinessForNextTurn() < 0;
        if (!isUnhappy) stats.food*=1+statPercentBonuses.food/100; // Regular food bonus revoked when unhappy per https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
        stats.food -= population * 2; // Food reduced after the bonus
        if(isUnhappy) stats.food /= 4; // Reduce excess food to 1/4 per the same


        stats.gold-= cityConstructions.getMaintainanceCosts(); // this is AFTER the bonus calculation!
        this.cityStats = stats;
    }

    public float getCityHappiness(){ // needs to be a separate function because we need to know the global happiness state
        // in order to determine how much food is produced in a city!
        float happiness = -3; // -3 happiness per city
        if(CivilizationInfo.current().getBuildingUniques().contains("CitizenUnhappinessDecreased"))
            happiness-=population*0.9;
        else happiness-=population; //and -1 per population
        return happiness + (int) cityConstructions.getStats().happiness;
    }

    void nextTurn() {
        FullStats stats = cityStats;
        if (cityConstructions.currentConstruction.equals(CityConstructions.Settler) && stats.food > 0) {
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

        cityConstructions.nextTurn(stats);

        cultureStored+=stats.culture;
        if(cultureStored>=getCultureToNextTile()){
            addNewTile();
            CivilizationInfo.current().notifications.add(name+" has expanded its borders!");
        }

        CivilizationInfo civInfo = CivilizationInfo.current();
        float greatPersonGenerationMultiplier = 3;
        if(civInfo.getBuildingUniques().contains("GreatPersonGenerationIncrease")) greatPersonGenerationMultiplier*=1.33;
        civInfo.greatPersonPoints.gold+=specialists.gold*greatPersonGenerationMultiplier;
        civInfo.greatPersonPoints.production+=specialists.production*greatPersonGenerationMultiplier;
        civInfo.greatPersonPoints.culture+=specialists.culture*greatPersonGenerationMultiplier;
        civInfo.greatPersonPoints.science+=specialists.science*greatPersonGenerationMultiplier;
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
            double value = rankTile(tileInfo);
            if (value > maxValue) {
                maxValue = value;
                toWork = tileInfo;
            }
        }

        if(toWork!=null) // This is when we've run out of tiles!
            toWork.workingCity = name;
    }

    private double rankTile(TileInfo tile){
        FullStats stats = tile.getTileStats(this);
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

    private boolean isConnectedToCapital(RoadStatus roadType){
        TileInfo capitalTile = CivilizationInfo.current().getCapital().getTile();
        LinqCollection<TileInfo> tilesReached = new LinqCollection<TileInfo>();
        LinqCollection<TileInfo> tilesToCheck = new LinqCollection<TileInfo>();
        tilesToCheck.add(getTile());
        while(!tilesToCheck.isEmpty()){
            LinqCollection<TileInfo> newTiles = new LinqCollection<TileInfo>();
            for(TileInfo tile : tilesToCheck)
                for (TileInfo maybeNewTile : getTileMap().getTilesInDistance(tile.position,1))
                    if(!tilesReached.contains(maybeNewTile) && !tilesToCheck.contains(maybeNewTile) && !newTiles.contains(maybeNewTile)
                            && (roadType != RoadStatus.Road || maybeNewTile.roadStatus != RoadStatus.None)
                            && (roadType!=RoadStatus.Railroad || maybeNewTile.roadStatus == roadType))
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
        return cityConstructions.getBuiltBuildings().select(new LinqCollection.Func<Building, String>() {
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




}