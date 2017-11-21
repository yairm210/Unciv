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

public class ImprovementPickerScreen extends CameraStageBaseScreen {
    TileImprovement SelectedImprovement;

    public ImprovementPickerScreen(final UnCivGame game, final com.unciv.civinfo.TileInfo tileInfo) {
        super(game);

        Table buttonTable = new Table();
        TextButton closeButton =new TextButton("Close", skin);
        closeButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new com.unciv.game.CityScreen(game));
                dispose();
            }
        });
//        closeButton.getLabel().setFontScale(0.7f);
        buttonTable.add(closeButton).width(stage.getWidth()/4);

        final Label improvementDescription = new Label("",skin);
        buttonTable.add(improvementDescription).width(stage.getWidth()/2).pad(5);
        improvementDescription.setFontScale(game.settings.labelScale);

        final TextButton pickImprovementButton = new TextButton("Pick improvement",skin);
        pickImprovementButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tileInfo.StartWorkingOnImprovement(SelectedImprovement);
                game.setWorldScreen();
                dispose();
            }
        });
//        pickImprovementButton.getLabel().setFontScale(0.7f);
        pickImprovementButton.setTouchable(Touchable.disabled);
        pickImprovementButton.setColor(Color.GRAY);
        buttonTable.add(pickImprovementButton).width(stage.getWidth()/4);

        Table buildingsTable = new Table();
        for(final TileImprovement improvement : GameBasics.TileImprovements.values()) {
            if(!tileInfo.CanBuildImprovement(improvement)) continue;
            TextButton TB = new TextButton(improvement.Name+"\r\n"+improvement.TurnsToBuild+" turns", skin);
            TB.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    SelectedImprovement = improvement;
                    pickImprovementButton.setTouchable(Touchable.enabled);
                    pickImprovementButton.setText("Construct "+improvement.Name);
                    pickImprovementButton.setColor(Color.WHITE);
                    improvementDescription.setText(improvement.GetDescription());
                }
            });
            buildingsTable.add(TB).pad(10);
            buildingsTable.row();
        }
        ScrollPane scrollPane = new ScrollPane(buildingsTable);
        scrollPane.setSize(stage.getWidth(),stage.getHeight()*0.9f);


        SplitPane splitPane = new SplitPane(scrollPane, buttonTable, true, skin);
        splitPane.setSplitAmount(0.9f);
        splitPane.setFillParent(true);
        stage.addActor(splitPane);
    }
}