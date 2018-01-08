package com.unciv.game.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.unciv.civinfo.CivilizationInfo;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;

import java.util.Collection;
import java.util.Collections;

public class CameraStageBaseScreen implements Screen {

    public UnCivGame game;
    public Stage stage;
    public Skin skin = new Skin(Gdx.files.internal("skin/flat-earth-ui.json"));
    static Batch batch = new SpriteBatch();

    public CameraStageBaseScreen() {
        this.game = UnCivGame.Current;
        stage = new Stage(new ExtendViewport(1000,600
        ),batch);// FitViewport(1000,600)
        Gdx.input.setInputProcessor(stage);
    }


    @Override
    public void show() {    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act();
        stage.draw();;
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width,height,true);
    }

    @Override
    public void pause() {    }

    @Override
    public void resume() {    }

    @Override
    public void hide() {    }

    @Override
    public void dispose() {    }

    private LinqCollection<String> tutorialTexts = new LinqCollection<String>();

    public void displayTutorials(String name, LinqCollection<String> texts){
        if(CivilizationInfo.current().tutorial.contains(name)) return;
        CivilizationInfo.current().tutorial.add(name);

        tutorialTexts.addAll(texts);
        if(!isTutorialShowing) displayTutorial();
    }

    boolean isTutorialShowing=false;

    public void displayTutorial(){
        isTutorialShowing=true;
        final Table tutorialTable = new Table().pad(10);
        tutorialTable.background(ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(new Color(0x101050cf)));
        Label label = new Label(tutorialTexts.get(0),skin);
        label.setFontScale(1.5f);
        label.setAlignment(Align.center);
        tutorialTexts.remove(0);
        tutorialTable.add(label).pad(10).row();
        TextButton button = new TextButton("Close",skin);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tutorialTable.remove();
                if(!tutorialTexts.isEmpty()) displayTutorial();
                else isTutorialShowing=false;
            }
        });
        tutorialTable.add(button).pad(10);
        tutorialTable.pack();
        tutorialTable.setPosition(stage.getWidth()/2-tutorialTable.getWidth()/2,
                stage.getHeight()/2-tutorialTable.getHeight()/2);
        stage.addActor(tutorialTable);
    }

}