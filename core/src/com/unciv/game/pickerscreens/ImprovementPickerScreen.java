package com.unciv.game.pickerscreens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.game.CameraStageBaseScreen;
import com.unciv.game.UnCivGame;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.TileImprovement;

public class ImprovementPickerScreen extends PickerScreen {
    TileImprovement SelectedImprovement;

    public ImprovementPickerScreen(final UnCivGame game, final com.unciv.civinfo.TileInfo tileInfo) {
        super(game);

        rightSideButton.setText("Pick improvement");

        rightSideButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tileInfo.StartWorkingOnImprovement(SelectedImprovement);
                game.setWorldScreen();
                dispose();
            }
        });

        rightSideButton.setTouchable(Touchable.disabled);
        rightSideButton.setColor(Color.GRAY);

        for(final TileImprovement improvement : GameBasics.TileImprovements.values()) {
            if(!tileInfo.CanBuildImprovement(improvement)) continue;
            TextButton TB = new TextButton(improvement.Name+"\r\n"+improvement.TurnsToBuild+" turns", skin);
            TB.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    SelectedImprovement = improvement;
                    rightSideButton.setTouchable(Touchable.enabled);
                    rightSideButton.setText("Construct "+improvement.Name);
                    rightSideButton.setColor(Color.WHITE);
                    descriptionLabel.setText(improvement.GetDescription());
                }
            });
            topTable.add(TB).pad(10);
            topTable.row();
        }
    }
}