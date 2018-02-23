package com.unciv.logic.civilization;

import com.badlogic.gdx.math.Vector2;

public class Notification{
    public String text;
    public Vector2 location;

    Notification(){}

    public Notification(String text, Vector2 location) {
        this.text = text;
        this.location = location;
    }
}
