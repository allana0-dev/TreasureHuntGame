// Update the TreasureCollectionData class
package com.th.game;

import com.badlogic.gdx.math.Vector2;

public class TreasureCollectionData {
    private int roundNumber;
    private String mapName;  // Added map name
    private Vector2 treasurePosition;
    private Vector2 collectorPosition;
    private boolean collectedByPlayer;  // true if player, false if AI
    private long timeStamp;

    public TreasureCollectionData(int roundNumber, String mapName, Vector2 treasurePosition,
                                  Vector2 collectorPosition, boolean collectedByPlayer) {
        this.roundNumber = roundNumber;
        this.mapName = mapName;
        this.treasurePosition = new Vector2(treasurePosition);
        this.collectorPosition = new Vector2(collectorPosition);
        this.collectedByPlayer = collectedByPlayer;
        this.timeStamp = System.currentTimeMillis();
    }

    // Getters
    public int getRoundNumber() { return roundNumber; }
    public String getMapName() { return mapName; }
    public Vector2 getTreasurePosition() { return treasurePosition; }
    public Vector2 getCollectorPosition() { return collectorPosition; }
    public boolean isCollectedByPlayer() { return collectedByPlayer; }
    public long getTimeStamp() { return timeStamp; }
}
