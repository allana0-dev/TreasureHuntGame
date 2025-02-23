package com.th.game;

import com.badlogic.gdx.math.Vector2;

public class Treasure {
    public Vector2 position;
    public boolean collected;

    public Treasure(Vector2 position) {
        this.position = position;
        this.collected = false;
    }
}
