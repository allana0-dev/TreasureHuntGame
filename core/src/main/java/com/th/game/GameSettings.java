package com.th.game;

public class GameSettings {
    public enum GameMode {
        TIMER, FIRST_TO_HALF
    }

    public MapType mapType; // e.g., STORED or RANDOM
    public String selectedMapName; // store the name of the selected map when using stored maps.

    public static enum MapType {
        STORED, RANDOM
    }
    // Settings that apply to every round
    public int treasureCount;
    public GameMode gameMode;
    public float timerDuration; // if <= 0, then First-to-Half mode is used
    public int totalRounds;

    // Overall score counters (accumulated over rounds)
    public int playerRoundsWon;
    public int aiRoundsWon;
}

