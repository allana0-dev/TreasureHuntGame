package com.th.game.screens;
// gdx core + audio + graphics + utils
import com.badlogic.gdx.*;
import com.badlogic.gdx.audio.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.maps.*;
import com.badlogic.gdx.maps.objects.*;
import com.badlogic.gdx.maps.tiled.*;
import com.badlogic.gdx.maps.tiled.renderers.*;
import com.badlogic.gdx.math.*;
import java.util.*;
import java.sql.SQLException;
// project packages
import com.th.game.*;
import com.th.game.ai.*;
import com.th.game.database.*;
import com.th.game.entities.*;
import com.th.game.util.*;
import com.th.game.util.map.MapManager;
import com.th.game.util.settings.GameSettings;
import com.th.game.extenders.gamescreen.*;

/**
 * Main gameplay screen: loads resources, runs the game loop (input, AI, treasures), renders frames, and manages lifecycle.
 */
public class GameScreen implements Screen {

    /**
     * Core game dependencies and basic settings.
     */
    private Main game;
    public GameSettings settings;
    public String currentMapName;

    /**
     * Round pop‑up display settings.
     */
    public boolean showingRoundPopup = false;
    public float roundPopupTimer = 0f;
    private final float ROUND_POPUP_DURATION = 2.5f; // Show popup for 2.5 seconds
    public BitmapFont largeFont; // For round popup text
    public boolean hintEnabled;

    /**
     * AI speed boost visual effect settings.
     */
    public boolean showingAISpeedBoost = false;
    public float speedBoostEffectTimer = 0f;
    public final float SPEED_BOOST_EFFECT_DURATION = 3.0f; // Show effect for 3 seconds
    public final Color SPEED_BOOST_COLOR = new Color(1f, 0.4f, 0.2f, 0.7f); // Orange/red glow
    public boolean pulseDirectionUp = true;
    public float pulseAlpha = 0.4f;
    private Texture speedBoostTexture;

    /**
     * Map, camera, and rendering objects.
     */
    TiledMap tiledMap;
    public OrthogonalTiledMapRenderer mapRenderer;
    public OrthographicCamera camera;
    public SpriteBatch batch;
    public BitmapFont font;
    public ShapeRenderer shapeRenderer;
    private int tileWidth;
    private int tileHeight;

    /**
     * Countdown and hint system state and timing.
     */
    public float countdownTimer;
    public float hintTimer = 0f;
    public String currentHint = "";
    public boolean countdownActive = false;
    private final float COUNTDOWN_DURATION = 3.0f;
    public int currentCountdownNumber = 3;
    public final float COUNTDOWN_NUMBER_DURATION = 1.0f; // Each number shows for 1 second
    public float countdownNumberTimer = 0f;
    private final float HINT_INTERVAL = 10f;
    private float hintDisplayDuration = 3.0f;
    public float currentHintDisplayTimer = 0f;
    public boolean hintVisible = false;
    private boolean hintButtonPressed = false;
    public float hintCooldown = 0f;
    public final float HINT_COOLDOWN_DURATION = 15f; // 15 seconds between hints
    public boolean hintAvailable = true;
    private List<Landmark> landmarks = new ArrayList<>();

    /**
     * Audio assets for collection, gameplay music, and hints.
     */
    public Sound collectSound;
    private Music duringGameMusic;
    public Sound hintSound;

    /**
     * Spawn point management.
     */
    private final List<Vector2> spawnPoints;
    private List<SpawnPosition> availableSpawnPositions;

    /**
     * Countdown completion flag.
     */
    public boolean gameStarted = false;

    /**
     * AI agent and animation state.
     */
    public SmartAI ai;
    public Animation<TextureRegion> aiWalkDown;
    public Animation<TextureRegion> aiWalkLeft;
    public Animation<TextureRegion> aiWalkRight;
    public Animation<TextureRegion> aiWalkUp;
    public float aiStateTime = 0f;
    public Direction aiDirection = Direction.DOWN;

    /**
     * Player character and animation state.
     */
    public Player player;
    public Animation<TextureRegion> playerWalkDown;
    public Animation<TextureRegion> playerWalkLeft;
    public Animation<TextureRegion> playerWalkRight;
    public Animation<TextureRegion> playerWalkUp;
    public float playerStateTime = 0f;
    public Direction playerDirection = Direction.DOWN;

    /**
     * Treasure chest tracking.
     */
    public ArrayList<TreasureChest> treasureChests;

    /**
     * Round progression, randomization, and timers.
     */
    private Random random;
    public int currentRound = 1;
    private float roundTimer;

    /**
     * Map dimensions in pixels.
     */
    public int mapPixelWidth;
    public int mapPixelHeight;

    /**
     * Modular extenders for rendering, input, treasure logic, and round end.
     */
    private RenderExtender renderExtender;
    private PlayerInputExtender playerInputExtender;
    private TreasureExtender treasureExtender;
    private RoundEndExtender roundEndExtender;


    public GameScreen(Main game, GameSettings settings) {
        // Store references to the main game instance and settings
        this.game = game;
        this.settings = settings;
        random = new Random();

        // Initialize the render, treasure, and round-end extenders
        renderExtender = new RenderExtender(this);
        treasureExtender = new TreasureExtender(this);
        roundEndExtender = new RoundEndExtender(this);

        // Enable hints if configured in settings
        this.hintEnabled = settings.hintsEnabled;

        // Show the round-start popup initially
        showingRoundPopup = true;
        roundPopupTimer = 0f;
        countdownActive = false;

        // If in timer mode, set up the timer
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            resetTimer();
        }

        // Initialize the training data database (for AI behavior)
        try {
            TrainingDataDAO.initializeDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Select the map based on settings (random or specific)
        MapManager.MapInfo selectedMap = null;
        if (settings.mapType == GameSettings.MapType.RANDOM) {
            selectedMap = MapManager.getRandomMap();
            currentMapName = selectedMap.getName();
        } else {
            currentMapName = settings.selectedMapName != null ? settings.selectedMapName : "Map 1";
            selectedMap = MapManager.getMapByName(currentMapName);
        }
        // Fallback to a random map if selection failed
        if (selectedMap == null) {
            selectedMap = MapManager.getRandomMap();
            currentMapName = selectedMap.getName();
        }

        // Load the tiled map for rendering
        tiledMap = new TmxMapLoader().load(selectedMap.getPath());

        // Load all landmark objects from the map (e.g., obstacles, key points)
        loadAllLandmarksFromObjectGroups();

        // Set up the Tiled map renderer
        mapRenderer = new OrthogonalTiledMapRenderer(tiledMap);

        // Calculate full map pixel dimensions
        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int tilePixelWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        int tilePixelHeight = tiledMap.getProperties().get("tileheight", Integer.class);
        tileWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        tileHeight = tiledMap.getProperties().get("tileheight", Integer.class);
        mapPixelWidth = mapTileWidth * tilePixelWidth;
        mapPixelHeight = mapTileHeight * tilePixelHeight;

        // Initialize player input handling
        playerInputExtender = new PlayerInputExtender(this);

        // Set up the camera to center on the map
        camera = new OrthographicCamera(mapPixelWidth, mapPixelHeight);
        camera.position.set(mapPixelWidth / 2f, mapPixelHeight / 2f, 0);
        camera.update();

        // Load player sprite sheet and create animations
        Texture playerTexture = new Texture(Gdx.files.internal("player.png"));
        int expectedPlayerCols = 4, expectedPlayerRows = 4;
        int playerCellWidth = playerTexture.getWidth() / expectedPlayerCols;
        int playerCellHeight = playerTexture.getHeight() / expectedPlayerRows;
        TextureRegion[][] playerFrames = TextureRegion.split(playerTexture, playerCellWidth, playerCellHeight);
        playerWalkDown = new Animation<>(0.15f, playerFrames[0][0], playerFrames[0][1]);
        playerWalkLeft = new Animation<>(0.15f, playerFrames[2][0], playerFrames[2][1]);
        playerWalkRight = new Animation<>(0.15f, playerFrames[1][0], playerFrames[1][1]);
        playerWalkUp = new Animation<>(0.15f, playerFrames[3][0], playerFrames[3][1]);
        // Loop animations for continuous walking
        playerWalkDown.setPlayMode(Animation.PlayMode.LOOP);
        playerWalkLeft.setPlayMode(Animation.PlayMode.LOOP);
        playerWalkRight.setPlayMode(Animation.PlayMode.LOOP);
        playerWalkUp.setPlayMode(Animation.PlayMode.LOOP);

        // Define canonical spawn points for player and AI
        spawnPoints = new ArrayList<>();
        createSpawnPositions();

        // Perform AI scan of walkable areas for pathfinding
        ai.scanWalkableAreas(this, tiledMap);

        // Place treasure chests randomly on the map
        treasureChests = new ArrayList<>();
        placeTreasuresScattered();

        // If in timer mode, initialize the round timer duration
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            roundTimer = settings.timerDuration;
        }

        // Load AI sprite sheet and create directional animations
        Texture aiTexture = new Texture(Gdx.files.internal("ai.png"));
        int aiCols = 4, aiRows = 4;
        TextureRegion[][] aiFrames = TextureRegion.split(aiTexture, aiTexture.getWidth() / aiCols, aiTexture.getHeight() / aiRows);
        TextureRegion[] aiWalkDownFrames = new TextureRegion[aiRows];
        TextureRegion[] aiWalkLeftFrames = new TextureRegion[aiRows];
        TextureRegion[] aiWalkUpFrames = new TextureRegion[aiRows];
        TextureRegion[] aiWalkRightFrames = new TextureRegion[aiRows];
        for (int row = 0; row < aiRows; row++) {
            aiWalkDownFrames[row] = aiFrames[row][0];  // down
            aiWalkLeftFrames[row] = aiFrames[row][1];  // left
            aiWalkUpFrames[row] = aiFrames[row][2];    // up
            aiWalkRightFrames[row] = aiFrames[row][3]; // right
        }
        aiWalkDown = new Animation<>(0.15f, aiWalkDownFrames);
        aiWalkLeft = new Animation<>(0.15f, aiWalkLeftFrames);
        aiWalkUp = new Animation<>(0.15f, aiWalkUpFrames);
        aiWalkRight = new Animation<>(0.15f, aiWalkRightFrames);
        aiWalkDown.setPlayMode(Animation.PlayMode.LOOP);
        aiWalkLeft.setPlayMode(Animation.PlayMode.LOOP);
        aiWalkUp.setPlayMode(Animation.PlayMode.LOOP);
        aiWalkRight.setPlayMode(Animation.PlayMode.LOOP);

        // Initialize SpriteBatch, fonts, and shape renderer for drawing
        batch = new SpriteBatch();
        font = new BitmapFont();
        shapeRenderer = new ShapeRenderer();
        // Create a larger font for special on-screen text
        largeFont = new BitmapFont();
        largeFont.getData().setScale(2.0f);
    }

    public TiledMap getTiledMap() {
        return tiledMap;
    }

    /**
     * Returns a random spawn point from the list
     * @return A randomly selected spawn point
     */
    private void initializeSpawnPositions() {
        availableSpawnPositions = new ArrayList<>();
        availableSpawnPositions.add(SpawnPosition.TOP_RIGHT);
        availableSpawnPositions.add(SpawnPosition.BOTTOM_RIGHT);
        availableSpawnPositions.add(SpawnPosition.BOTTOM_LEFT);
        availableSpawnPositions.add(SpawnPosition.TOP_LEFT);
    }
    /**
     * Creates spawn positions for player and AI using one of the predefined positions
     * This is called from constructor and resetRound
     */
    public void createSpawnPositions() {
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
            // If no positions left, reinitialize (just in case)
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
            case TOP_LEFT:
            default:
                return new Vector2(
                    bufferX * tileWidth + tileWidth/2,
                    (mapTileHeight - bufferY) * tileHeight - tileHeight/2);
        }
    }

    /**
     * Triggers and initializes the AI speed boost visual effect.
     */
    public void showAISpeedBoostEffect() {
        showingAISpeedBoost = true;
        speedBoostEffectTimer = 0f;
        pulseDirectionUp = true;
        pulseAlpha = 0.4f;
    }

    /**
     * Places treasures scattered randomly across the current map by delegating to TreasureExtender.
     */
    public void placeTreasuresScattered() {
        treasureExtender.placeTreasuresScattered();
    }

    /**
     * Resets the countdown timer to the initial duration specified in the game settings.
     */
    public void resetTimer() {
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

    /**
     * Clears {@code landmarks}, then converts every {@link MapObject} in every
     * {@link MapLayer} of {@code tiledMap} into a {@link Landmark} (using a default
     * radius of 100 f) and stores it.
     *
     * @throws NumberFormatException if an object’s {@code x} or {@code y} value
     *                               cannot be parsed.
     */
    public void loadAllLandmarksFromObjectGroups() {
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

    /**
     * Gets the “speed‑boost” picture for the game.
     *
     * <p>The first time you call this, we draw a small <span style="color:orange">orange</span> glow
     * (128 × 128 pixels) and keep it, so later calls can just reuse it.</p>
     *
     * @return the ready‑to‑use speed‑boost texture
     */
    public Texture getSpeedBoostTexture() {
        if (speedBoostTexture == null) {
            final int size = 128;
            Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
            pixmap.setBlending(Pixmap.Blending.SourceOver);

            float center = size * 0.5f;
            float sigma  = size * 0.25f;
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

    /**
     * Updates one game frame.
     *
     * <p>If the start‑countdown is finished, this:
     * player + AI logic, hint timers, chest states, camera,
     * and win/lose checks—all scaled by {@code delta}.</p>
     *
     * @param delta seconds since the last frame
     */
    public void update(float delta) {
        // Only allow player input and AI movement if countdown is not active
        if (!countdownActive) {
            playerStateTime += delta;
            aiStateTime += delta;
            handlePlayerInput(delta);
            updateAI(delta);

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

    /**
     * Fits the camera to the full Tiled map and centres it.
     *
     * <p>Calculates the map’s pixel size from its properties, sets the viewport
     * to match, centres the camera, then calls {@code camera.update()}.</p>
     */
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

    /**
     * Feeds the current frame’s input to the player‑controller.
     *
     * @param delta seconds since last frame
     */
    private void handlePlayerInput(float delta) {
        playerInputExtender.handleInput(delta);
    }

    /**
     * Runs one AI frame—unless a countdown or popup is on screen.
     * <p>Main steps:</p>
     * <ol>
     *   <li>Advance AI logic and store its facing direction.</li>
     *   <li>If close to a closed chest, play SFX, open it, increment AI score,
     *       log the event for training data, and clear any nearby hint landmarks.</li>
     * </ol>
     *
     * @param delta seconds since last frame
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



    /**
     * Checks whether the round should end:
     * if the player or AI has claimed more than half the treasures,
     * or if every chest is open.
     * <p>If so, calls {@link #endRound()}.</p>
     */
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

    /**
     * Ends the current round by delegating to the roundEndExtender.
     */
    public void endRound() {
        roundEndExtender.endRound();
    }

    /**
     * Switches to the end screen using the final player and AI round wins.
     */
    public void showEndScreen() {
        game.setScreen(new EndScreen(game, settings.playerRoundsWon, settings.aiRoundsWon, settings));
    }

    /**
     * Determines if the given world position is free for movement.
     *
     * <p>Checks, in order:</p>
     * <ol>
     *   <li>Map bounds</li>
     *   <li>Presence on at least one “walkable” tile layer</li>
     *   <li>Absence from any collidable tile layer</li>
     *   <li>Absence from any collision‐object in the “Collision” layer</li>
     * </ol>
     *
     * @param pos the position in world pixels to test
     * @return {@code true} if it’s within bounds, on a walkable tile, and not blocked
     */
    public boolean isWalkable(Vector2 pos) {
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

    /**
     * Renders one frame by delegating to the render extender.
     *
     * @param delta seconds since the last frame
     */
    @Override
    public void render(float delta) {
        renderExtender.render(delta);
    }

    /**
     * Adjusts the viewport to new dimensions and updates the camera.
     *
     * @param width  new viewport width in pixels
     * @param height new viewport height in pixels
     */
    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    /**
     * Called when this screen is shown; loads sound effects and starts looping game music.
     */
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
    /**
     * Pauses the screen—no action required.
     */
    @Override public void pause() { }

    /**
     * Resumes the screen—no action required.
     */
    @Override public void resume() { }

    /**
     * Hides the screen and disposes game music if it’s still playing.
     */
    @Override public void hide() {

        if (duringGameMusic != null && duringGameMusic.isPlaying()) {
            duringGameMusic.dispose();
        }
    }

    /**
     * Cleans up all resources: map, renderers, batch, fonts, shapes, chests, and audio.
     */
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
