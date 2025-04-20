package com.th.game.entities;

import com.badlogic.gdx.math.Vector2;

/**
 * Represents the player character in the game.
 * Inherits common character behavior from {@link GameCharacter} and
 * initializes with a predefined movement speed.
 */
public class Player extends GameCharacter {

    /**
     * Constructs a new Player at the specified starting position.
     * Uses a default movement speed of 200 units.
     *
     * @param startPosition the initial world position of the player
     */
    public Player(Vector2 startPosition) {
        super(startPosition, 200);
    }

}


