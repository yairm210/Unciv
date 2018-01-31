package com.unciv.ui.worldscreen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.civilization.Notification;
import com.unciv.ui.utils.CameraStageBaseScreen;
import com.unciv.ui.utils.ImageGetter;

public class NotificationsScroll extends ScrollPane {

    Table notificationsTable = new Table();
    final WorldScreen worldScreen;

    public NotificationsScroll(WorldScreen worldScreen) {
        super(null);
        this.worldScreen = worldScreen;
        setWidget(notificationsTable);
    }

    void update() {
        notificationsTable.clearChildren();
        for (final Notification notification : CivilizationInfo.current().notifications) {
            Label label = new Label(notification.text, CameraStageBaseScreen.skin);
            label.setColor(Color.WHITE);
            label.setFontScale(1.2f);
            Table minitable = new Table();

            minitable.background(ImageGetter.getDrawable("skin/civTableBackground.png")
                    .tint(new Color(0x004085bf)));
            minitable.add(label).pad(5);

            if(notification.location!=null){
                minitable.addListener(new ClickListener(){
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        worldScreen.tileMapHolder.setCenterPosition(notification.location);
                    }
                });
            }

            notificationsTable.add(minitable).pad(5);
            notificationsTable.row();
        }
        notificationsTable.pack();

        setSize(worldScreen.stage.getWidth() / 3,
                Math.min(notificationsTable.getHeight(),worldScreen.stage.getHeight() / 3));
    }

}
