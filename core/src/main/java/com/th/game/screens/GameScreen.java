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


public class GameScreen implements Screen {

    private Main game;
    public GameSettings settings;
    public String currentMapName;
    public boolean showingRoundPopup = false;
    public float roundPopupTimer = 0f;
    private final float ROUND_POPUP_DURATION = 2.5f; // Show popup for 2.5 seconds
    public BitmapFont largeFont; // For round popup text
    public boolean hintEnabled;


    //speedboost
    public boolean showingAISpeedBoost = false;
    public float speedBoostEffectTimer = 0f;
    public final float SPEED_BOOST_EFFECT_DURATION = 3.0f; // Show effect for 3 seconds
    public final Color SPEED_BOOST_COLOR = new Color(1f, 0.4f, 0.2f, 0.7f); // Orange/red glow
    public boolean pulseDirectionUp = true;
    public float pulseAlpha = 0.4f;
    private Texture speedBoostTexture;

    // Map & rendering objects.
    TiledMap tiledMap;
    public OrthogonalTiledMapRenderer mapRenderer;
    public OrthographicCamera camera;
    public SpriteBatch batch;
    public BitmapFont font;
    public ShapeRenderer shapeRenderer;
    private int tileWidth;
    private int tileHeight;
    public float countdownTimer;
    public float hintTimer = 0f;
    public String currentHint = "";
    // Add these variables to GameScreen class
    public boolean countdownActive = false;
    private final float COUNTDOWN_DURATION = 3.0f;
    public int currentCountdownNumber = 3;
    private final float HINT_INTERVAL = 10f;
    private List<Landmark> landmarks = new ArrayList<>();
    // Add these variables to the GameScreen class
    private float hintDisplayDuration = 3.0f;
    public float currentHintDisplayTimer = 0f;
    public boolean hintVisible = false;
    // Add to GameScreen class
    private boolean hintButtonPressed = false;
    public float hintCooldown = 0f;
    public final float HINT_COOLDOWN_DURATION = 15f; // 15 seconds between hints
    public boolean hintAvailable = true;
    public Sound collectSound;
    private Music duringGameMusic;
    public Sound hintSound;
    private final List<Vector2> spawnPoints;

    // In the GameScreen class variables section, add or update these variables:
    public final float COUNTDOWN_NUMBER_DURATION = 1.0f; // Each number shows for 1 second
    public float countdownNumberTimer = 0f;
    public boolean gameStarted = false;



    public SmartAI ai;
    public Animation<TextureRegion> aiWalkDown;
    public Animation<TextureRegion> aiWalkLeft;
    public Animation<TextureRegion> aiWalkRight;
    public Animation<TextureRegion> aiWalkUp;
    public float aiStateTime = 0f;
    public Direction aiDirection = Direction.DOWN;
    private float aiMoveTimer = 0f;
    private final float aiMoveInterval = 0.5f; // Change direction every 0.5 seconds.
    private Direction aiCurrentDirection = Direction.DOWN; // Use your existing Direction enum.



    // --- Player fields ---
    public Player player;
    public Animation<TextureRegion> playerWalkDown;
    public Animation<TextureRegion> playerWalkLeft;
    public Animation<TextureRegion> playerWalkRight;
    public Animation<TextureRegion> playerWalkUp;
    public float playerStateTime = 0f;
    public Direction playerDirection = Direction.DOWN;

    // --- Treasure chests ---
    public ArrayList<TreasureChest> treasureChests;

    // Define an enum for spawn positions
    public enum SpawnPosition {
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT,
        CENTRE,
        TOP_LEFT
    }
    private List<SpawnPosition> availableSpawnPositions;


    // Other game fields.
    private Random random;
    public int currentRound = 1;
    private float roundTimer;

    // Cached map dimensions.
    public int mapPixelWidth;
    private RenderExtender renderExtender;
    private PlayerInputExtender playerInputExtender;
    private TreasureExtender treasureExtender;
    private RoundEndExtender roundEndExtender;



    public int mapPixelHeight;

    public GameScreen(Main game, GameSettings settings) {
        this.game = game;
        this.settings = settings;
        random = new Random();
        // Initialize the render extender
        renderExtender = new RenderExtender(this);
        treasureExtender = new TreasureExtender(this);
        roundEndExtender = new RoundEndExtender(this);



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

        playerInputExtender = new PlayerInputExtender(this);


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
            case TOP_LEFT:
            default:
                return new Vector2(
                    bufferX * tileWidth + tileWidth/2,
                    (mapTileHeight - bufferY) * tileHeight - tileHeight/2);
        }
    }

    public void showAISpeedBoostEffect() {
        showingAISpeedBoost = true;
        speedBoostEffectTimer = 0f;
        pulseDirectionUp = true;
        pulseAlpha = 0.4f;
    }

    public void placeTreasuresScattered() {
        treasureExtender.placeTreasuresScattered();
    }

    // Resets the countdown timer to the initial value from game settings.
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
     * Updated update method to integrate with new AI logic
     * Replace your existing update method with this one
     */
    public void update(float delta) {


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
    // Replace the handlePlayerInput method with:
    private void handlePlayerInput(float delta) {
        playerInputExtender.handleInput(delta);
    }

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
    // Replace the existing endRound method with:
    public void endRound() {
        roundEndExtender.endRound();
    }

    public void showEndScreen() {
        game.setScreen(new EndScreen(game, settings.playerRoundsWon, settings.aiRoundsWon, settings));
    }
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

    @Override
    public void render(float delta) {
        renderExtender.render(delta);
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
