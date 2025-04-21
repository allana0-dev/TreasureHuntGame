package com.th.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.th.game.screens.StartScreen;

public class Main extends Game {
    @Override
    public void create() {
        //allana was here
        setScreen(new StartScreen(this));
    }

    @Override
    public void render() {
        // Allow exit by hitting esc
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
        super.render();
    }
}




