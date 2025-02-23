package com.th.game;

public class GameSettings {
    public enum GameMode {
        TIMER, FIRST_TO_HALF
    }

    public int treasureCount;
    public GameMode gameMode;
    public float timerDuration; // only used in Timer Mode
}
