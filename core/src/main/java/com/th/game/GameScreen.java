package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.audio.Sound;
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
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Ellipse;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameScreen implements Screen {

    private Main game;
    private GameSettings settings;
    private String currentMapName;
    private boolean showingRoundPopup = false;
    private float roundPopupTimer = 0f;
    private final float ROUND_POPUP_DURATION = 2.5f; // Show popup for 2.5 seconds
    private BitmapFont largeFont; // For round popup text
    private boolean hintEnabled;


    //speedboost
    private boolean showingAISpeedBoost = false;
    private float speedBoostEffectTimer = 0f;
    private final float SPEED_BOOST_EFFECT_DURATION = 3.0f; // Show effect for 3 seconds
    private final Color SPEED_BOOST_COLOR = new Color(1f, 0.4f, 0.2f, 0.7f); // Orange/red glow
    private boolean pulseDirectionUp = true;
    private float pulseAlpha = 0.4f;
    private Texture speedBoostTexture;

    // Map & rendering objects.
    TiledMap tiledMap;
    private OrthogonalTiledMapRenderer mapRenderer;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;
    private int tileWidth;
    private int tileHeight;
    private float countdownTimer;
    private float hintTimer = 0f;
    private String currentHint = "";
    // Add these variables to GameScreen class
    private boolean countdownActive = false;
    private final float COUNTDOWN_DURATION = 3.0f; // 3 seconds countdown
    private int currentCountdownNumber = 3;
    private final float HINT_INTERVAL = 10f;
    private List<Landmark> landmarks = new ArrayList<>();
    // Add these variables to the GameScreen class
    private float hintDisplayDuration = 3.0f;
    private float currentHintDisplayTimer = 0f;
    private boolean hintVisible = false;
    // Add to GameScreen class
    private boolean hintButtonPressed = false;
    private float hintCooldown = 0f;
    private final float HINT_COOLDOWN_DURATION = 15f; // 15 seconds between hints
    private boolean hintAvailable = true;
    private Sound collectSound;
    private Music duringGameMusic;
    private Sound hintSound;
    private final List<Vector2> spawnPoints;

    // In the GameScreen class variables section, add or update these variables:
    private final float COUNTDOWN_NUMBER_DURATION = 1.0f; // Each number shows for 1 second
    private float countdownNumberTimer = 0f;
    private boolean gameStarted = false;



    private SmartAI ai;
    private Animation<TextureRegion> aiWalkDown;
    private Animation<TextureRegion> aiWalkLeft;
    private Animation<TextureRegion> aiWalkRight;
    private Animation<TextureRegion> aiWalkUp;
    private float aiStateTime = 0f;
    private Direction aiDirection = Direction.DOWN;
    private float aiMoveTimer = 0f;
    private final float aiMoveInterval = 0.5f; // Change direction every 0.5 seconds.
    private Direction aiCurrentDirection = Direction.DOWN; // Use your existing Direction enum.



    // --- Player fields ---
    private Player player;
    private Animation<TextureRegion> playerWalkDown;
    private Animation<TextureRegion> playerWalkLeft;
    private Animation<TextureRegion> playerWalkRight;
    private Animation<TextureRegion> playerWalkUp;
    private float playerStateTime = 0f;
    private Direction playerDirection = Direction.DOWN;

    // --- Treasure chests ---
    ArrayList<TreasureChest> treasureChests;

    // Define an enum for spawn positions
    private enum SpawnPosition {
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT,
        CENTRE,
        TOP_LEFT
    }
    private List<SpawnPosition> availableSpawnPositions;


    // Other game fields.
    private Random random;
    private int currentRound = 1;
    private float roundTimer;

    // Simple direction enum.

    // Cached map dimensions.
    private int mapPixelWidth;
    private int mapPixelHeight;

    public GameScreen(Main game, GameSettings settings) {
        this.game = game;
        this.settings = settings;
        random = new Random();


        this.hintEnabled = settings.hintsEnabled;
        showingRoundPopup = true;
        roundPopupTimer = 0f;
        countdownActive = false;
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            resetTimer();
        }

        // Initialize the training data database.
        try {
            TrainingDataDAO.initializeDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        MapManager.MapInfo selectedMap = null;
        if (settings.mapType == GameSettings.MapType.RANDOM) {
            selectedMap = MapManager.getRandomMap();
            currentMapName = selectedMap.getName();
        } else {
            currentMapName = settings.selectedMapName != null ? settings.selectedMapName : "Map 1";
            selectedMap = MapManager.getMapByName(currentMapName);
        }

        if (selectedMap == null) {
            selectedMap = MapManager.getRandomMap();
            currentMapName = selectedMap.getName();
        }

        tiledMap = new TmxMapLoader().load(selectedMap.getPath());

        loadAllLandmarksFromObjectGroups();


        // --- Load Tile Map ---
        mapRenderer = new OrthogonalTiledMapRenderer(tiledMap);

        // Calculate the full map dimensions in pixels.
        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int tilePixelWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        int tilePixelHeight = tiledMap.getProperties().get("tileheight", Integer.class);
        // Calculate tile dimensions.
        tileWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        tileHeight = tiledMap.getProperties().get("tileheight", Integer.class);

        mapPixelWidth = mapTileWidth * tilePixelWidth;
        mapPixelHeight = mapTileHeight * tilePixelHeight;

        camera = new OrthographicCamera(mapPixelWidth, mapPixelHeight);
        camera.position.set(mapPixelWidth / 2f, mapPixelHeight / 2f, 0);
        camera.update();

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

        // define the five canonical spawn tiles:
        spawnPoints = new ArrayList<>();

        createSpawnPositions();

        ai.scanWalkableAreas(this, tiledMap);





        // --- Create Treasure Chests ---
        treasureChests = new ArrayList<>();
        placeTreasuresScattered();

        // Set timer if in timer mode.
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            roundTimer = settings.timerDuration;
        }
        // --- Load AI Sprite Sheets ---
// --- Load AI Sprite Sheets with Correct Column Order ---
        Texture aiTexture = new Texture(Gdx.files.internal("ai.png"));
        int aiCols = 4; // There are 4 columns representing directions.
        int aiRows = 4; // Assume there are 4 rows (frames per animation).

// Split the texture into a 2D array of regions.
        TextureRegion[][] aiFrames = TextureRegion.split(aiTexture, aiTexture.getWidth() / aiCols, aiTexture.getHeight() / aiRows);

// Create arrays to hold the frames for each direction.
        TextureRegion[] aiWalkDownFrames = new TextureRegion[aiRows];
        TextureRegion[] aiWalkLeftFrames = new TextureRegion[aiRows];
        TextureRegion[] aiWalkUpFrames = new TextureRegion[aiRows];
        TextureRegion[] aiWalkRightFrames = new TextureRegion[aiRows];

// Extract each frame from the appropriate column for the four directions.
        for (int row = 0; row < aiRows; row++) {
            aiWalkDownFrames[row] = aiFrames[row][0];  // Column 0: walk down.
            aiWalkLeftFrames[row] = aiFrames[row][1];    // Column 1: walk left.
            aiWalkUpFrames[row] = aiFrames[row][2];      // Column 2: walk up.
            aiWalkRightFrames[row] = aiFrames[row][3];     // Column 3: walk right.
        }

// Create animations using the extracted frames.
        aiWalkDown = new Animation<>(0.15f, aiWalkDownFrames);
        aiWalkLeft = new Animation<>(0.15f, aiWalkLeftFrames);
        aiWalkUp = new Animation<>(0.15f, aiWalkUpFrames);
        aiWalkRight = new Animation<>(0.15f, aiWalkRightFrames);

// Set looping mode for smooth continuous animation.
        aiWalkDown.setPlayMode(Animation.PlayMode.LOOP);
        aiWalkLeft.setPlayMode(Animation.PlayMode.LOOP);
        aiWalkUp.setPlayMode(Animation.PlayMode.LOOP);
        aiWalkRight.setPlayMode(Animation.PlayMode.LOOP);



        // --- Initialize batch, font, and shapeRenderer ---
        batch = new SpriteBatch();
        font = new BitmapFont();
        shapeRenderer = new ShapeRenderer();
        // In the GameScreen constructor, after initializing the regular font, add:
        largeFont = new BitmapFont();
        largeFont.getData().setScale(2.0f); // Make it twice as large as the normal font

        // Removed AI initialization.
    }

    public TiledMap getTiledMap() {
        return tiledMap;
    }

    /**
     * Returns a random spawn point from the list
     * @return A randomly selected spawn point
     */
    private Vector2 getRandomSpawnPoint() {
        if (spawnPoints.isEmpty()) {
            // Fallback in case spawn points haven't been initialized
            return new Vector2(100, 100);
        }
        return spawnPoints.get(random.nextInt(spawnPoints.size()));
    }

    private void initializeSpawnPositions() {
        availableSpawnPositions = new ArrayList<>();
        availableSpawnPositions.add(SpawnPosition.TOP_RIGHT);
        availableSpawnPositions.add(SpawnPosition.BOTTOM_RIGHT);
        availableSpawnPositions.add(SpawnPosition.BOTTOM_LEFT);
        availableSpawnPositions.add(SpawnPosition.CENTRE);
        availableSpawnPositions.add(SpawnPosition.TOP_LEFT);
    }
    /**
     * Creates spawn positions for player and AI using one of the predefined positions
     * This is called from constructor and resetRound
     */
    private void createSpawnPositions() {
        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);

        // Calculate buffer from edges (15% of the way in from each side)
        int bufferX = (int)(mapTileWidth * 0.15);
        int bufferY = (int)(mapTileHeight * 0.15);

        // Initialize or reset available positions
        initializeSpawnPositions();

        // Randomly select position for player
        SpawnPosition playerPosition = getRandomSpawnPosition();

        // Create player spawn based on selected position
        Vector2 playerSpawn = createPositionVector(playerPosition, mapTileWidth, mapTileHeight, bufferX, bufferY);
        player = new Player(playerSpawn.cpy());

        // Get a different position for AI
        SpawnPosition aiPosition = getRandomSpawnPosition();

        // Create AI spawn based on selected position
        Vector2 aiSpawn = createPositionVector(aiPosition, mapTileWidth, mapTileHeight, bufferX, bufferY);
        ai = new SmartAI(aiSpawn.cpy(), currentMapName);
        ai.scanWalkableAreas(this, tiledMap);

    }

    /**
     * Selects a random spawn position and removes it from available positions
     * @return A randomly selected spawn position
     */
    private SpawnPosition getRandomSpawnPosition() {
        if (availableSpawnPositions.isEmpty()) {
            // If no positions left, reinitialize (shouldn't happen, but just in case)
            initializeSpawnPositions();
        }

        int index = random.nextInt(availableSpawnPositions.size());
        SpawnPosition position = availableSpawnPositions.get(index);
        availableSpawnPositions.remove(index);
        return position;
    }

    /**
     * Creates a Vector2 position based on the selected spawn position
     */
    private Vector2 createPositionVector(SpawnPosition position, int mapTileWidth, int mapTileHeight, int bufferX, int bufferY) {
        switch (position) {
            case TOP_RIGHT:
                return new Vector2(
                    (mapTileWidth - bufferX) * tileWidth - tileWidth/2,
                    (mapTileHeight - bufferY) * tileHeight - tileHeight/2);
            case BOTTOM_RIGHT:
                return new Vector2(
                    (mapTileWidth - bufferX) * tileWidth - tileWidth/2,
                    bufferY * tileHeight + tileHeight/2);
            case BOTTOM_LEFT:
                return new Vector2(
                    bufferX * tileWidth + tileWidth/2,
                    bufferY * tileHeight + tileHeight/2);
            case CENTRE:
                return new Vector2(
                    (mapTileWidth/2) * tileWidth,
                    (mapTileHeight/2) * tileHeight);
            case TOP_LEFT:
            default:
                return new Vector2(
                    bufferX * tileWidth + tileWidth/2,
                    (mapTileHeight - bufferY) * tileHeight - tileHeight/2);
        }
    }

    private void showAISpeedBoostEffect() {
        showingAISpeedBoost = true;
        speedBoostEffectTimer = 0f;
        pulseDirectionUp = true;
        pulseAlpha = 0.4f;
    }



    /**
     * Draws the AI's pathfinding visualization (for debugging)
     * Call this from render() after drawing everything else
     */
    private void drawPathVisualization() {
        if (ai == null) return;

        // Cast to our SmartAI implementation to access the path visualizer
        Array<Vector2> pathPoints = ((SmartAI)ai).getPathVisualizer();

        if (pathPoints != null && pathPoints.size >= 2) {
            // Set up shape renderer
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(1, 0, 0, 0.5f); // Red line with transparency

            // Draw path segments
            for (int i = 0; i < pathPoints.size - 1; i++) {
                Vector2 current = pathPoints.get(i);
                Vector2 next = pathPoints.get(i + 1);
                shapeRenderer.line(current, next);
            }

            shapeRenderer.end();

            // Draw points
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0, 1, 0, 0.5f); // Green points with transparency

            for (Vector2 point : pathPoints) {
                shapeRenderer.circle(point.x, point.y, 5);
            }

            shapeRenderer.end();
        }
    }

    private void placeTreasuresScattered() {
        treasureChests.clear();

        // Get map dimensions
        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int mapPixelWidth = mapTileWidth * tileWidth;
        int mapPixelHeight = mapTileHeight * tileHeight;

        // Define an edge buffer so treasures aren't spawned too close to edges
        int treasureEdgeBuffer = 50;

        // Divide the map into sections based on treasure count
        int gridSize = (int) Math.ceil(Math.sqrt(settings.treasureCount));
        int cellWidth = (mapPixelWidth - 2 * treasureEdgeBuffer) / gridSize;
        int cellHeight = (mapPixelHeight - 2 * treasureEdgeBuffer) / gridSize;

        // Keep track of placed treasures
        int placedTreasures = 0;

        // First try to place one treasure per grid cell
        List<Integer> cellIndices = new ArrayList<>();
        for (int i = 0; i < gridSize * gridSize; i++) {
            cellIndices.add(Integer.valueOf(i));
        }
        java.util.Collections.shuffle(cellIndices, random);

        for (int index : cellIndices) {
            if (placedTreasures >= settings.treasureCount) break;

            int gridX = index % gridSize;
            int gridY = index / gridSize;

            // Try multiple positions within each cell
            for (int attempt = 0; attempt < 10; attempt++) {
                int x = treasureEdgeBuffer + gridX * cellWidth + random.nextInt(cellWidth);
                int y = treasureEdgeBuffer + gridY * cellHeight + random.nextInt(cellHeight);
                Vector2 pos = new Vector2(x, y);

                // Make sure it's walkable and not too close to player or other treasures
                if (isWalkable(pos) && pos.dst(player.position) > 100 && !tooCloseToOtherTreasures(pos, 100)) {
                    treasureChests.add(new TreasureChest(pos, 8, 0.1f));
                    placedTreasures++;
                    break;
                }
            }
        }



        // If we still need more treasures, place them randomly across the map
        while (placedTreasures < settings.treasureCount) {
            Vector2 pos = new Vector2(
                random.nextInt(mapPixelWidth - 2 * treasureEdgeBuffer) + treasureEdgeBuffer,
                random.nextInt(mapPixelHeight - 2 * treasureEdgeBuffer) + treasureEdgeBuffer
            );

            if (isWalkable(pos) && pos.dst(player.position) > 75 && !tooCloseToOtherTreasures(pos, 75)) {
                treasureChests.add(new TreasureChest(pos, 8, 0.1f));
                placedTreasures++;
            }
        }

    }

    // Helper method to check if a position is too close to existing treasures
    private boolean tooCloseToOtherTreasures(Vector2 pos, float minDistance) {
        for (TreasureChest chest : treasureChests) {
            if (pos.dst(chest.position) < minDistance) {
                return true;
            }
        }
        return false;
    }

    // Resets the countdown timer to the initial value from game settings.
    private void resetTimer() {
        this.countdownTimer = settings.timerDuration;
    }

    /**
     * Returns the current hint text
     * @return The current hint text
     */
    public String getCurrentHint() {
        return currentHint;
    }

    /**
     * Returns the list of landmarks in the map
     * @return The list of landmarks
     */
    public List<Landmark> getLandmarks() {
        return landmarks;
    }


    private void loadAllLandmarksFromObjectGroups() {
        // Clear any previously loaded landmarks
        landmarks.clear();

        // Iterate over all map layers
        for (MapLayer layer : tiledMap.getLayers()) {
            MapObjects objects = layer.getObjects();
            if (objects.getCount() > 0) {  // or objects.size if available
                for (MapObject object : objects) {
                    float x = Float.parseFloat(object.getProperties().get("x").toString());
                    float y = Float.parseFloat(object.getProperties().get("y").toString());
                    String name = object.getName();
                    float radius = 100f; // You can adjust or calculate the radius accordingly
                    landmarks.add(new Landmark(name, new Vector2(x, y), radius));
                }
            }
        }
    }



    @Override
    public void render(float delta) {
        // Update countdown timer only when in TIMER mode.
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            countdownTimer -= delta;
            if (countdownTimer <= 0) {
                countdownTimer = 0;
                // Handle end of the round when time runs out.
                endRound();
            }
        }

        // Update game logic (e.g., player movement, AI behavior, collision detection, etc.)
        update(delta);

        // Clear the screen.
        Gdx.gl.glClearColor(0, 0, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Update the camera and render the map.
        camera.update();
        mapRenderer.setView(camera);
        mapRenderer.render();

        // Begin sprite batch rendering.
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

        // Draw "YOU" label above player
        font.setColor(Color.GREEN);
        font.draw(batch, "YOU", player.position.x + 10, player.position.y + 70);
        font.setColor(Color.WHITE);

        // --- Render the AI with a label above ---
        TextureRegion aiFrame;
        switch (aiDirection) {
            case LEFT:
                aiFrame = aiWalkLeft.getKeyFrame(aiStateTime, true);
                break;
            case RIGHT:
                aiFrame = aiWalkRight.getKeyFrame(aiStateTime, true);
                break;
            case UP:
                aiFrame = aiWalkUp.getKeyFrame(aiStateTime, true);
                break;
            case DOWN:
            default:
                aiFrame = aiWalkDown.getKeyFrame(aiStateTime, true);
                break;
        }
        batch.draw(aiFrame, ai.position.x, ai.position.y, 64, 64);

        // Draw "AI" label above the AI
        font.setColor(Color.RED);
        font.draw(batch, "AI", ai.position.x + 25, ai.position.y + 80);
        font.setColor(Color.WHITE);

        // Update pulse effect for speed boost
        if (showingAISpeedBoost) {
            speedBoostEffectTimer += delta;
            if (pulseDirectionUp) {
                pulseAlpha += delta * 1.5f;
                if (pulseAlpha >= 0.8f) {
                    pulseAlpha = 0.8f;
                    pulseDirectionUp = false;
                }
            } else {
                pulseAlpha -= delta * 1.5f;
                if (pulseAlpha <= 0.4f) {
                    pulseAlpha = 0.4f;
                    pulseDirectionUp = true;
                }
            }

            // End effect after duration
            if (speedBoostEffectTimer >= SPEED_BOOST_EFFECT_DURATION) {
                showingAISpeedBoost = false;
            } else {
                // Calculate fade out near the end
                float alpha = 1.0f;
                if (speedBoostEffectTimer > SPEED_BOOST_EFFECT_DURATION - 0.5f) {
                    alpha = (SPEED_BOOST_EFFECT_DURATION - speedBoostEffectTimer) / 0.5f;
                }

                // Save the batch color
                Color prevColor = batch.getColor().cpy();

                // Draw the glow effect
                Color effectColor = new Color(SPEED_BOOST_COLOR);
                effectColor.a = pulseAlpha * alpha;
                batch.setColor(effectColor);

                // Draw outer glow (larger circle)
                float glowSize = 90f;
                batch.draw(
                    getSpeedBoostTexture(),
                    ai.position.x + 32 - glowSize/2,
                    ai.position.y + 32 - glowSize/2,
                    glowSize, glowSize
                );

                // Draw speed boost text
                font.setColor(1f, 0.8f, 0.2f, alpha); // Golden yellow text
                GlyphLayout layout = new GlyphLayout(font, "SPEED BOOST!");
                font.draw(batch, "SPEED BOOST!",
                    ai.position.x + 32 - layout.width/2,
                    ai.position.y + 95);

                // Restore previous color
                batch.setColor(prevColor);
                font.setColor(Color.WHITE);
            }
        }

        // --- Draw Heads Up Display (HUD) ---
        font.draw(batch, "Player Score: " + player.score, camera.position.x - 380, camera.position.y + (camera.viewportHeight / 2f) - 20);
        font.draw(batch, "AI Score: " + ai.score, camera.position.x - 380, camera.position.y + (camera.viewportHeight / 2f) - 40);

        font.draw(batch, "Round: " + currentRound + "/" + settings.totalRounds,
            camera.position.x + 250, camera.position.y + (camera.viewportHeight / 2f) - 40);

        // Draw round start popup if active
        if (showingRoundPopup) {
            // Update the popup timer
            roundPopupTimer += delta;
            if (roundPopupTimer >= ROUND_POPUP_DURATION) {
                showingRoundPopup = false;
                // Start the countdown as soon as the round popup is done
                countdownActive = true;
                countdownNumberTimer = 0f;
                currentCountdownNumber = 3;
            }

            // Calculate fade effect for smooth appearance/disappearance
            float alpha = 1.0f;
            if (roundPopupTimer < 0.5f) {
                // Fade in
                alpha = roundPopupTimer / 0.5f;
            } else if (roundPopupTimer > ROUND_POPUP_DURATION - 0.5f) {
                // Fade out
                alpha = (ROUND_POPUP_DURATION - roundPopupTimer) / 0.5f;
            }

            // Save current color
            Color prevColor = largeFont.getColor();

            // Draw semi-transparent black background for popup
            batch.end(); // End the sprite batch to switch to shape renderer

            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0, 0, 0, 0.7f * alpha);

            // Draw background rectangle centered on screen
            float boxWidth = 400;
            float boxHeight = 100;
            shapeRenderer.rect(
                camera.position.x - boxWidth/2,
                camera.position.y - boxHeight/2,
                boxWidth, boxHeight
            );

            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);

            // Start the sprite batch again to draw text
            batch.begin();

            // Draw round announcement text
            largeFont.setColor(1, 1, 1, alpha); // White text with fade effect
            String roundText = "ROUND " + currentRound;
            float textWidth = new GlyphLayout(largeFont, roundText).width; // Measure text width

            largeFont.draw(batch, roundText,
                camera.position.x - textWidth / 2, // Center text horizontally
                camera.position.y + 10  // Slight offset from center for better appearance
            );

            // Reset font color
            largeFont.setColor(prevColor);
        }

        // Draw countdown when active (place this after the round popup code)
        if (countdownActive) {
            // Update the countdown timer
            countdownNumberTimer += delta;
            if (countdownNumberTimer >= COUNTDOWN_NUMBER_DURATION) {
                // Move to next number or finish countdown
                countdownNumberTimer = 0f;
                currentCountdownNumber--;

                if (currentCountdownNumber < 0) {
                    // Countdown finished
                    countdownActive = false;
                    gameStarted = true;
                }
            }

            // Calculate fade effect for smooth appearance/disappearance
            float alpha = 1.0f;
            if (countdownNumberTimer < 0.2f) {
                // Fade in
                alpha = countdownNumberTimer / 0.2f;
            } else if (countdownNumberTimer > COUNTDOWN_NUMBER_DURATION - 0.2f) {
                // Fade out
                alpha = (COUNTDOWN_NUMBER_DURATION - countdownNumberTimer) / 0.2f;
            }

            // Save current color
            Color prevColor = largeFont.getColor();

            // Draw semi-transparent black background for popup
            batch.end(); // End the sprite batch to switch to shape renderer

            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0, 0, 0, 0.7f * alpha);

            // Draw background rectangle centered on screen instead of circle
            float boxWidth = 150;
            float boxHeight = 150;
            shapeRenderer.rect(
                camera.position.x - boxWidth/2,
                camera.position.y - boxHeight/2,
                boxWidth, boxHeight
            );

            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);

            // Start the sprite batch again to draw text
            batch.begin();

            // Draw countdown text
            largeFont.setColor(1, 1, 0, alpha); // Yellow text with fade effect
            String countdownText = currentCountdownNumber > 0 ?
                String.valueOf(currentCountdownNumber) : "GO!";

            GlyphLayout layout = new GlyphLayout(largeFont, countdownText);
            largeFont.draw(batch, countdownText,
                camera.position.x - layout.width / 2, // Center text horizontally
                camera.position.y + layout.height / 2  // Center text vertically
            );

            // Reset font color
            largeFont.setColor(prevColor);
        }
        if(hintEnabled){
            if (hintAvailable) {
                font.draw(batch, "Press H for Hint", camera.position.x - 380, camera.position.y + (camera.viewportHeight / 2f) - 60);
            } else {
                font.draw(batch, "Hint available in: " + (int)hintCooldown, camera.position.x - 380, camera.position.y + (camera.viewportHeight / 2f) - 60);
            }
        }


        if (hintVisible && !currentHint.isEmpty()) {
            float alpha = 1.0f;
            // Fade calculations...
            Color prevColor = font.getColor();
            font.setColor(prevColor.r, prevColor.g, prevColor.b, alpha);
            // Center the hint text
            font.draw(batch, currentHint, camera.position.x - 200, camera.position.y + (camera.viewportHeight / 2f) - 20);
            font.setColor(prevColor);
        }

        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            int timeLeft = (int) countdownTimer;
            font.draw(batch, "Time Left: " + timeLeft, camera.position.x + 250, camera.position.y + (camera.viewportHeight / 2f) - 20);
        }

        batch.end();
    }

    private Texture getSpeedBoostTexture() {
        if (speedBoostTexture == null) {
            final int size = 128;                        // larger for smoother edges
            Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
            pixmap.setBlending(Pixmap.Blending.SourceOver);

            float center = size * 0.5f;
            float sigma  = size * 0.25f;                 // controls the width of the glow
            float twoSigmaSq = 2 * sigma * sigma;

            // pre‑compute color channels
            float r = 1f, g = 0.5f, b = 0.2f;

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = x - center;
                    float dy = y - center;
                    float distSq = dx*dx + dy*dy;

                    // Gaussian formula: exp(−d²/(2σ²))
                    float alpha = (float)Math.exp(-distSq / twoSigmaSq);

                    // drop very faint pixels
                    if (alpha < 0.01f) continue;

                    pixmap.setColor(r, g, b, alpha);
                    pixmap.drawPixel(x, y);
                }
            }

            speedBoostTexture = new Texture(pixmap);
            pixmap.dispose();
        }
        return speedBoostTexture;
    }

    private String generateHintForTreasure(TreasureChest chest) {
        for (Landmark landmark : landmarks) {
            // Check if the treasure is within a set distance from the landmark.
            float distance = chest.position.dst(landmark.position);
            if (distance < landmark.radius) {
                // Replace underscores with spaces to improve readability.
                return "A treasure is placed close to a " + landmark.name.replace("_", " ");
            }
        }
        return "";
    }

    /**
     * Updated update method to integrate with new AI logic
     * Replace your existing update method with this one
     */
    private void update(float delta) {


        // Only allow player input and AI movement if countdown is not active
        if (!countdownActive) {
            // Update animation times
            playerStateTime += delta;
            aiStateTime += delta;
            // Handle player input
            handlePlayerInput(delta);

            // Update AI
            updateAI(delta);

            // Keep the hint display timing logic
            if (hintVisible) {
                currentHintDisplayTimer += delta;
                if (currentHintDisplayTimer >= hintDisplayDuration) {
                    // Hide the hint after display duration
                    hintVisible = false;
                }
            }

            // Keep the hint cooldown logic
            if (!hintAvailable) {
                hintCooldown -= delta;
                if (hintCooldown <= 0) {
                    hintAvailable = true;
                    hintCooldown = 0;
                }
            }


            // Update treasure states
            for (TreasureChest chest : treasureChests) {
                chest.update(delta);
            }

            // Update the camera and check round conditions
            updateCamera();
            checkRoundEnd();
        }

    }

    private String generateGlobalHint() {
        // Loop over closed treasures and generate a hint if one is near a landmark.
        for (TreasureChest chest : treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED) {
                String hint = generateHintForTreasure(chest);
                if (!hint.isEmpty()) {
                    return hint;
                }
            }
        }
        return "Explore the area for hidden treasures!";
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
    /**
     * Modification to handlePlayerInput to properly notify the AI when player collects a treasure
     * Update your existing handlePlayerInput method to include this logic
     */
    private void handlePlayerInput(float delta) {
        if (countdownActive || showingRoundPopup) {
            return;
        }
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

        player.position.x = MathUtils.clamp(player.position.x, 0, mapPixelWidth - 64);
        player.position.y = MathUtils.clamp(player.position.y, 0, mapPixelHeight - 64);
        if (!isWalkable(player.position)) {
            player.position.set(oldPosition);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            for (TreasureChest chest : treasureChests) {
                if (chest.state == TreasureChest.ChestState.CLOSED &&
                    player.position.dst(chest.position) < 32) {
                    collectSound.play(0.8f);
                    chest.open();
                    player.score++;

                    // Store the treasure collection data with map name
                    try {
                        TreasureCollectionData collectionData = new TreasureCollectionData(
                            currentRound,
                            currentMapName,
                            new Vector2(chest.position),
                            new Vector2(player.position),
                            true  // collected by player
                        );
                        TrainingDataDAO.saveTreasureCollection(collectionData);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    // Notify AI that player collected a treasure
                    ai.notifyTreasureCollected(chest.position, true);
                    // Show the speed boost effect
                    showAISpeedBoostEffect();

                    // Remove associated landmark hints
                    Iterator<Landmark> iter = landmarks.iterator();
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

        // Handle hint button

        if (Gdx.input.isKeyJustPressed(Input.Keys.H) && hintAvailable && hintEnabled) {
            currentHint = generateGlobalHint();
            if (!currentHint.isEmpty()) {
                hintVisible = true;
                hintSound.play(0.8f);
                currentHintDisplayTimer = 0f;
                hintAvailable = false;
                hintCooldown = HINT_COOLDOWN_DURATION;

                ai.processHint(currentHint, landmarks);

            }
        }
    }
    // Modify the updateAI method in GameScreen.java

    /**
     * Example of how to update the updateAI method in GameScreen to work with the new AI
     * Replace your existing updateAI method with this one
     */
    private void updateAI(float delta) {
        if (countdownActive || showingRoundPopup) {
            return; // Skip AI updates entirely during countdown
        }
        ai.update(delta, this);

        // Set animation direction based on AI's current direction
        aiDirection = ai.getCurrentDirection();

        // Check for treasure collection
        for (TreasureChest chest : treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED &&
                ai.position.dst(chest.position) < 32) {
                collectSound.play(0.8f);
                chest.open();
                ai.score++;

                // Notify the AI that a treasure was collected by AI
                ai.notifyTreasureCollected(chest.position, false);

                // Store the treasure collection data
                try {
                    TreasureCollectionData collectionData = new TreasureCollectionData(
                        currentRound,
                        currentMapName,
                        new Vector2(chest.position),
                        new Vector2(ai.position),
                        false  // collected by AI
                    );
                    TrainingDataDAO.saveTreasureCollection(collectionData);
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                // Remove associated landmark hints
                Iterator<Landmark> iter = landmarks.iterator();
                while (iter.hasNext()) {
                    Landmark lm = iter.next();
                    if (chest.position.dst(lm.position) < lm.radius) {
                        System.out.println("Removing landmark hint: " + lm.name + " (treasure collected by AI)");
                        iter.remove();
                        break;
                    }
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

        if (player.score > settings.treasureCount / 2 ||
            ai.score > settings.treasureCount / 2 ||
            allOpen) {
            endRound();
        }
    }

    private void endRound() {
        // Determine round winner.
        settings.playerRoundScores.add(Integer.valueOf(player.score));
        settings.aiRoundScores.add(Integer.valueOf(ai.score));
        if (ai.score > player.score) {
            settings.aiRoundsWon++;
        } else if (player.score > ai.score) {
            settings.playerRoundsWon++;
        }
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


        // Check for early win based on mathematically derived winning threshold.
        if (settings.playerRoundsWon > settings.totalRounds / 2 || settings.aiRoundsWon > settings.totalRounds / 2) {
            game.setScreen(new EndScreen(game, settings.playerRoundsWon, settings.aiRoundsWon, settings));
            return;
        }

        // Transition to the next round if rounds remain.
        if (currentRound < settings.totalRounds) {
            currentRound++;
            resetRound();
        } else {
            game.setScreen(new EndScreen(game, settings.playerRoundsWon, settings.aiRoundsWon, settings));
        }
    }

    /**
     * Example of how to modify the resetRound method to work with the new AI
     * Update your resetRound method to include this logic
     */
// Then update resetRound() to use these spawn points
    private void resetRound() {
        player.score = 0;
        ai.score = 0;

        // Reset hint system
        currentHint = "";
        hintTimer = 0f;
        hintVisible = false;
        currentHintDisplayTimer = 0f;
        hintAvailable = true;
        hintCooldown = 0f;
        showingRoundPopup = true;
        roundPopupTimer = 0f;
        countdownActive = false; // Change to false - we'll start countdown after round popup

        createSpawnPositions();

        // Reload all landmarks from the map
        loadAllLandmarksFromObjectGroups();

        // Rescan the map for the AI
        ai.scanWalkableAreas(this, tiledMap);

        // Place new treasure chests
        placeTreasuresScattered();

        // Reset round timer
        resetTimer();
        showingRoundPopup = true;
        roundPopupTimer = 0f;
    }
    boolean isWalkable(Vector2 pos) {
        // --- 1. Check if position is within map bounds ---
        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int mapPixelWidth = mapTileWidth * tileWidth;
        int mapPixelHeight = mapTileHeight * tileHeight;

        if (pos.x < 0 || pos.y < 0 || pos.x >= mapPixelWidth || pos.y >= mapPixelHeight) {
            return false; // Out of map bounds
        }

        // --- 2. Check if we're on at least one walkable layer ---
        String[] walkableLayers = {"Green Patch", "Grass", "Path"};
        boolean onWalkableLayer = false;

        for (String layerName : walkableLayers) {
            MapLayer layerRaw = tiledMap.getLayers().get(layerName);
            if (layerRaw instanceof TiledMapTileLayer) {
                TiledMapTileLayer layer = (TiledMapTileLayer) layerRaw;
                int tileX = (int)(pos.x / tileWidth);
                int tileY = (int)(pos.y / tileHeight);
                TiledMapTileLayer.Cell cell = layer.getCell(tileX, tileY);

                if (cell != null && cell.getTile() != null && cell.getTile().getId() != 0) {
                    onWalkableLayer = true;
                    break;
                }
            }
        }

        if (!onWalkableLayer) {
            return false; // Not on any walkable layer
        }

        // --- 3. Check if we're on any collidable layer ---
        for (int i = 0; i < tiledMap.getLayers().size(); i++) {
            MapLayer layer = tiledMap.getLayers().get(i);
            if (layer instanceof TiledMapTileLayer &&
                Boolean.TRUE.equals(layer.getProperties().get("collidable"))) {

                TiledMapTileLayer tileLayer = (TiledMapTileLayer) layer;
                int tileX = (int)(pos.x / tileWidth);
                int tileY = (int)(pos.y / tileHeight);
                TiledMapTileLayer.Cell cell = tileLayer.getCell(tileX, tileY);

                if (cell != null && cell.getTile() != null && cell.getTile().getId() != 0) {
                    return false; // On a collidable tile
                }
            }
        }

        // --- 4. Check collision objects ---
        MapLayer collisionLayer = tiledMap.getLayers().get("Collision");
        if (collisionLayer != null) {
            MapObjects objects = collisionLayer.getObjects();
            for (MapObject object : objects) {
                if (object instanceof RectangleMapObject) {
                    Rectangle rect = ((RectangleMapObject) object).getRectangle();
                    if (rect.contains(pos.x, pos.y)) {
                        return false;
                    }
                } else if (object instanceof PolygonMapObject) {
                    Polygon polygon = ((PolygonMapObject) object).getPolygon();
                    if (polygon.contains(pos.x, pos.y)) {
                        return false;
                    }
                } else if (object instanceof EllipseMapObject) {
                    Ellipse ellipse = ((EllipseMapObject) object).getEllipse();
                    if (ellipse.contains(pos.x, pos.y)) {
                        return false;
                    }
                }
            }
        }


        // All checks passed
        return true;
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }
    @Override public void show() {
        collectSound = Gdx.audio.newSound(Gdx.files.internal("sfx/collecttreasure.ogg"));
        hintSound = Gdx.audio.newSound(Gdx.files.internal("sfx/hint.ogg"));
        duringGameMusic = Gdx.audio.newMusic(
            Gdx.files.internal("music/duringgamemusic.ogg")
        );
        duringGameMusic.setLooping(true);
        duringGameMusic.setVolume(0.5f);  // adjust to taste
        duringGameMusic.play();

    }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() {

        if (duringGameMusic != null && duringGameMusic.isPlaying()) {
            duringGameMusic.dispose();
        }
    }

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
        if (duringGameMusic != null) {
            duringGameMusic.dispose();
        }
        if (speedBoostTexture != null) {
            speedBoostTexture.dispose();
        }

    }
}
