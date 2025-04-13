package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public class GameScreenHelper {

    // Dependencies and game elements.
    private TiledMap tiledMap;
    private int mapPixelWidth, mapPixelHeight;
    private final int CELL_SIZE;
    private Random random;
    private Player player;
    private AIPlayer ai;
    private List<TreasureChest> treasureChests;
    private GameSettings settings;

    // Walkability function passed in from GameScreen.
    private Predicate<Vector2> isWalkableFn;

    // Round management.
    private int currentRound;
    private float roundTimer;

    // End-round listener.
    public interface EndRoundListener {
        void onRoundEnd();
    }
    private EndRoundListener endRoundListener;

    public void setEndRoundListener(EndRoundListener listener) {
        this.endRoundListener = listener;
    }

    /**
     * Constructor that receives all required objects and a Predicate to check walkability.
     */
    public GameScreenHelper(TiledMap tiledMap, int cellSize, Random random,
                            Player player, AIPlayer ai, List<TreasureChest> treasureChests,
                            GameSettings settings, int mapPixelWidth, int mapPixelHeight,
                            Predicate<Vector2> isWalkableFn) {
        this.tiledMap = tiledMap;
        this.CELL_SIZE = cellSize;
        this.random = random;
        this.player = player;
        this.ai = ai;
        this.treasureChests = treasureChests;
        this.settings = settings;
        this.mapPixelWidth = mapPixelWidth;
        this.mapPixelHeight = mapPixelHeight;
        this.isWalkableFn = isWalkableFn;
        currentRound = 1;
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            roundTimer = settings.timerDuration;
        }
    }

    // Called each frame to update game logic.
    public void update(float delta) {
        handlePlayerInput(delta);
        // Update treasure chests.
        for (TreasureChest chest : treasureChests) {
            chest.update(delta);
        }
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            roundTimer -= delta;
            if (roundTimer < 0) {
                endRound();
            }
        }
        checkRoundEnd();
    }

    // Update the camera based on current map dimensions.
    public void updateCamera(OrthographicCamera camera) {
        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int tilePixelWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        int tilePixelHeight = tiledMap.getProperties().get("tileheight", Integer.class);
        int mpw = mapTileWidth * tilePixelWidth;
        int mph = mapTileHeight * tilePixelHeight;
        camera.viewportWidth = mpw;
        camera.viewportHeight = mph;
        camera.position.set(mpw / 2f, mph / 2f, 0);
        camera.update();
    }

    // Handles player input (movement and chest interaction).
    public void handlePlayerInput(float delta) {
        Vector2 oldPosition = new Vector2(player.position);
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            player.position.x -= player.speed * delta;
        } else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            player.position.x += player.speed * delta;
        } else if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            player.position.y += player.speed * delta;
        } else if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            player.position.y -= player.speed * delta;
        }
        player.position.x = MathUtils.clamp(player.position.x, 0, mapPixelWidth - 64);
        player.position.y = MathUtils.clamp(player.position.y, 0, mapPixelHeight - 96);
        if (!isWalkableFn.test(player.position)) {
            player.position.set(oldPosition);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            for (TreasureChest chest : treasureChests) {
                if (chest.state == TreasureChest.ChestState.CLOSED &&
                    player.position.dst(chest.position) < 32) {
                    chest.open();
                    player.score++;
                }
            }
        }
    }

    // Checks if the round should end.
    public void checkRoundEnd() {
        boolean allOpen = true;
        for (TreasureChest chest : treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED) {
                allOpen = false;
                break;
            }
        }
        for (TreasureChest chest : treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED &&
                ai.position.dst(chest.position) < 32f) {
                chest.open();
                ai.score++;
            }
        }
        if (player.score > settings.treasureCount / 2 ||
            ai.score > settings.treasureCount / 2 || allOpen) {
            endRound();
        }
    }

    // Ends the round (saving a training sample, training the model, etc.).
    public void endRound() {
        Vector2 nearest = null;
        float minDist = Float.MAX_VALUE;
        for (TreasureChest chest : treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED) {
                float dist = ai.position.dst(chest.position);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = chest.position;
                }
            }
        }
        if (nearest == null)
            nearest = new Vector2(400, 300); // fallback
        Vector2 toTreasure = nearest.cpy().sub(ai.position).nor();
        int bestMove = -1;
        if (nearest != null) {
            Vector2 diff = nearest.cpy().sub(ai.position);
            float dx = diff.x;
            float dy = diff.y;
            bestMove = (Math.abs(dx) > Math.abs(dy)) ? ((dx > 0) ? 3 : 2) : ((dy > 0) ? 0 : 1);
        }
        double[] labels = new double[]{0, 0, 0, 0};
        if (bestMove >= 0) {
            labels[bestMove] = 1.0;
        }
        double[] features = new double[]{
            player.position.x / (double) mapPixelWidth,
            player.position.y / (double) mapPixelHeight,
            ai.position.x / (double) mapPixelWidth,
            ai.position.y / (double) mapPixelHeight,
            nearest.x / (double) mapPixelWidth,
            nearest.y / (double) mapPixelHeight,
            toTreasure.x,
            toTreasure.y
        };
        TrainingSample sample = new TrainingSample(features, labels);
        try {
            TrainingDataDAO.saveTrainingSample(sample);
            System.out.println("Training sample saved.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        // (Omitted: Model training logic for brevity.)

        if (currentRound < settings.totalRounds) {
            currentRound++;
            resetRound();
        } else {
            if (endRoundListener != null) {
                endRoundListener.onRoundEnd();
            }
        }
    }

    // Resets the round by reinitializing scores and recreating treasure chests.
    public void resetRound() {
        player.score = 0;
        ai.score = 0;
        do {
            player.position.set(random.nextInt(mapPixelWidth), random.nextInt(mapPixelHeight));
        } while (!isWalkableFn.test(player.position));
        do {
            ai.position.set(random.nextInt(mapPixelWidth), random.nextInt(mapPixelHeight));
        } while (!isWalkableFn.test(ai.position));
        treasureChests.clear();
        for (int i = 0; i < settings.treasureCount; i++) {
            Vector2 pos;
            do {
                pos = new Vector2(random.nextInt(mapPixelWidth), random.nextInt(mapPixelHeight));
            } while (!isWalkableFn.test(pos) || pos.dst(player.position) < 50 || pos.dst(ai.position) < 50);
            treasureChests.add(new TreasureChest(pos, 8, 0.1f));
        }
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            roundTimer = settings.timerDuration;
        }
    }
}


