package com.th.game;

import com.badlogic.gdx.math.Vector2;

/**
 * Represents a landmark used for hint generation in the game.
 * A treasure within the specified radius of this landmark may trigger a hint.
 */
public class Landmark {

    /**
     * The identifier for this landmark, typically matching the map object's name.
     */
    public String name;

    /**
     * The world position (in pixels) of the landmark's center.
     */
    public Vector2 position;

    /**
     * The radius (in pixels) around the landmark used to detect nearby treasures.
     */
    public float radius;

    /**
     * Constructs a new Landmark with the given name, position, and detection radius.
     *
     * @param name     the display name of the landmark
     * @param position the world coordinates of the landmark
     * @param radius   the distance within which treasures are considered 'near' this landmark
     */
    public Landmark(String name, Vector2 position, float radius) {
        this.name = name;
        this.position = position;
        this.radius = radius;
    }
}
