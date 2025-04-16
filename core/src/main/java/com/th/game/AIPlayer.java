package com.th.game;

import com.badlogic.gdx.math.Vector2;
import java.util.Random;

public class AIPlayer extends GameCharacter {
    private Vector2 target;
    private Random random;

    public AIPlayer(Vector2 position) {
        super(position, 60);  // AI speed
        random = new Random();
        target = null;
    }

    public void setTarget(Vector2 target) {
        this.target = target;
    }
}
