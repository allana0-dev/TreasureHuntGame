package com.th.game;

import com.badlogic.gdx.math.Vector2;

/**
 * Records data about a treasure collection event during gameplay.
 * Stores the round number, map name, treasure and collector positions,
 * whether the player collected it, and the timestamp of collection.
 */
public class TreasureCollectionData {

    /** The round number in which the treasure was collected. */
    private int roundNumber;

    /** The name of the map where the collection occurred. */
    private String mapName;

    /** The world coordinates of the treasure when collected. */
    private Vector2 treasurePosition;

    /** The world coordinates of the collector (player or AI). */
    private Vector2 collectorPosition;

    /** Flag indicating if the treasure was collected by the player (true) or AI (false). */
    private boolean collectedByPlayer;

    /** The system timestamp (milliseconds since epoch) when the collection happened. */
    private long timeStamp;

    /**
     * Constructs a new TreasureCollectionData instance.
     *
     * @param roundNumber         the round number during which the event occurred
     * @param mapName             the name of the map
     * @param treasurePosition    the position of the treasure at collection time
     * @param collectorPosition   the position of the collector at collection time
     * @param collectedByPlayer   true if collected by the player, false if by AI
     */
    public TreasureCollectionData(int roundNumber, String mapName, Vector2 treasurePosition,
                                  Vector2 collectorPosition, boolean collectedByPlayer) {
        this.roundNumber = roundNumber;
        this.mapName = mapName;
        this.treasurePosition = new Vector2(treasurePosition);
        this.collectorPosition = new Vector2(collectorPosition);
        this.collectedByPlayer = collectedByPlayer;
        this.timeStamp = System.currentTimeMillis();
    }

    /**
     * Returns the round number of the collection event.
     *
     * @return the round number
     */
    public int getRoundNumber() {
        return roundNumber;
    }

    /**
     * Returns the name of the map where the treasure was collected.
     *
     * @return the map name
     */
    public String getMapName() {
        return mapName;
    }

    /**
     * Returns the position of the treasure at the time of collection.
     *
     * @return a Vector2 representing the treasure coordinates
     */
    public Vector2 getTreasurePosition() {
        return treasurePosition;
    }

    /**
     * Returns the position of the collector (player or AI) at the time of collection.
     *
     * @return a Vector2 representing the collector coordinates
     */
    public Vector2 getCollectorPosition() {
        return collectorPosition;
    }

    /**
     * Indicates whether the treasure was collected by the player.
     *
     * @return true if collected by player, false if collected by AI
     */
    public boolean isCollectedByPlayer() {
        return collectedByPlayer;
    }

    /**
     * Returns the timestamp (in milliseconds since epoch) when the treasure was collected.
     *
     * @return the timestamp of collection
     */
    public long getTimeStamp() {
        return timeStamp;
    }
}

