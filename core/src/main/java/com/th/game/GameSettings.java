package com.th.game;

import java.util.ArrayList;
import java.util.List;

public class GameSettings {
    public enum GameMode {
        TIMER, FIRST_TO_HALF
    }

    public MapType mapType;
    public String selectedMapName;

    public static enum MapType {
        STORED, RANDOM
    }

    // Settings that apply to every round
    public int treasureCount;
    public GameMode gameMode;
    public float timerDuration;
    public int totalRounds;

    // Overall score counters
    public int playerRoundsWon;
    public int aiRoundsWon;

    // Lists to track round-by-round scores
    public List<Integer> playerRoundScores;
    public List<Integer> aiRoundScores;

    // Constructor to initialize lists
    public GameSettings() {
        playerRoundScores = new ArrayList<>();
        aiRoundScores = new ArrayList<>();
    }
}
