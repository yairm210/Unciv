package com.unciv.game.pickerscreens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.civinfo.CityConstructions;
import com.unciv.civinfo.Unit;
import com.unciv.game.CityScreen;
import com.unciv.game.UnCivGame;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;

public class ConstructionPickerScreen extends PickerScreen {
    public String selectedProduction;

    TextButton getProductionButton(final String production, String buttonText,
                                   final String description, final String rightSideButtonText){
        TextButton TB = new TextButton(buttonText, skin);
        TB.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedProduction = production;
                rightSideButton.setTouchable(Touchable.enabled);
                rightSideButton.setText(rightSideButtonText);
                rightSideButton.setColor(Color.WHITE);
                descriptionLabel.setText(description);
            }
        });
        return TB;
    }

    public ConstructionPickerScreen(final UnCivGame game) {
        super(game);

        closeButton.clearListeners(); // Don't go back to the world screen, unlike the other picker screens!
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new CityScreen(game));
                dispose();
            }
        });

        rightSideButton.setText("Pick building");
        rightSideButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.civInfo.getCurrentCity().cityConstructions.currentConstruction = selectedProduction;
                game.civInfo.getCurrentCity().updateCityStats(); // Because maybe we set/removed the science or gold production options.
                game.setScreen(new CityScreen(game));
                dispose();
            }
        });
        rightSideButton.setTouchable(Touchable.disabled);
        rightSideButton.setColor(Color.GRAY);

        CityConstructions cityConstructions = game.civInfo.getCurrentCity().cityConstructions;
        VerticalGroup regularBuildings = new VerticalGroup().space(10),
                wonders = new VerticalGroup().space(10),
                units = new VerticalGroup().space(10),
                specials = new VerticalGroup().space(10);

        for(final Building building : GameBasics.Buildings.values()) {
            if(!building.isBuildable(cityConstructions)) continue;
            TextButton TB = getProductionButton(building.name,
                    building.name +"\r\n"+cityConstructions.turnsToConstruction(building.name)+" turns",
                    building.getDescription(true),
                    "Build "+building.name);
            if(building.isWonder) wonders.addActor(TB);
            else regularBuildings.addActor(TB);
        }

        for(Unit unit : GameBasics.Units.values()){
            if(!unit.isConstructable()) continue;
            units.addActor(getProductionButton(unit.name,
                    unit.name+"\r\n"+cityConstructions.turnsToConstruction(unit.name)+" turns",
                    unit.description, "Train "+unit.name));
        }

        if(game.civInfo.tech.isResearched("Education"))
            specials.addActor(getProductionButton("Science","Produce Science",
                    "Convert production to science at a rate of 4 to 1", "Produce Science"));

        if(game.civInfo.tech.isResearched("Currency"))
            specials.addActor(getProductionButton("Gold","Produce Gold",
                    "Convert production to gold at a rate of 4 to 1", "Produce Gold"));

        topTable.add(units);
        topTable.add(regularBuildings);
        topTable.add(wonders);
        topTable.add(specials);
    }

}