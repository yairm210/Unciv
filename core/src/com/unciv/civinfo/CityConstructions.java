package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.stats.FullStats;

import java.util.HashMap;


public class CityConstructions
{
    static final String Worker="Worker";
    static final String Settler="Settler";

    public Vector2 cityLocation;

    public CityConstructions(){} // for json parsing, we need to have a default constructor

    public CityConstructions(CityInfo cityInfo)
    {
        cityLocation = cityInfo.cityLocation;
        chooseNextConstruction();
    }

    public LinqCollection<String> builtBuildings = new LinqCollection<String>();
    public HashMap<String, Integer> inProgressConstructions = new HashMap<String, Integer>();
    public String currentConstruction; // default starting building!
    public IConstruction getCurrentConstruction(){return getConstruction(currentConstruction);}

    public CityInfo getCity(){return UnCivGame.Current.civInfo.tileMap.get(cityLocation).getCity(); }
    public boolean isBuilt(String buildingName) { return builtBuildings.contains(buildingName); }
    public boolean isBuilding(String buildingName) { return currentConstruction !=null && currentConstruction.equals(buildingName); }

    IConstruction getConstruction(String constructionName) {
        if(GameBasics.Buildings.containsKey(constructionName))
            return GameBasics.Buildings.get(constructionName);
        else if(GameBasics.Units.containsKey(constructionName))
            return GameBasics.Units.get(constructionName);
        return null;
    }

    public LinqCollection<Building> getBuiltBuildings(){ return  builtBuildings.select(new LinqCollection.Func<String, Building>() {
        @Override
        public Building GetBy(String arg0) {
            return GameBasics.Buildings.get(arg0);
        }
    }); }

    public void addConstruction(int constructionToAdd){
        if (!inProgressConstructions.containsKey(currentConstruction)) inProgressConstructions.put(currentConstruction, 0);
        inProgressConstructions.put(currentConstruction, inProgressConstructions.get(currentConstruction) + constructionToAdd);
    }

    public void nextTurn(FullStats cityStats)
    {
        if (getCurrentConstruction()==null) return;

        IConstruction construction = getConstruction(currentConstruction);

        // Let's try to remove the building from the city, and see if we can still build it (weneed to remove because of wonders etc.
        String saveCurrentConstruction = currentConstruction;
        currentConstruction = null;
        if(!construction.isBuildable(this)){
            // We can't build this building anymore! (Wonder has been built / resource is gone / etc.)
            CivilizationInfo.current().notifications.add("Cannot continue work on "+saveCurrentConstruction);
            chooseNextConstruction();
            construction = getConstruction(currentConstruction);
        }
        else currentConstruction = saveCurrentConstruction;

        addConstruction(Math.round(cityStats.production));
        if (inProgressConstructions.get(currentConstruction) >= construction.getProductionCost())
        {
            construction.postBuildEvent(this);
            inProgressConstructions.remove(currentConstruction);
            CivilizationInfo.current().notifications.add(currentConstruction +" has been built in "+getCity().name);

            chooseNextConstruction();
        }

    }

    private void chooseNextConstruction() {
        currentConstruction = getBuildableBuildings().first(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                if(((Building)getConstruction(arg0)).isWonder) return false;
                return !builtBuildings.contains(arg0);
            }
        });
        if (currentConstruction == null) currentConstruction = Worker;

        CivilizationInfo.current().notifications.add("Work has started on "+ currentConstruction);
    }


    public LinqCollection<String> getBuildableBuildings()
    {
        final CityConstructions self=this;
        return new LinqCollection<Building>(GameBasics.Buildings.values())
                .where(new Predicate<Building>() {
            @Override
            public boolean evaluate(Building arg0) { return (arg0.isBuildable(self)); }
        })
                .select(new LinqCollection.Func<Building, String>() {
                    @Override
                    public String GetBy(Building arg0) {
                        return arg0.name;
                    }
                });
    }

    public FullStats getStats()
    {
        FullStats stats = new FullStats();
        for(Building building : getBuiltBuildings()) stats.add(building.getStats());
        stats.science += getCity().getBuildingUniques().count(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                return "SciencePer2Pop".equals(arg0);
            }
        }) * getCity().population/2; // Library and public school unique (not actualy unique, though...hmm)
        return stats;
    }

    public int getMaintainanceCosts(){
        int maintainanceTotal = 0;
        for( Building building : getBuiltBuildings()) maintainanceTotal+=building.maintainance;
        return maintainanceTotal;
    }

    public FullStats getStatPercentBonuses(){

        FullStats stats = new FullStats();
        for(Building building : getBuiltBuildings())
            if(building.percentStatBonus != null)
                stats.add(building.percentStatBonus);

        return stats;
    }

    public int workDone(String constructionName) {
        if (inProgressConstructions.containsKey(constructionName))
            return inProgressConstructions.get(constructionName);
        return 0;
    }

    public int turnsToConstruction(String constructionName){
        int cost = getConstruction(constructionName).getProductionCost();

        float workLeft = cost - workDone(constructionName); // needs to be float so that we get the cieling properly ;)

        FullStats cityStats = getCity().cityStats;
        int production = Math.round(cityStats.production);
        if (constructionName.equals(Settler)) production += cityStats.food;

        return (int) Math.ceil(workLeft / production);
    }

    public void purchaseBuilding(String buildingName) {
        CivilizationInfo.current().civStats.gold -= getConstruction(buildingName).getGoldCost();
        getConstruction(buildingName).postBuildEvent(this);
        if(currentConstruction.equals(buildingName)) chooseNextConstruction();
        getCity().updateCityStats();
    }

    public String getCityProductionTextForCityButton(){
        String result = currentConstruction;
        if(!result.equals("Science") && !result.equals("Gold"))
            result+="\r\n"+ turnsToConstruction(currentConstruction)+" turns";
        return result;
    }

    public String getProductionForTileInfo(){
        String result = currentConstruction;
        if(!result.equals("Science") && !result.equals("Gold"))
            result+="\r\nin "+ turnsToConstruction(currentConstruction)+" turns,\r\n";
        return result;
    }

    public String getAmountConstructedText(){
        if(currentConstruction.equals("Science")  || currentConstruction.equals("Gold")) return "";
        return " ("+ workDone(currentConstruction) + "/"+ getCurrentConstruction().getProductionCost()+")";
    }
}