package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.TimeUtils;
import java.util.ArrayList;
import java.util.Random;

public class GameScreen implements Screen {
    private Main game;
    private GameSettings settings;
    private ShapeRenderer shapeRenderer;
    private TiledMap tiledMap;
    private OrthogonalTiledMapRenderer mapRenderer;
    private OrthographicCamera camera;
    // Existing game objects
    private Player player;
    private AIPlayer ai;
    private ArrayList<Treasure> treasures;
    private Random random;
    // Timers and messaging
    private long gameStartTime;
    private long lastHintTime;
    private long lastAIMoveTime;
    private String message = "";
    private boolean awaitingCollection = false;
    private long collectionStartTime;
    private Treasure currentTreasure = null;

    public GameScreen(Main game, GameSettings settings) {
        this.game = game;
        this.settings = settings;
        shapeRenderer = new ShapeRenderer();
        random = new Random();

        // Load your TMX file from assets (ensure the path is correct)
        TmxMapLoader mapLoader = new TmxMapLoader();
        tiledMap = mapLoader.load("maps/map1.tmx");
        mapRenderer = new OrthogonalTiledMapRenderer(tiledMap);

        // Set up the camera so it covers the window size
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // Create game objects (these might be used in combination with your tile map for collision, etc.)
        player = new Player(new Vector2(50, 50));
        ai = new AIPlayer(new Vector2(750, 550));

        // Generate treasures at random positions within the window bounds
        treasures = new ArrayList<>();
        for (int i = 0; i < settings.treasureCount; i++) {
            Vector2 pos;
            do {
                pos = new Vector2(random.nextInt(Gdx.graphics.getWidth()), random.nextInt(Gdx.graphics.getHeight()));
            } while (pos.dst(player.position) < 50 || pos.dst(ai.position) < 50); // You might also use tile map info for valid areas
            treasures.add(new Treasure(pos));
        }

        gameStartTime = TimeUtils.millis();
        lastHintTime = TimeUtils.millis();
        lastAIMoveTime = TimeUtils.millis();
    }

    @Override
    public void render(float delta) {
        // Update game logic here...

        // Clear screen and update camera
        Gdx.gl.glClearColor(0, 0, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        camera.update();

        // Render the tile map first
        mapRenderer.setView(camera);
        mapRenderer.render();

        // Then render additional overlays (players, treasures, etc.) using a separate batch or shape renderer
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        // Your drawing code here…
        shapeRenderer.end();
    }


    private void update(float delta) {
        // --- Update game logic here ---
        // Example: Player movement, AI movement, treasure checks, etc.
        Vector2 oldPos = new Vector2(player.position);
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            player.position.x -= player.speed * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            player.position.x += player.speed * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            player.position.y += player.speed * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            player.position.y -= player.speed * delta;
        }
        // You might want to add collision checks with the tile map here too

        // Update AI (existing logic)
        if (TimeUtils.millis() - lastAIMoveTime > 30000) {
            Treasure hintTarget = getClosestTreasure(ai.position);
            if (hintTarget != null) {
                ai.setTarget(hintTarget.position);
            } else {
                ai.setTarget(new Vector2(random.nextInt(Gdx.graphics.getWidth()), random.nextInt(Gdx.graphics.getHeight())));
            }
            lastAIMoveTime = TimeUtils.millis();
        }
        ai.update(delta, null); // You could pass tile map collision info here if needed

        // --- Other game logic for hints and treasure collection as before ---
    }

    private Treasure getClosestTreasure(Vector2 pos) {
        Treasure closest = null;
        float minDist = Float.MAX_VALUE;
        for (Treasure t : treasures) {
            if (!t.collected) {
                float d = pos.dst(t.position);
                if (d < minDist) {
                    minDist = d;
                    closest = t;
                }
            }
        }
        return closest;
    }

    @Override public void show() { }
    @Override public void resize(int width, int height) { }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() { }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        mapRenderer.dispose();
        tiledMap.dispose();
    }
}
