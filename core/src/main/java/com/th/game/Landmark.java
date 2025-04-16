package com.th.game;

import com.badlogic.gdx.math.Vector2;

public class Landmark {
    public String name;
    public Vector2 position;
    public float radius;

    public Landmark(String name, Vector2 position, float radius) {
        this.name = name;
        this.position = position;
        this.radius = radius;
    }
}
