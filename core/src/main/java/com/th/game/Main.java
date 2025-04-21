package com.th.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.th.game.screens.StartScreen;

/**
 * Entry point for the treasure‑hunt game.
 *
 * <p>Initializes the start screen and adds a global check to exit the app when ESC is pressed.</p>
 */
public class Main extends Game {

    /**
     * Called once when the application starts.
     * Sets the initial screen to the {@link StartScreen}.
     */
    @Override
    public void create() {
        setScreen(new StartScreen(this));
    }

    /**
     * Called every frame to render the current screen.
     * Also listens for the ESC key to exit the application.
     */
    @Override
    public void render() {
        // Allow exit by hitting esc
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
        super.render();
    }
}




