package com.th.game;

import com.badlogic.gdx.math.Vector2;

public class AIPlayer {
    public Vector2 position;
    public int score;
    public float speed = 150f; // Default AI movement speed

    public AIPlayer(Vector2 startPos) {
        this.position = startPos;
        this.score = 0;
    }

    // Default update method that can be overridden by subclasses
    public void update(float delta, GameScreen gameScreen) {
        // Base implementation does nothing
    }

    // Default method to get current direction
    public Direction getCurrentDirection() {
        return Direction.DOWN; // Default direction
    }
}
