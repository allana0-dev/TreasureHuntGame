package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
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
    private boolean showingRoundPopup = false;
    private float roundPopupTimer = 0f;
    private final float ROUND_POPUP_DURATION = 2.5f; // Show popup for 2.5 seconds
    private BitmapFont largeFont; // For round popup text


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
    private Sound collectSound;
    private Music duringGameMusic;
    private Sound hintSound;



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



        showingRoundPopup = true;
        roundPopupTimer = 0f;

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
        float playerX = 3 * tileWidth + (tileWidth / 2);
        float playerY = 14 * tileHeight + (tileHeight / 2);
        player = new Player(new Vector2(playerX, playerY));

        float aiX = 5 * tileWidth + (tileWidth / 2);
        float aiY = 14 * tileHeight + (tileHeight / 2);
        ai = new SmartAI(new Vector2(aiX, aiY), currentMapName);

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

        // --- Draw Heads Up Display (HUD) ---
// Scores at the top left
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
            float textWidth = largeFont.draw(batch, roundText, 0, 0).width; // Measure text width

            largeFont.draw(batch, roundText,
                camera.position.x - textWidth / 2, // Center text horizontally
                camera.position.y + 10  // Slight offset from center for better appearance
            );

            // Reset font color
            largeFont.setColor(prevColor);
        }
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

    /**
     * Updated update method to integrate with new AI logic
     * Replace your existing update method with this one
     */
    private void update(float delta) {
        // Update animation times
        playerStateTime += delta;
        aiStateTime += delta;

        // Handle player input
        handlePlayerInput(delta);

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

        // Update AI
        updateAI(delta);

        // Update treasure states
        for (TreasureChest chest : treasureChests) {
            chest.update(delta);
        }

        // Update the camera and check round conditions
        updateCamera();
        checkRoundEnd();
    }
    /**
     * Add a method to the render method to enable path visualization (for debugging)
     * Call this at the end of your render method if you want to see the AI's path
     */
    private void renderDebugInfo() {
        // Only enable this during development/debugging
        boolean debugMode = false;

        if (debugMode) {
            // Draw the AI's path
            drawPathVisualization();

            // Draw AI target if it has one
            if (ai instanceof SmartAI) {
                SmartAI smartAI = (SmartAI) ai;
                Vector2 target = smartAI.getTargetPosition();

                if (target != null) {
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                    shapeRenderer.setColor(1, 0, 1, 0.7f); // Purple for target
                    shapeRenderer.circle(target.x, target.y, 10);
                    shapeRenderer.end();
                }
            }
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
    /**
     * Modification to handlePlayerInput to properly notify the AI when player collects a treasure
     * Update your existing handlePlayerInput method to include this logic
     */
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

                    // Remove associated landmark hints
                    Iterator<Landmark> iter = landmarks.iterator();
                    while (iter.hasNext()) {
                        Landmark lm = iter.next();
                        if (chest.position.dst(lm.position) < lm.radius) {
                            System.out.println("Removing landmark hint: " + lm.name + " (collected by player)");
                            iter.remove();
                            break;
                        }
                    }
                }
            }
        }

        // Handle hint button
        if (Gdx.input.isKeyJustPressed(Input.Keys.H) && hintAvailable) {
            // Generate and display a hint
            currentHint = generateGlobalHint();

            if (!currentHint.isEmpty()) {
                // Reset hint display state
                hintVisible = true;
                hintSound.play(0.8f);
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
    }
    // Modify the updateAI method in GameScreen.java

    /**
     * Example of how to update the updateAI method in GameScreen to work with the new AI
     * Replace your existing updateAI method with this one
     */
    private void updateAI(float delta) {
        // Update the AI's state and movement
        // In the GameScreen class update method:
        System.out.println("AI position: " + ai.position + ", hasTarget: " + ((SmartAI)ai).getTargetPosition());

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
// In the GameScreen class, modify the resetRound method like this:
// In GameScreen.java, modify the resetRound() method:

    /**
     * Example of how to modify the resetRound method to work with the new AI
     * Update your resetRound method to include this logic
     */
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

        // Convert tile coordinates to pixel coordinates
        float playerX = 3 * tileWidth + (tileWidth / 2);
        float playerY = 14 * tileHeight + (tileHeight / 2);
        player.position.set(playerX, playerY);

        // Place AI at a different but still valid location
        float aiX = 5 * tileWidth + (tileWidth / 2);
        float aiY = 14 * tileHeight + (tileHeight / 2);
        ai.position.set(aiX, aiY);

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

    }
}
