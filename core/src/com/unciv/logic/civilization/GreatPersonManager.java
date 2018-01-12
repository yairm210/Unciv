package com.unciv.logic.civilization;

import com.unciv.logic.city.CityInfo;
import com.unciv.models.stats.FullStats;

public class GreatPersonManager{

    public void addGreatPerson(String unitName){ // This is also done by some wonders and social policies, remember
        CivilizationInfo civInfo = CivilizationInfo.current();
        civInfo.tileMap.placeUnitNearTile(civInfo.getCapital().cityLocation,unitName);
        civInfo.addNotification("A "+unitName+" has been born!",civInfo.getCapital().cityLocation);
    }

    public void greatPersonPointsForTurn(){
        for(CityInfo city : CivilizationInfo.current().cities)
            greatPersonPoints.add(city.getGreatPersonPoints());

        if(greatPersonPoints.science>pointsForNextGreatPerson){
            greatPersonPoints.science-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            addGreatPerson("Great Scientist");
        }
        if(greatPersonPoints.production>pointsForNextGreatPerson){
            greatPersonPoints.production-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            addGreatPerson("Great Engineer");
        }
        if(greatPersonPoints.culture>pointsForNextGreatPerson){
            greatPersonPoints.culture-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            addGreatPerson("Great Artist");
        }
        if(greatPersonPoints.gold>pointsForNextGreatPerson){
            greatPersonPoints.gold-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            addGreatPerson("Great Merchant");
        }
    }

    public int pointsForNextGreatPerson=100;

    public FullStats greatPersonPoints = new FullStats();

}
