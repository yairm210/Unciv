package com.unciv.ui.pickerscreens;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.models.gamebasics.Unit;
import com.unciv.models.gamebasics.GameBasics;

public class GreatPersonPickerScreen extends PickerScreen{
    Unit theChosenOne;
    CivilizationInfo civInfo;

    public GreatPersonPickerScreen(){
        rightSideButton.setText("Choose a free great person");
        for(final Unit unit : GameBasics.Units.linqValues()){
            if(!unit.name.startsWith("Great")) continue;
            TextButton button = new TextButton(unit.name,skin);
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    theChosenOne=unit;
                    pick(unit.name);
                }
            });
            topTable.add(button).pad(10);
        }
        rightSideButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                civInfo.placeUnitNearTile(civInfo.cities.get(0).cityLocation, theChosenOne.name);
            }
        });
    }
}
