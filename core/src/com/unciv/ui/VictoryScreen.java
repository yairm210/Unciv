package com.unciv.ui;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.ui.utils.CameraStageBaseScreen;

public class VictoryScreen extends CameraStageBaseScreen{

    public VictoryScreen() {

        Table table = new Table();
        Label label = new Label("A resounding victory!",skin);
        label.setFontScale(2);

        table.add(label).pad(20).row();

        TextButton newGameButton = new TextButton("New game!",skin);
        newGameButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.startNewGame();
            }
        });
        table.add(newGameButton).pad(20).row();


        table.pack();
        table.setPosition((stage.getWidth()-table.getWidth())/2 , (stage.getHeight()-table.getHeight())/2 );

        stage.addActor(table);
    }


}
