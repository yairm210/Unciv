package com.unciv.game.pickerscreens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.civinfo.CityBuildings;
import com.unciv.game.CityScreen;
import com.unciv.game.UnCivGame;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;

public class BuildingPickerScreen extends PickerScreen {
    Building selectedBuilding;

    public BuildingPickerScreen(final UnCivGame game) {
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
                game.civInfo.getCurrentCity().cityBuildings.currentBuilding = selectedBuilding.name;
                game.setScreen(new CityScreen(game));
                dispose();
            }
        });
        rightSideButton.setTouchable(Touchable.disabled);
        rightSideButton.setColor(Color.GRAY);

        CityBuildings cityBuildings = game.civInfo.getCurrentCity().cityBuildings;
        for(final Building building : GameBasics.Buildings.values()) {
            if(!cityBuildings.canBuild(building)) continue;
            TextButton TB = new TextButton(building.name +"\r\n"+cityBuildings.turnsToBuilding(building.name)+" turns", skin);
            TB.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedBuilding = building;
                    rightSideButton.setTouchable(Touchable.enabled);
                    rightSideButton.setText("Build "+building.name);
                    rightSideButton.setColor(Color.WHITE);
                    descriptionLabel.setText(building.getDescription());
                }
            });
            topTable.add(TB).pad(10);
            topTable.row();
        }
    }
}