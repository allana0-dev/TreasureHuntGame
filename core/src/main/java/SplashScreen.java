package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.Timer.Task;

public class SplashScreen implements Screen {
    private Main game;
    private ShapeRenderer shapeRenderer;

    public SplashScreen(Main game) {
        this.game = game;
        shapeRenderer = new ShapeRenderer();

        // Schedule transition to the StartScreen after 3 seconds.
        Timer.schedule(new Task() {
            @Override
            public void run() {
                game.setScreen(new StartScreen(game));
            }
        }, 3);  // 3-second delay
    }

    @Override
    public void show() {
        // Called when this screen is set as the current screen.
    }

    @Override
    public void render(float delta) {
        // Clear the screen with a white background.
        Gdx.gl.glClearColor(1, 1, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // --- Draw a Background Circle ---
        float circleDiameter = 300f;
        float circleX = (Gdx.graphics.getWidth() - circleDiameter) / 2;
        float circleY = Gdx.graphics.getHeight() - circleDiameter - 20;
        shapeRenderer.setColor(new Color(0.529f, 0.808f, 0.922f, 1)); // Sky blue
        shapeRenderer.circle(circleX + circleDiameter / 2, circleY + circleDiameter / 2, circleDiameter / 2);

        // --- Draw a Treasure Chest ---
        float chestWidth = 150f;
        float chestHeight = 80f;
        float chestX = (Gdx.graphics.getWidth() - chestWidth) / 2;
        float chestY = 100f;
        // Draw chest body.
        shapeRenderer.setColor(new Color(0.545f, 0.271f, 0.075f, 1)); // Brown
        shapeRenderer.rect(chestX, chestY, chestWidth, chestHeight);
        // Draw chest lid.
        shapeRenderer.setColor(new Color(0.627f, 0.322f, 0.176f, 1)); // Lighter brown
        shapeRenderer.rect(chestX, chestY + chestHeight, chestWidth, 20f);

        // --- Draw Flat Foliage ---
        shapeRenderer.setColor(new Color(0.133f, 0.545f, 0.133f, 1)); // Forest green
        shapeRenderer.circle(chestX - 30f, chestY + 20f, 15f);
        shapeRenderer.circle(chestX + chestWidth + 30f, chestY + 30f, 15f);

        shapeRenderer.end();
    }

    @Override
    public void resize(int width, int height) {
        // Handle viewport changes if necessary.
    }

    @Override
    public void pause() {
        // Handle pause if needed.
    }

    @Override
    public void resume() {
        // Handle resume if needed.
    }

    @Override
    public void hide() {
        // Called when this screen is no longer the current screen.
    }

    @Override
    public void dispose() {
        // Dispose of resources.
        shapeRenderer.dispose();
    }
}
