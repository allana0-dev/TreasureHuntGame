package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.TimeUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class GameScreen implements Screen {
    private Main game;
    private GameSettings settings;
    private ShapeRenderer shapeRenderer;
    private Maze maze;
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

        maze = new Maze(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());


        // Create player and AI in walkable areas
        player = new Player(new Vector2(50, 50));
        ai = new AIPlayer(new Vector2(Gdx.graphics.getWidth() - 50, Gdx.graphics.getHeight() - 50));

        // Generate treasures at random walkable positions (avoid spawning too close to player or AI)
        treasures = new ArrayList<>();
        for (int i = 0; i < settings.treasureCount; i++) {
            Vector2 pos;
            do {
                pos = new Vector2(random.nextInt(Gdx.graphics.getWidth()), random.nextInt(Gdx.graphics.getHeight()));
            } while (!maze.isWalkable(pos) || pos.dst(player.position) < 50 || pos.dst(ai.position) < 50);
            treasures.add(new Treasure(pos));
        }

        gameStartTime = TimeUtils.millis();
        lastHintTime = TimeUtils.millis();
        lastAIMoveTime = TimeUtils.millis();
    }

    @Override
    public void render(float delta) {
        update(delta);
        Gdx.gl.glClearColor(0, 0, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        // Draw maze walls
        maze.render(shapeRenderer);
        // Draw treasures (if not collected)
        for (Treasure t : treasures) {
            if (!t.collected) {
                shapeRenderer.setColor(Color.GOLD);
                shapeRenderer.circle(t.position.x, t.position.y, 10);
            }
        }
        // Draw player
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.circle(player.position.x, player.position.y, 15);
        // Draw AI
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.circle(ai.position.x, ai.position.y, 15);
        shapeRenderer.end();

        // (For simplicity, messages are printed to the console.
        // In a real game you’d render text with a BitmapFont.)
        System.out.println(message);
    }

    private void update(float delta) {
        // End game if in Timer Mode and time is up.
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            float elapsed = (TimeUtils.millis() - gameStartTime) / 1000f;
            if (elapsed >= settings.timerDuration) {
                endGame();
                return;
            }
        }

        // --- 1. Player Moves ---
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
        // Prevent player from moving into walls.
        if (!maze.isWalkable(player.position)) {
            player.position.set(oldPos);
        }

        // --- 2. AI Moves (every 30 seconds, smart decision-making) ---
        if (TimeUtils.millis() - lastAIMoveTime > 30000) {
            Treasure hintTarget = getClosestTreasure(ai.position);
            if (hintTarget != null) {
                ai.setTarget(hintTarget.position);
            } else {
                // If no treasure available, choose a random target.
                ai.setTarget(new Vector2(random.nextInt(800), random.nextInt(600)));
            }
            lastAIMoveTime = TimeUtils.millis();
        }
        // AI continuously moves toward its target.
        ai.update(delta, maze);

        // --- 6. Hint System (Every 15 seconds) ---
        if (TimeUtils.millis() - lastHintTime > 15000 && !treasures.isEmpty()) {
            Treasure hint = treasures.get(random.nextInt(treasures.size()));
            message = "Hint: Treasure near (" + (int)hint.position.x + ", " + (int)hint.position.y + ")";
            ai.setTarget(hint.position);
            lastHintTime = TimeUtils.millis();
        }

        // --- 3. Check if Player or AI Finds a Treasure ---
        for (Treasure t : treasures) {
            if (!t.collected && player.position.dst(t.position) < 20) {
                message = "Press [SPACE] to collect treasure!";
                if (!awaitingCollection) {
                    awaitingCollection = true;
                    collectionStartTime = TimeUtils.millis();
                    currentTreasure = t;
                }
            }
            if (!t.collected && ai.position.dst(t.position) < 20) {
                if (!awaitingCollection) {
                    awaitingCollection = true;
                    collectionStartTime = TimeUtils.millis();
                    currentTreasure = t;
                }
            }
        }

        // --- 4. Treasure Collection (3-second timer) ---
        if (awaitingCollection && currentTreasure != null) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                currentTreasure.collected = true;
                player.score++;
                message = "Treasure Collected!";
                awaitingCollection = false;
                currentTreasure = null;
            } else if (TimeUtils.millis() - collectionStartTime > 3000) {
                if (currentTreasure != null) {
                    currentTreasure.collected = true;
                    ai.score++;
                    message = "AI Stole the Treasure!";
                }
                awaitingCollection = false;
                currentTreasure = null;
            }
        }

        // --- 7. Check if the Game is Over (First-to-Half Mode) ---
        if (settings.gameMode == GameSettings.GameMode.FIRST_TO_HALF) {
            int half = settings.treasureCount / 2 + 1;
            if (player.score >= half || ai.score >= half) {
                endGame();
            }
        }

        // (update UI elements with current scores and remaining time here.)
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

    private void endGame() {
        game.setScreen(new EndScreen(game, player.score, ai.score, settings));
    }

    @Override public void show() { }
    @Override public void resize(int width, int height) { }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() { }
    @Override public void dispose() { shapeRenderer.dispose(); }
}
