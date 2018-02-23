package com.unciv.ui.cityscreen;

import com.badlogic.gdx.utils.Align;
import com.unciv.logic.city.CityInfo;
import com.unciv.logic.map.TileInfo;
import com.unciv.ui.tilegroups.TileGroup;
import com.unciv.ui.utils.ImageGetter;

public class CityTileGroup extends TileGroup {

    public YieldGroup yield = new YieldGroup();
    private final CityInfo city;

    public CityTileGroup(CityInfo city, TileInfo tileInfo) {
        super(tileInfo);
        this.city = city;

        addActor(yield);

        if(city.cityLocation.equals(tileInfo.position)){
            populationImage = ImageGetter.getImage("StatIcons/City_Center_(Civ6).png");
            addActor(populationImage);
        }

    }

    public void update(){
        super.update();

        if(populationImage!=null) {
            populationImage.setSize(30, 30);
            populationImage.setPosition(getWidth()/2-populationImage.getWidth()/2,
                    getHeight()*0.85f-populationImage.getHeight()/2);
        }

        if(improvementImage!=null) improvementImage.setColor(1, 1, 1, 0.5f);
        if(unitImage!=null) unitImage.setColor(1, 1, 1, 0.5f);
        if(resourceImage!=null) resourceImage.setColor(1, 1, 1, 0.5f);

        yield.setStats(tileInfo.getTileStats(city,city.civInfo.gameInfo.getPlayerCivilization()));
        yield.setOrigin(Align.center);
        yield.setScale(0.7f);
        yield.toFront();
        yield.setPosition(getWidth() / 2 - yield.getWidth() / 2, getHeight() *0.25f - yield.getHeight() / 2);

    }
}
