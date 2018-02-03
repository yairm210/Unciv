package com.unciv.ui.cityscreen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.logic.city.CityInfo;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.linq.Linq;
import com.unciv.models.stats.FullStats;

public class BuildingsTable extends Table {

    final CityScreen cityScreen;

    public BuildingsTable(CityScreen cityScreen) {
        this.cityScreen = cityScreen;
    }

    private Image getSpecialistIcon(String imageName, final String building, final boolean isFilled, final FullStats specialistType) {
        Image specialist = com.unciv.ui.utils.ImageGetter.getImage(imageName);
        specialist.setSize(50,50);
        if(!isFilled) specialist.setColor(Color.GRAY);
        specialist.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                CityInfo cityInfo = cityScreen.getCity();
                if(isFilled) cityInfo.population.buildingsSpecialists.get(building).add(specialistType.minus()); //unassign
                else if(cityInfo.population.getFreePopulation()==0) return;
                else {
                    if(!cityInfo.population.buildingsSpecialists.containsKey(building))
                        cityInfo.population.buildingsSpecialists.put(building,new FullStats());
                    cityInfo.population.buildingsSpecialists.get(building).add(specialistType); //assign!}
                }

                cityInfo.cityStats.update();
                cityScreen.update();
            }
        });
        return specialist;
    }

    void update(){
        clear();
        Skin skin = cityScreen.skin;

        CityInfo cityInfo = cityScreen.getCity();
        Linq<Building> Wonders = new Linq<Building>();
        Linq<Building> SpecialistBuildings = new Linq<Building>();
        Linq<Building> Others = new Linq<Building>();

        for(Building building : cityInfo.cityConstructions.getBuiltBuildings()) {
            if (building.isWonder) Wonders.add(building);
            else if (building.specialistSlots != null) SpecialistBuildings.add(building);
            else Others.add(building);
        }

        if(!Wonders.isEmpty()) {
            Label label = new Label("Wonders", skin);
            label.setFontScale(1.5f);
            label.setColor(Color.GREEN);
            add(label).pad(5).row();
            for (Building building : Wonders)
                add(new Label(building.name, skin)).pad(5).row();
        }

        if(!SpecialistBuildings.isEmpty()) {
            Label label = new Label("Specialist Buildings", skin);
            label.setFontScale(1.5f);
            label.setColor(Color.GREEN);
            add(label).pad(5).row();
            for (Building building : SpecialistBuildings) {
                add(new Label(building.name, skin)).pad(5);
                Table specialists = new Table();
                specialists.row().size(20).pad(5);
                if (!cityInfo.population.buildingsSpecialists.containsKey(building.name))
                    cityInfo.population.buildingsSpecialists.put(building.name, new FullStats());
                FullStats currentBuildingSpecialists = cityInfo.population.buildingsSpecialists.get(building.name);
                for (int i = 0; i < building.specialistSlots.production; i++) {
                    specialists.add(getSpecialistIcon("StatIcons/populationBrown.png", building.name,
                            currentBuildingSpecialists.production > i, new FullStats() {{
                                production = 1;
                            }}));
                }
                for (int i = 0; i < building.specialistSlots.science; i++) {
                    specialists.add(getSpecialistIcon("StatIcons/populationBlue.png", building.name,
                            currentBuildingSpecialists.science > i, new FullStats() {{
                                science = 1;
                            }}));
                }
                for (int i = 0; i < building.specialistSlots.culture; i++) {
                    specialists.add(getSpecialistIcon("StatIcons/populationPurple.png", building.name,
                            currentBuildingSpecialists.culture > i, new FullStats() {{
                                culture = 1;
                            }}));
                }
                for (int i = 0; i < building.specialistSlots.gold; i++) {
                    specialists.add(getSpecialistIcon("StatIcons/populationYellow.png", building.name,
                            currentBuildingSpecialists.gold > i, new FullStats() {{
                                gold = 1;
                            }}));
                }
                add(specialists).row();
            }
        }

        if(!Others.isEmpty()) {
            Label label = new Label("Buildings", skin);
            label.setFontScale(1.5f);
            label.setColor(Color.GREEN);
            add(label).pad(5).row();
            for (Building building : Others)
                add(new Label(building.name, skin)).pad(5).row();
        }
        pack();
    }

}
