package com.th.game.extenders.gamescreen;
import com.badlogic.gdx.math.Vector2;
import com.th.game.entities.TreasureChest;
import com.th.game.screens.GameScreen;

/**
 * Handles end-of-round processing: updates scores, logs training data, and transitions to the next round or end screen.
 */
public class RoundEndExtender {
    private final GameScreen gameScreen;

    public RoundEndExtender(GameScreen gameScreen) {
        this.gameScreen = gameScreen;
    }

    /**
     * Ends the current round, saves training data, and determines whether to proceed to the next round or end the game.
     * Updates scores, determines winners, and prepares transition to the next game state.
     */
    public void endRound() {
        gameScreen.settings.playerRoundScores.add(Integer.valueOf(gameScreen.player.score));
        gameScreen.settings.aiRoundScores.add(Integer.valueOf(gameScreen.ai.score));
        if (gameScreen.ai.score > gameScreen.player.score) {
            gameScreen.settings.aiRoundsWon++;
        } else if (gameScreen.player.score > gameScreen.ai.score) {
            gameScreen.settings.playerRoundsWon++;
        }

        Vector2 nearest = null;
        float minDist = Float.MAX_VALUE;
        for (TreasureChest chest : gameScreen.treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED) {
                float dist = gameScreen.player.position.dst(chest.position);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = chest.position;
                }
            }
        }
        if (nearest == null) nearest = new Vector2(400, 300); // fallback

        Vector2 toTreasure = nearest.cpy().sub(gameScreen.player.position).nor();
        int bestMove = -1;
        if (nearest != null) {
            Vector2 diff = nearest.cpy().sub(gameScreen.player.position);
            float dx = diff.x;
            float dy = diff.y;
            // Simple heuristic for best move
            if (Math.abs(dx) > Math.abs(dy)) {
                bestMove = (dx > 0) ? 3 : 2; // RIGHT or LEFT
            } else {
                bestMove = (dy > 0) ? 0 : 1; // UP or DOWN
            }
        }

        double[] labels = new double[]{0, 0, 0, 0};
        if (bestMove >= 0) {
            labels[bestMove] = 1.0;
        }

        double[] features = new double[]{
            gameScreen.player.position.x / gameScreen.mapPixelWidth,
            gameScreen.player.position.y / gameScreen.mapPixelHeight,
            gameScreen.player.position.x / gameScreen.mapPixelWidth,
            gameScreen.player.position.y / gameScreen.mapPixelHeight,
            nearest.x / gameScreen.mapPixelWidth,
            nearest.y / gameScreen.mapPixelHeight,
            toTreasure.x,
            toTreasure.y
        };

        if (gameScreen.settings.playerRoundsWon > gameScreen.settings.totalRounds / 2 ||
            gameScreen.settings.aiRoundsWon > gameScreen.settings.totalRounds / 2) {
            gameScreen.showEndScreen();
            return;
        }

        if (gameScreen.currentRound < gameScreen.settings.totalRounds) {
            gameScreen.currentRound++;
            resetRound();
        } else {
            gameScreen.showEndScreen();
        }
    }
    /**
     * Resets all necessary game elements for the next round.
     * This includes resetting scores, hint system, creating new spawn positions,
     * reloading landmarks, rescanning walkable areas for AI, and placing new treasures.
     */
    private void resetRound() {
        gameScreen.player.score = 0;
        gameScreen.ai.score = 0;
        gameScreen.currentHint = "";
        gameScreen.hintTimer = 0f;
        gameScreen.hintVisible = false;
        gameScreen.currentHintDisplayTimer = 0f;
        gameScreen.hintAvailable = true;
        gameScreen.hintCooldown = 0f;
        gameScreen.showingRoundPopup = true;
        gameScreen.roundPopupTimer = 0f;
        gameScreen.countdownActive = false;
        gameScreen.gameStarted = false;
        gameScreen.createSpawnPositions();
        gameScreen.loadAllLandmarksFromObjectGroups();
        gameScreen.ai.scanWalkableAreas(gameScreen, gameScreen.getTiledMap());
        gameScreen.placeTreasuresScattered();
        gameScreen.resetTimer();
        gameScreen.showingRoundPopup = true;
        gameScreen.roundPopupTimer = 0f;
    }
}
