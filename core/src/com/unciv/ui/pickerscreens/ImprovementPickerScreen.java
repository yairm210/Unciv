package com.unciv.ui.pickerscreens;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.map.TileInfo;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.TileImprovement;

public class ImprovementPickerScreen extends PickerScreen {
    private TileImprovement SelectedImprovement;

    public ImprovementPickerScreen(final TileInfo tileInfo) {
        final CivilizationInfo civInfo = game.gameInfo.getPlayerCivilization();

        rightSideButton.setText("Pick improvement");
        rightSideButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tileInfo.startWorkingOnImprovement(SelectedImprovement,civInfo);
                game.setWorldScreen();
                dispose();
            }
        });

        VerticalGroup regularImprovements = new VerticalGroup();
        regularImprovements.space(10);
        for(final TileImprovement improvement : GameBasics.TileImprovements.values()) {
            if(!tileInfo.canBuildImprovement(improvement,civInfo) || improvement.name.equals(tileInfo.improvement)) continue;
            TextButton TB = new TextButton(improvement.name +"\r\n"+improvement.getTurnsToBuild(civInfo)+" turns", skin);

            TB.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    SelectedImprovement = improvement;
                    pick(improvement.name);
                    descriptionLabel.setText(improvement.getDescription());
                }
            });
            regularImprovements.addActor(TB);
        }
        topTable.add(regularImprovements);
    }
}