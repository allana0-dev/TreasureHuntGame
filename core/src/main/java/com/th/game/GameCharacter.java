package com.th.game;

import com.badlogic.gdx.math.Vector2;

public class GameCharacter {
    public Vector2 position;
    public int score;
    public float speed;

    public GameCharacter(Vector2 position, float speed) {
        this.position = position;
        this.speed = speed;
        this.score = 0;
    }
}
