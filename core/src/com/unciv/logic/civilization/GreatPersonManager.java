package com.unciv.logic.civilization;

import com.unciv.logic.city.CityInfo;
import com.unciv.models.stats.FullStats;

public class GreatPersonManager{

    private int pointsForNextGreatPerson=100;
    private FullStats greatPersonPoints = new FullStats();

    public void addGreatPersonPoints(FullStats greatPersonPoints){ greatPersonPoints.add(greatPersonPoints);}

    public String getNewGreatPerson(){
        if(greatPersonPoints.science>pointsForNextGreatPerson){
            greatPersonPoints.science-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            return "Great Scientist";
        }
        if(greatPersonPoints.production>pointsForNextGreatPerson){
            greatPersonPoints.production-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            return "Great Engineer";
        }
        if(greatPersonPoints.culture>pointsForNextGreatPerson){
            greatPersonPoints.culture-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            return "Great Artist";
        }
        if(greatPersonPoints.gold>pointsForNextGreatPerson){
            greatPersonPoints.gold-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            return "Great Merchant";
        }
        return null;
    }
    
}
