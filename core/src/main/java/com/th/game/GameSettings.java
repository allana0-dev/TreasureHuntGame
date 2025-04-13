package com.th.game;

public class GameSettings {
    public enum GameMode {
        TIMER, FIRST_TO_HALF
    }

    public enum MapType {
        STORED, RANDOM
    }

    // Settings that apply to every round
    public int treasureCount;
    public GameMode gameMode;
    public float timerDuration; // if <= 0, then First-to-Half mode is used
    public int totalRounds;
    public MapType mapType;

    // Overall score counters (accumulated over rounds)
    public int playerRoundsWon;
    public int aiRoundsWon;
}

