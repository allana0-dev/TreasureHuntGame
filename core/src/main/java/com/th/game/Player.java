package com.th.game;

import com.badlogic.gdx.math.Vector2;

public class Player {
    public Vector2 position;
    public float speed;
    public int score;

    public Player(Vector2 startPosition) {
        this.position = startPosition;
        this.speed = 200;
        this.score = 0;
    }
}

