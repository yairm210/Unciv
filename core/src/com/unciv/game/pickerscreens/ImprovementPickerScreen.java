package com.unciv.game.pickerscreens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.civinfo.CivilizationInfo;
import com.unciv.civinfo.TileInfo;
import com.unciv.game.UnCivGame;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.TileImprovement;

public class ImprovementPickerScreen extends PickerScreen {
    String SelectedImprovement;
    int TurnsToImprovement;

    public ImprovementPickerScreen(final UnCivGame game, final TileInfo tileInfo) {
        super(game);

        rightSideButton.setText("Pick improvement");

        rightSideButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tileInfo.startWorkingOnImprovement(SelectedImprovement, TurnsToImprovement);
                game.setWorldScreen();
                dispose();
            }
        });

        rightSideButton.setTouchable(Touchable.disabled);
        rightSideButton.setColor(Color.GRAY);

        VerticalGroup regularImprovements = new VerticalGroup();
        regularImprovements.space(10);
        for(final TileImprovement improvement : GameBasics.TileImprovements.values()) {
            if(!tileInfo.canBuildImprovement(improvement) || improvement.name.equals(tileInfo.improvement)) continue;
            int turnsToBuild = improvement.turnsToBuild;
            if(CivilizationInfo.current().getBuildingUniques().contains("WorkerConstruction")) turnsToBuild= (int) Math.round(0.75*turnsToBuild);
            TextButton TB = new TextButton(improvement.name +"\r\n"+turnsToBuild +" turns", skin);
            final int finalTurnsToBuild = turnsToBuild;
            TB.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    SelectedImprovement = improvement.name;
                    TurnsToImprovement = finalTurnsToBuild;
                    rightSideButton.setTouchable(Touchable.enabled);
                    rightSideButton.setText(improvement.name);
                    rightSideButton.setColor(Color.WHITE);
                    descriptionLabel.setText(improvement.getDescription());
                }
            });
            regularImprovements.addActor(TB);
        }
        topTable.add(regularImprovements);
    }
}