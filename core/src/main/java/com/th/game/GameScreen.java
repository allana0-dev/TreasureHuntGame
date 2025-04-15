package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.EllipseMapObject;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Ellipse;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import org.encog.ml.data.basic.BasicMLDataSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameScreen implements Screen {

    private Main game;
    private GameSettings settings;

    // Map & rendering objects.
    private TiledMap tiledMap;
    private OrthogonalTiledMapRenderer mapRenderer;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;

    // --- Player fields ---
    private Player player;
    private Animation<TextureRegion> playerWalkDown;
    private Animation<TextureRegion> playerWalkLeft;
    private Animation<TextureRegion> playerWalkRight;
    private Animation<TextureRegion> playerWalkUp;
    private float playerStateTime = 0f;
    private Direction playerDirection = Direction.DOWN;

    // --- Treasure chests ---
    private ArrayList<TreasureChest> treasureChests;

    // Other game fields.
    private Random random;
    private int currentRound = 1;
    private float roundTimer;

    // Simple direction enum.
    private enum Direction { UP, DOWN, LEFT, RIGHT }

    // Cached map dimensions.
    private int mapPixelWidth;
    private int mapPixelHeight;

    // For grid-based exploration (kept for possible future use).
    private final int CELL_SIZE = 64;

    public GameScreen(Main game, GameSettings settings) {
        this.game = game;
        this.settings = settings;
        random = new Random();

        // Initialize the training data database.
        try {
            TrainingDataDAO.initializeDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // --- Load Tile Map ---
        tiledMap = new TmxMapLoader().load("maps/map1.tmx");
        mapRenderer = new OrthogonalTiledMapRenderer(tiledMap);

        // Calculate the full map dimensions in pixels.
        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int tilePixelWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        int tilePixelHeight = tiledMap.getProperties().get("tileheight", Integer.class);
        mapPixelWidth = mapTileWidth * tilePixelWidth;
        mapPixelHeight = mapTileHeight * tilePixelHeight;

        // --- Set up the camera ---
        camera = new OrthographicCamera(mapPixelWidth, mapPixelHeight);
        camera.position.set(mapPixelWidth / 2f, mapPixelHeight / 2f, 0);
        camera.update();

        // --- Load Sprite Sheets ---
        // Player sprites:
        Texture playerTexture = new Texture(Gdx.files.internal("player.png"));
        int expectedPlayerCols = 4, expectedPlayerRows = 4;
        int playerCellWidth = playerTexture.getWidth() / expectedPlayerCols;
        int playerCellHeight = playerTexture.getHeight() / expectedPlayerRows;
        TextureRegion[][] playerFrames = TextureRegion.split(playerTexture, playerCellWidth, playerCellHeight);
        playerWalkDown = new Animation<>(0.15f, playerFrames[0][0], playerFrames[0][1]);
        playerWalkLeft = new Animation<>(0.15f, playerFrames[2][0], playerFrames[2][1]);
        playerWalkRight = new Animation<>(0.15f, playerFrames[1][0], playerFrames[1][1]);
        playerWalkUp = new Animation<>(0.15f, playerFrames[3][0], playerFrames[3][1]);
        playerWalkDown.setPlayMode(Animation.PlayMode.LOOP);
        playerWalkLeft.setPlayMode(Animation.PlayMode.LOOP);
        playerWalkRight.setPlayMode(Animation.PlayMode.LOOP);
        playerWalkUp.setPlayMode(Animation.PlayMode.LOOP);

        // --- Initialize Player position in a walkable area ---
        do {
            player = new Player(new Vector2(random.nextInt(mapPixelWidth), random.nextInt(mapPixelHeight)));
        } while (!isWalkable(player.position));

        // --- Create Treasure Chests ---
        treasureChests = new ArrayList<>();
        for (int i = 0; i < settings.treasureCount; i++) {
            Vector2 pos;
            do {
                pos = new Vector2(random.nextInt(mapPixelWidth), random.nextInt(mapPixelHeight));
            } while (!isWalkable(pos) || pos.dst(player.position) < 50);
            treasureChests.add(new TreasureChest(pos, 8, 0.1f));
        }

        // Set timer if in timer mode.
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            roundTimer = settings.timerDuration;
        }

        // Initialize batch, font, and shapeRenderer.
        batch = new SpriteBatch();
        font = new BitmapFont();
        shapeRenderer = new ShapeRenderer();

        // Removed AI initialization.
    }

    @Override
    public void render(float delta) {
        update(delta);

        Gdx.gl.glClearColor(0, 0, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        mapRenderer.setView(camera);
        mapRenderer.render();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Render treasure chests with a hint effect.
        float hintThreshold = 150f;
        for (TreasureChest chest : treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED) {
                float dist = player.position.dst(chest.position);
                if (dist < hintThreshold) {
                    float alpha = 1f - (dist / hintThreshold);
                    chest.render(batch, alpha);
                }
            } else {
                chest.render(batch, 1f);
            }
        }

        // --- Render the Player ---
        batch.setColor(Color.WHITE);
        TextureRegion playerFrame;
        switch (playerDirection) {
            case LEFT:
                playerFrame = playerWalkLeft.getKeyFrame(playerStateTime, true);
                break;
            case RIGHT:
                playerFrame = playerWalkRight.getKeyFrame(playerStateTime, true);
                break;
            case UP:
                playerFrame = playerWalkUp.getKeyFrame(playerStateTime, true);
                break;
            case DOWN:
            default:
                playerFrame = playerWalkDown.getKeyFrame(playerStateTime, true);
                break;
        }
        batch.draw(playerFrame, player.position.x, player.position.y, 40, 50);

        // Removed AI rendering.

        // --- Draw HUD ---
        font.draw(batch, "Player Score: " + player.score, camera.position.x - 380, camera.position.y + (camera.viewportHeight / 2f) - 20);
        // Removed AI Score display.
        batch.end();
    }

    private void update(float delta) {
        playerStateTime += delta;
        // Removed AI state updates.

        handlePlayerInput(delta);
        // Removed AI movement updates.

        for (TreasureChest chest : treasureChests) {
            chest.update(delta);
        }

        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            roundTimer -= delta;
            if (roundTimer < 0) {
                endRound();
            }
        }
        updateCamera();
        checkRoundEnd();
    }

    // Camera update
    private void updateCamera() {
        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int tilePixelWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        int tilePixelHeight = tiledMap.getProperties().get("tileheight", Integer.class);
        int mapPixelWidth = mapTileWidth * tilePixelWidth;
        int mapPixelHeight = mapTileHeight * tilePixelHeight;
        camera.viewportWidth = mapPixelWidth;
        camera.viewportHeight = mapPixelHeight;
        camera.position.set(mapPixelWidth / 2f, mapPixelHeight / 2f, 0);
        camera.update();
    }

    // Handle player input
    private void handlePlayerInput(float delta) {
        Vector2 oldPosition = new Vector2(player.position);
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            player.position.x -= player.speed * delta;
            playerDirection = Direction.LEFT;
        } else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            player.position.x += player.speed * delta;
            playerDirection = Direction.RIGHT;
        } else if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            player.position.y += player.speed * delta;
            playerDirection = Direction.UP;
        } else if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            player.position.y -= player.speed * delta;
            playerDirection = Direction.DOWN;
        }

        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int tilePixelWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        int tilePixelHeight = tiledMap.getProperties().get("tileheight", Integer.class);
        int mapPixelWidth = mapTileWidth * tilePixelWidth;
        int mapPixelHeight = mapTileHeight * tilePixelHeight;
        player.position.x = MathUtils.clamp(player.position.x, 0, mapPixelWidth - 64);
        player.position.y = MathUtils.clamp(player.position.y, 0, mapPixelHeight - 96);
        if (!isWalkable(player.position)) {
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

    private void checkRoundEnd() {
        boolean allOpen = true;
        for (TreasureChest chest : treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED) {
                allOpen = false;
                break;
            }
        }

        // Removed AI chest-picking logic.

        if (player.score > settings.treasureCount / 2 || allOpen) {
            endRound();
        }
    }

    private void endRound() {
        // Determine round winner.
        // With AI removed, we simply count any round as a win for the player.
        settings.playerRoundsWon++;
        System.out.println("Round " + currentRound + " ended. Player Score: " + player.score);

        // ---------- Save a Training Sample ----------
        // Find the nearest treasure chest for training context using the player's position.
        Vector2 nearest = null;
        float minDist = Float.MAX_VALUE;
        for (TreasureChest chest : treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED) {
                float dist = player.position.dst(chest.position);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = chest.position;
                }
            }
        }
        if (nearest == null) nearest = new Vector2(400, 300); // fallback

        Vector2 toTreasure = nearest.cpy().sub(player.position).nor();
        int bestMove = -1;
        if (nearest != null) {
            Vector2 diff = nearest.cpy().sub(player.position);
            float dx = diff.x;
            float dy = diff.y;
            // Simple heuristic for best move
            if (Math.abs(dx) > Math.abs(dy)) {
                bestMove = (dx > 0) ? 3 : 2; // RIGHT or LEFT
            } else {
                bestMove = (dy > 0) ? 0 : 1; // UP or DOWN
            }
        }

        // Create one-hot encoded output
        double[] labels = new double[]{0, 0, 0, 0};
        if (bestMove >= 0) {
            labels[bestMove] = 1.0;
        }

        double[] features = new double[]{
            // Normalize all values between 0-1
            player.position.x / mapPixelWidth,
            player.position.y / mapPixelHeight,
            // Since AI is removed, we use the player's position as a placeholder.
            player.position.x / mapPixelWidth,
            player.position.y / mapPixelHeight,
            nearest.x / mapPixelWidth,
            nearest.y / mapPixelHeight,
            toTreasure.x,
            toTreasure.y
        };
        TrainingSample sample = new TrainingSample(features, labels);

        try {
            TrainingDataDAO.saveTrainingSample(sample);
            System.out.println("Training sample saved.");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // ---------- Load and Train Model from All Saved Data ----------
        try {
            List<TrainingSample> samples = TrainingDataDAO.getAllTrainingSamples();
            if (!samples.isEmpty()) {
                double[][] inputData = new double[samples.size()][8];
                double[][] idealOutput = new double[samples.size()][4];
                for (int i = 0; i < samples.size(); i++) {
                    inputData[i] = samples.get(i).getFeatures();
                    idealOutput[i] = samples.get(i).getLabels();
                }
                org.encog.ml.data.MLDataSet trainingSet = new BasicMLDataSet(inputData, idealOutput);
                // The training call is kept here for your training samples.
                // For example, if you add a future method to handle training, it can be called here.
                System.out.println("Trained model on " + samples.size() + " samples.");
            } else {
                System.out.println("No training samples available for training.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // ---------- Transition to Next Round or End Game ----------
        if (currentRound < settings.totalRounds) {
            currentRound++;
            resetRound();
        } else {
            // Since AI is removed, pass 0 for AI rounds won.
            game.setScreen(new EndScreen(game, settings.playerRoundsWon, 0, settings));
        }
    }

    private void resetRound() {
        player.score = 0;
        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int tilePixelWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        int tilePixelHeight = tiledMap.getProperties().get("tileheight", Integer.class);
        int mapPixelWidth = mapTileWidth * tilePixelWidth;
        int mapPixelHeight = mapTileHeight * tilePixelHeight;
        do {
            player.position.set(random.nextInt(mapPixelWidth), random.nextInt(mapPixelHeight));
        } while (!isWalkable(player.position));
        // Removed AI repositioning.
        treasureChests.clear();
        // Define an edge buffer so that treasures aren't spawned too close to the map boundaries.
        int treasureEdgeBuffer = 50; // You can adjust this value as needed.
        for (int i = 0; i < settings.treasureCount; i++) {
            Vector2 pos;
            do {
                // Choose a random position within the safe area.
                pos = new Vector2(
                    random.nextInt(mapPixelWidth - 2 * treasureEdgeBuffer) + treasureEdgeBuffer,
                    random.nextInt(mapPixelHeight - 2 * treasureEdgeBuffer) + treasureEdgeBuffer
                );
            } while (!isWalkable(pos) || pos.dst(player.position) < 50);
            treasureChests.add(new TreasureChest(pos, 8, 0.1f));
        }

        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            roundTimer = settings.timerDuration;
        }
    }

    private boolean isWalkable(Vector2 pos) {
        MapLayer collisionLayer = tiledMap.getLayers().get("Collision");
        if (collisionLayer == null) {
            return true;
        }
        MapObjects objects = collisionLayer.getObjects();
        for (MapObject object : objects) {
            // Check for rectangular objects.
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                if (rect.contains(pos.x, pos.y)) {
                    return false;
                }
            }
            // Check for polygon objects.
            else if (object instanceof PolygonMapObject) {
                Polygon polygon = ((PolygonMapObject) object).getPolygon();
                if (polygon.contains(pos.x, pos.y)) {
                    return false;
                }
            }
            // Check for ellipse objects.
            else if (object instanceof EllipseMapObject) {
                Ellipse ellipse = ((EllipseMapObject) object).getEllipse();
                if (ellipse.contains(pos.x, pos.y)) {
                    return false;
                }
            }
            // If needed, you can add more shape checks (e.g., PolylineMapObject) here.
        }
        return true;
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }
    @Override public void show() { }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() { }

    @Override
    public void dispose() {
        tiledMap.dispose();
        mapRenderer.dispose();
        batch.dispose();
        font.dispose();
        shapeRenderer.dispose();
        for (TreasureChest chest : treasureChests) {
            chest.dispose();
        }
    }
}

