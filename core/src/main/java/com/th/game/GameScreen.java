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
import org.encog.ml.data.basic.BasicMLDataSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameScreen implements Screen {

    private Main game;
    private GameSettings settings;
    private String currentMapName;


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
    private float hintTimer = 0f;         // Accumulates time until 10 seconds have passed.
    private String currentHint = "";      // The current hint message to display.
    private final float HINT_INTERVAL = 10f; // Interval for showing a new hint (10 seconds).
    private List<Landmark> landmarks = new ArrayList<>();
    // Add these variables to the GameScreen class
    private float hintDisplayDuration = 3.0f; // Show hint for 3 seconds
    private float currentHintDisplayTimer = 0f; // Track current display time
    private boolean hintVisible = false; // Is the hint currently visible
    // Add to GameScreen class
    private boolean hintButtonPressed = false;
    private float hintCooldown = 0f;
    private final float HINT_COOLDOWN_DURATION = 15f; // 15 seconds between hints
    private boolean hintAvailable = true;

    // --- AI fields ---
// at top of GameScreen
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

        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            resetTimer();
        }

        // Initialize the training data database.
        try {
            TrainingDataDAO.initializeDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // --- Load Tile Map ---

        // --- Determine which map to load ---
        // Store the selected map name
// --- Determine which map to load ---
        MapManager.MapInfo selectedMap = null;
        if (settings.mapType == GameSettings.MapType.RANDOM) {
            selectedMap = MapManager.getRandomMap();
            currentMapName = selectedMap.getName();
            System.out.println("Randomly selected map: " + selectedMap.getName());
        } else {
            // Need to set selectedMap for STORED maps too
            currentMapName = settings.selectedMapName != null ? settings.selectedMapName : "Map 1";
            selectedMap = MapManager.getMapByName(currentMapName);
            System.out.println("Stored map selected: " + currentMapName);
        }

// Check if map was found
        if (selectedMap == null) {
            // Fallback to the first map if the requested map wasn't found
            selectedMap = MapManager.getRandomMap();
            currentMapName = selectedMap.getName();
            System.out.println("WARNING: Requested map not found. Using fallback map: " + currentMapName);
        }

// --- Load Tile Map ---
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

        do {
            ai = new SmartAI(new Vector2(random.nextInt(mapPixelWidth), random.nextInt(mapPixelHeight)), currentMapName);
        } while (!isWalkable(ai.position));

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

        // Removed AI initialization.
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
            cellIndices.add(i);
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

        System.out.println("Placed " + placedTreasures + " treasure chests scattered across the map");
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
                System.out.println("Loading landmarks from layer: " + layer.getName());
                for (MapObject object : objects) {
                    float x = Float.parseFloat(object.getProperties().get("x").toString());
                    float y = Float.parseFloat(object.getProperties().get("y").toString());
                    String name = object.getName();
                    float radius = 100f; // You can adjust or calculate the radius accordingly
                    landmarks.add(new Landmark(name, new Vector2(x, y), radius));
                    System.out.println("Loaded landmark: " + name + " at (" + x + ", " + y + ")");
                }
            }
        }
        System.out.println("Total landmarks loaded: " + landmarks.size());
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

        // --- Render the AI ---
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

        // --- Draw Heads Up Display (HUD) ---
// Scores at the top left
        font.draw(batch, "Player Score: " + player.score, camera.position.x - 380, camera.position.y + (camera.viewportHeight / 2f) - 20);
        font.draw(batch, "AI Score: " + ai.score, camera.position.x - 380, camera.position.y + (camera.viewportHeight / 2f) - 40);

// Hint button below scores
        if (hintAvailable) {
            font.draw(batch, "Press H for Hint", camera.position.x - 380, camera.position.y + (camera.viewportHeight / 2f) - 60);
        } else {
            font.draw(batch, "Hint available in: " + (int)hintCooldown, camera.position.x - 380, camera.position.y + (camera.viewportHeight / 2f) - 60);
        }

// Current hint at top center
        if (hintVisible && !currentHint.isEmpty()) {
            float alpha = 1.0f;
            // Fade calculations...
            Color prevColor = font.getColor();
            font.setColor(prevColor.r, prevColor.g, prevColor.b, alpha);
            // Center the hint text
            font.draw(batch, currentHint, camera.position.x - 200, camera.position.y + (camera.viewportHeight / 2f) - 20);
            font.setColor(prevColor);
        }


// Timer at top right (unchanged)
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            int timeLeft = (int) countdownTimer;
            font.draw(batch, "Time Left: " + timeLeft, camera.position.x + 250, camera.position.y + (camera.viewportHeight / 2f) - 20);
        }

        batch.end();
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

    // Update the main update method to call the modified updateAI
// Make sure hint processing passes the landmarks to the AI
    private void update(float delta) {
        // Update game logic, player movement, AI, etc.
        playerStateTime += delta;
        aiStateTime += delta;

        // Handle player input
        handlePlayerInput(delta);

        // REMOVE THIS ENTIRE BLOCK - it's the automatic hint generation
    /*
    hintTimer += delta;
    if (hintTimer >= HINT_INTERVAL) {
        hintTimer = 0f; // reset timer
        // Generate a new hint
        currentHint = generateGlobalHint();

        // If we have a valid hint, start displaying it
        if (!currentHint.isEmpty()) {
            hintVisible = true;
            currentHintDisplayTimer = 0f;

            // Pass the hint to the AI along with landmarks
            if (ai != null && !currentHint.isEmpty()) {
                ai.processHint(currentHint, landmarks);
            }
        }
    }
    */

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

        // Update AI after potentially processing the hint
        updateAI(delta);

        // Update treasure states and other game logic
        for (TreasureChest chest : treasureChests) {
            chest.update(delta);
        }

        // Update the camera and check round conditions
        updateCamera();
        checkRoundEnd();
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
        // Fallback message if no treasure is near a landmark.
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
        player.position.x = MathUtils.clamp(player.position.x, 0, mapPixelWidth - 64);
        player.position.y = MathUtils.clamp(player.position.y, 0, mapPixelHeight - 64);
        if (!isWalkable(player.position)) {
            player.position.set(oldPosition);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            for (TreasureChest chest : treasureChests) {
                if (chest.state == TreasureChest.ChestState.CLOSED &&
                    player.position.dst(chest.position) < 32) {
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

                    // Notify AI that player collected a treasure - pass true to indicate player collected it
                    ai.notifyTreasureCollected(chest.position, true);

                    // Rest of the code remains the same...
                }
            }
        }
        // Add this to your handlePlayerInput method
        if (Gdx.input.isKeyJustPressed(Input.Keys.H) && hintAvailable) {
            // Generate and display a hint
            currentHint = generateGlobalHint();

            if (!currentHint.isEmpty()) {
                // Reset hint display state
                hintVisible = true;
                currentHintDisplayTimer = 0f;

                // Start cooldown
                hintAvailable = false;
                hintCooldown = HINT_COOLDOWN_DURATION;

                // Pass the hint to the AI
                if (ai != null) {
                    ai.processHint(currentHint, landmarks);
                }
            }
        }
// In update method, handle cooldown
        if (!hintAvailable) {
            hintCooldown -= delta;
            if (hintCooldown <= 0) {
                hintAvailable = true;
                hintCooldown = 0;
            }
        }
    }

    // Modify the updateAI method in GameScreen.java
    private void updateAI(float delta) {
        // Update the AI's state and movement
        // In the GameScreen class update method:
        ai.update(delta, this);

        // Set animation direction based on AI's current direction
        aiDirection = ai.getCurrentDirection();

        // Check for treasure collection
        for (TreasureChest chest : treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED &&
                ai.position.dst(chest.position) < 32) {
                chest.open();
                ai.score++;

                // Notify the AI that a treasure was collected by AI - pass false to indicate AI collected it
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
        }    }


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
        settings.playerRoundScores.add(player.score);
        settings.aiRoundScores.add(ai.score);
        if (ai.score > player.score) {
            settings.aiRoundsWon++;
            System.out.println("Round " + currentRound + " ended. AI wins! AI Score: " + ai.score + ", Player Score: " + player.score);
        } else if (player.score > ai.score) {
            settings.playerRoundsWon++;
            System.out.println("Round " + currentRound + " ended. Player wins! Player Score: " + player.score + ", AI Score: " + ai.score);
        } else {
            // In case of a tie, you could decide what to do (award both, award neither, etc.)
            System.out.println("Round " + currentRound + " ended in a tie! Player Score: " + player.score + ", AI Score: " + ai.score);
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

    // Modify the resetRound method to rescan the map when resetting the round
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

        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int mapPixelWidth = mapTileWidth * tileWidth;
        int mapPixelHeight = mapTileHeight * tileHeight;

        // Reset player position with validation
        Vector2 playerSpawnPos = SpawnLocationValidator.findValidSpawnLocation(
            this, mapPixelWidth, mapPixelHeight, tileWidth, tileHeight, 100);
        player.position.set(playerSpawnPos);

        // Reset AI position with validation
        Vector2 aiSpawnPos = SpawnLocationValidator.findValidSpawnLocation(
            this, mapPixelWidth, mapPixelHeight, tileWidth, tileHeight, 100);
        // Make sure AI doesn't spawn too close to player
        while (aiSpawnPos.dst(playerSpawnPos) < 200) {
            aiSpawnPos = SpawnLocationValidator.findValidSpawnLocation(
                this, mapPixelWidth, mapPixelHeight, tileWidth, tileHeight, 100);
        }
        ai.position.set(aiSpawnPos);

        // Reset hint system
        currentHint = "";
        hintTimer = 0f;

        // Reload all landmarks from the map
        loadAllLandmarksFromObjectGroups();

        // Rescan the map for the AI
        ai.scanWalkableAreas(this, tiledMap);

        // Place new treasure chests
        placeTreasuresScattered();

        // Reset round timer
        resetTimer();
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
