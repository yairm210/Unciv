package com.unciv.logic.city;

import com.badlogic.gdx.utils.Predicate;
import com.unciv.models.linq.Linq;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.stats.FullStats;


import java.util.HashMap;


public class CityConstructions
{
    public transient CityInfo cityInfo;

    static final String Worker="Worker";
    static final String Settler="Settler";

    public CityConstructions(){} // for json parsing, we need to have a default constructor

    public Linq<String> builtBuildings = new Linq<String>();
    public HashMap<String, Integer> inProgressConstructions = new HashMap<String, Integer>();
    public String currentConstruction; // default starting building!
    public IConstruction getCurrentConstruction(){return getConstruction(currentConstruction);}

    public boolean isBuilt(String buildingName) { return builtBuildings.contains(buildingName); }
    public boolean isBuilding(String buildingName) { return currentConstruction !=null && currentConstruction.equals(buildingName); }

    IConstruction getConstruction(String constructionName) {
        if(GameBasics.Buildings.containsKey(constructionName))
            return GameBasics.Buildings.get(constructionName);
        else if(GameBasics.Units.containsKey(constructionName))
            return GameBasics.Units.get(constructionName);
        return null;
    }

    public Linq<Building> getBuiltBuildings(){ return  builtBuildings.select(new Linq.Func<String, Building>() {
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
            cityInfo.civInfo.gameInfo.addNotification("Cannot continue work on "+saveCurrentConstruction,cityInfo.cityLocation);
            chooseNextConstruction();
            construction = getConstruction(currentConstruction);
        }
        else currentConstruction = saveCurrentConstruction;

        addConstruction(Math.round(cityStats.production));
        int productionCost = construction.getProductionCost(cityInfo.civInfo.policies.getAdoptedPolicies());
        if (inProgressConstructions.get(currentConstruction) >= productionCost)
        {
            construction.postBuildEvent(this);
            inProgressConstructions.remove(currentConstruction);
            cityInfo.civInfo.gameInfo.addNotification(currentConstruction +" has been built in "+cityInfo.name,cityInfo.cityLocation);

            chooseNextConstruction();
        }

    }

    public void chooseNextConstruction() {
        currentConstruction = getBuildableBuildings().first(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                if(((Building)getConstruction(arg0)).isWonder) return false;
                return !builtBuildings.contains(arg0);
            }
        });
        if (currentConstruction == null) currentConstruction = Worker;

        cityInfo.civInfo.gameInfo.addNotification("Work has started on "+ currentConstruction,cityInfo.cityLocation);
    }


    public Linq<String> getBuildableBuildings()
    {
        final CityConstructions self=this;
        return new Linq<Building>(GameBasics.Buildings.values())
                .where(new Predicate<Building>() {
            @Override
            public boolean evaluate(Building arg0) { return (arg0.isBuildable(self)); }
        }).select(new Linq.Func<Building, String>() {
                    @Override
                    public String GetBy(Building arg0) {
                        return arg0.name;
                    }
                });
    }

    public FullStats getStats()
    {
        FullStats stats = new FullStats();
        for(Building building : getBuiltBuildings())
            stats.add(building.getStats(cityInfo.civInfo.policies.getAdoptedPolicies()));
        stats.science += cityInfo.getBuildingUniques().count(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                return "SciencePer2Pop".equals(arg0);
            }
        }) * cityInfo.population.population/2; // Library and public school unique (not actualy unique, though...hmm)
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

        int productionCost = getConstruction(constructionName).getProductionCost(cityInfo.civInfo.policies.getAdoptedPolicies());

        float workLeft = productionCost - workDone(constructionName); // needs to be float so that we get the cieling properly ;)

        FullStats cityStats = cityInfo.cityStats.currentCityStats;
        int production = Math.round(cityStats.production);
        if (constructionName.equals(Settler)) production += cityStats.food;

        return (int) Math.ceil(workLeft / production);
    }

    public void purchaseBuilding(String buildingName) {
        cityInfo.civInfo.gold -= getConstruction(buildingName).getGoldCost(cityInfo.civInfo.policies.getAdoptedPolicies());
        getConstruction(buildingName).postBuildEvent(this);
        if(currentConstruction.equals(buildingName)) chooseNextConstruction();
        cityInfo.cityStats.update();
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
        return " ("+ workDone(currentConstruction) + "/"+ getCurrentConstruction().getProductionCost(cityInfo.civInfo.policies.getAdoptedPolicies())+")";
    }

    public void addCultureBuilding() {
        for (String string : new Linq<String>("Monument","Temple","Opera House","Museum")){
            if(!builtBuildings.contains(string)){
                builtBuildings.add(string);
                if(currentConstruction.equals(string))
                    chooseNextConstruction();
                break;
            }
        }
    }
}