package com.th.game.extenders.gamescreen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.th.game.entities.TreasureChest;
import com.th.game.database.TrainingDataDAO;
import com.th.game.database.TreasureCollectionData;
import com.th.game.screens.GameScreen;
import com.th.game.entities.Landmark;
import com.th.game.util.Direction;

import java.sql.SQLException;
import java.util.Iterator;

public class PlayerInputExtender {
    private final GameScreen gameScreen;
    private final int mapPixelWidth;
    private final int mapPixelHeight;

    public PlayerInputExtender(GameScreen gameScreen) {
        this.gameScreen = gameScreen;

        // Cache map dimensions
        int mapTileWidth = gameScreen.getTiledMap().getProperties().get("width", Integer.class);
        int mapTileHeight = gameScreen.getTiledMap().getProperties().get("height", Integer.class);
        int tilePixelWidth = gameScreen.getTiledMap().getProperties().get("tilewidth", Integer.class);
        int tilePixelHeight = gameScreen.getTiledMap().getProperties().get("tileheight", Integer.class);

        this.mapPixelWidth = mapTileWidth * tilePixelWidth;
        this.mapPixelHeight = mapTileHeight * tilePixelHeight;
    }
    /**
     * Handles all player input processing including movement, treasure collection, and hint requests.
     * Only processes input when the game is not in countdown or showing round popup.
     *
     * @param delta Time since last frame in seconds
     */
    public void handleInput(float delta) {
        if (gameScreen.countdownActive || gameScreen.showingRoundPopup) {
            return;
        }

        processMovement(delta);
        processTreasureCollection();
        processHintRequest();
    }

    /**
     * Processes player movement based on keyboard input.
     * Handles directional movement, map bounds clamping, and walkability checks.
     *
     * @param delta Time since last frame in seconds
     */
    private void processMovement(float delta) {
        Vector2 oldPosition = new Vector2(gameScreen.player.position);

        // Handle directional movement
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            gameScreen.player.position.x -= gameScreen.player.speed * delta;
            gameScreen.playerDirection = Direction.LEFT;
        } else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            gameScreen.player.position.x += gameScreen.player.speed * delta;
            gameScreen.playerDirection = Direction.RIGHT;
        } else if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            gameScreen.player.position.y += gameScreen.player.speed * delta;
            gameScreen.playerDirection = Direction.UP;
        } else if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            gameScreen.player.position.y -= gameScreen.player.speed * delta;
            gameScreen.playerDirection = Direction.DOWN;
        }

        // Clamp position to map bounds (always apply, not just when moved)
        gameScreen.player.position.x = MathUtils.clamp(gameScreen.player.position.x, 0, mapPixelWidth - 64);
        gameScreen.player.position.y = MathUtils.clamp(gameScreen.player.position.y, 0, mapPixelHeight - 64);

        // Check for walkability
        if (!gameScreen.isWalkable(gameScreen.player.position)) {
            gameScreen.player.position.set(oldPosition);
        }
    }

    /**
     * Processes treasure collection when player presses the SPACE key.
     * Handles chest interaction, score updates, sound effects, data persistence,
     * AI notification, and landmark cleanup.
     */
    private void processTreasureCollection() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            for (TreasureChest chest : gameScreen.treasureChests) {
                if (chest.state == TreasureChest.ChestState.CLOSED &&
                    gameScreen.player.position.dst(chest.position) < 32) {

                    // Play collection sound and open chest
                    gameScreen.collectSound.play(0.8f);
                    chest.open();
                    gameScreen.player.score++;

                    // Store the treasure collection data with map name
                    try {
                        TreasureCollectionData collectionData = new TreasureCollectionData(
                            gameScreen.currentRound,
                            gameScreen.currentMapName,
                            new Vector2(chest.position),
                            new Vector2(gameScreen.player.position),
                            true  // collected by player
                        );
                        TrainingDataDAO.saveTreasureCollection(collectionData);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    // Notify AI that player collected a treasure
                    gameScreen.ai.notifyTreasureCollected(chest.position, true);

                    // Show the speed boost effect
                    gameScreen.showAISpeedBoostEffect();

                    Iterator<Landmark> iter = gameScreen.getLandmarks().iterator();
                    while (iter.hasNext()) {
                        Landmark lm = iter.next();
                        if (chest.position.dst(lm.position) < lm.radius) {
                            iter.remove();
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Processes hint requests when player presses the H key.
     * Manages hint cooldown, generation, display, and AI hint processing.
     */
    private void processHintRequest() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.H) &&
            gameScreen.hintAvailable &&
            gameScreen.hintEnabled) {

            gameScreen.currentHint = generateGlobalHint();
            if (!gameScreen.currentHint.isEmpty()) {
                gameScreen.hintVisible = true;
                gameScreen.hintSound.play(0.8f);
                gameScreen.currentHintDisplayTimer = 0f;
                gameScreen.hintAvailable = false;
                gameScreen.hintCooldown = gameScreen.HINT_COOLDOWN_DURATION;

                gameScreen.ai.processHint(gameScreen.currentHint, gameScreen.getLandmarks());
            }
        }
    }

    /**
     * Generates a global hint by examining all closed treasures and their proximity to landmarks.
     * Returns a meaningful hint for the first valid treasure-landmark combination found.
     *
     * @return A hint string directing players to treasures near landmarks, or a generic exploration message
     */
    private String generateGlobalHint() {
        // Loop over closed treasures and generate a hint if one is near a landmark
        for (TreasureChest chest : gameScreen.treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED) {
                String hint = generateHintForTreasure(chest);
                if (!hint.isEmpty()) {
                    return hint;
                }
            }
        }
        return "Explore the area for hidden treasures!";
    }

    /**
     * Generates a specific hint for a given treasure chest based on its proximity to landmarks.
     *
     * @param chest The treasure chest to generate a hint for
     * @return A hint string if the chest is near a landmark, empty string otherwise
     */
    private String generateHintForTreasure(TreasureChest chest) {
        for (Landmark landmark : gameScreen.getLandmarks()) {
            float distance = chest.position.dst(landmark.position);
            if (distance < landmark.radius) {
                return "A treasure is placed close to a " + landmark.name.replace("_", " ");
            }
        }
        return "";
    }
}
