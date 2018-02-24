package com.unciv.ui;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.city.CityInfo;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.civilization.Notification;
import com.unciv.logic.map.TileInfo;
import com.unciv.logic.map.TileMap;
import com.unciv.models.linq.Linq;

public class GameInfo{

    public Linq<Notification> notifications = new Linq<Notification>();
    public void addNotification(String text, Vector2 location){
        notifications.add(new Notification(text,location));
    }

    public Linq<String> tutorial = new Linq<String>();
    public Linq<CivilizationInfo> civilizations = new Linq<CivilizationInfo>();
    public TileMap tileMap;
    public int turns = 1;

    public void nextTurn(){
        notifications.clear();

        for(CivilizationInfo civInfo : civilizations) civInfo.nextTurn();

        for(TileInfo tile : tileMap.getValues().where(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.unit!=null;
            }
        })) tile.nextTurn();

        // We need to update the stats after ALL the cities are done updating because
        // maybe one of them has a wonder that affects the stats of all the rest of the cities

        for(CivilizationInfo civInfo : civilizations)
            for (CityInfo city : civInfo.cities)
                city.cityStats.update();

        turns++;
    }

    public void setTransients(){
        tileMap.gameInfo=this;
        tileMap.setTransients();

        for(CivilizationInfo civInfo : civilizations){
            civInfo.gameInfo = this;
            civInfo.setTransients();
        }

        for(final TileInfo tile : tileMap.getValues())
            if(tile.unit!=null) tile.unit.civInfo=civilizations.first(new Predicate<CivilizationInfo>() {
                @Override
                public boolean evaluate(CivilizationInfo arg0) {
                    return arg0.civName.equals(tile.unit.owner);
                }
            });


        for(CivilizationInfo civInfo : civilizations)
            for(CityInfo cityInfo : civInfo.cities)
                cityInfo.cityStats.update();

    }

    public CivilizationInfo getPlayerCivilization() {
        return civilizations.get(0);
    }
}
