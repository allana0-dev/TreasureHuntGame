package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class EndScreen implements Screen {
    private Main game;
    private Stage stage;
    private Skin skin;

    public EndScreen(Main game, int playerScore, int aiScore, GameSettings settings) {
        this.game = game;
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("uiskin.json"));

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        String result;
        if (settings.gameMode == GameSettings.GameMode.TIMER) {
            if (playerScore > aiScore) result = "Player Wins!";
            else if (aiScore > playerScore) result = "AI Wins!";
            else result = "It's a Tie!";
        } else {
            result = (playerScore > aiScore) ? "Player Wins!" : "AI Wins!";
        }

        table.add(new Label("Game Over", skin)).colspan(2).padBottom(20);
        table.row();
        table.add(new Label("Player Score: " + playerScore, skin)).padBottom(10);
        table.row();
        table.add(new Label("AI Score: " + aiScore, skin)).padBottom(10);
        table.row();
        table.add(new Label(result, skin)).colspan(2).padBottom(20);
        table.row();

        TextButton restartButton = new TextButton("Restart", skin);
        TextButton quitButton = new TextButton("Quit", skin);
        table.add(restartButton).padRight(20);
        table.add(quitButton);

        restartButton.addListener(event -> {
            if (restartButton.isPressed()) {
                game.setScreen(new StartScreen(game));
            }
            return false;
        });
        quitButton.addListener(event -> {
            if (quitButton.isPressed()) {
                Gdx.app.exit();
            }
            return false;
        });
    }

    @Override public void show() { }
    @Override public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }
    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() { }
    @Override public void dispose() { stage.dispose(); skin.dispose(); }
}

