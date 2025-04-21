package com.th.game.util.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents configuration settings for the game, including mode, map selection,
 * treasure count, timing, rounds, and score tracking.
 */
public class GameSettings {
    /** Enumeration of possible game modes. */
    public enum GameMode {
        TIMER,
        FIRST_TO_HALF
    }

    /** Enumeration of how the map is chosen: stored preset or random. */
    public enum MapType {
        STORED,
        RANDOM
    }

    public boolean hintsEnabled;


    /** The type of map selection for the game. */
    public MapType mapType;
    /** Name of the selected map when using STORED mapType. */
    public String selectedMapName;

    /** Number of treasures to place each round. */
    public int treasureCount;
    /** The game mode for this session. */
    public GameMode gameMode;
    /** Duration of the round timer in seconds (used in TIMER mode). */
    public float timerDuration;
    /** Total number of rounds to play. */
    public int totalRounds;

    /** Rounds won by the player so far. */
    public int playerRoundsWon;
    /** Rounds won by the AI so far. */
    public int aiRoundsWon;

    /** Per-round score history for the player. */
    public List<Integer> playerRoundScores;
    /** Per-round score history for the AI. */
    public List<Integer> aiRoundScores;

    /**
     * Constructs default GameSettings and initializes score histories.
     */
    public GameSettings() {
        playerRoundScores = new ArrayList<>();
        aiRoundScores     = new ArrayList<>();
        // default hints on
        hintsEnabled      = true;
    }
}
