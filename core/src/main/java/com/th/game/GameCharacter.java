package com.th.game;

import com.badlogic.gdx.math.Vector2;

/**
 * Base class for all characters in the game, providing common properties
 * such as position, movement speed, and score tracking.
 */
public class GameCharacter {

    /**
     * The world position of the character (bottom-left corner).
     */
    public Vector2 position;

    /**
     * The current score accumulated by this character.
     */
    public int score;

    /**
     * The movement speed of the character (units per second).
     */
    public float speed;

    /**
     * Creates a new GameCharacter at the specified position with the given speed.
     * Initializes the score to zero.
     *
     * @param position the starting world position of the character
     * @param speed    the movement speed in units per second
     */
    public GameCharacter(Vector2 position, float speed) {
        this.position = position;
        this.speed = speed;
        this.score = 0;
    }
}
