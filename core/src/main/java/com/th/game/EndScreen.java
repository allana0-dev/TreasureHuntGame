package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class EndScreen implements Screen {
    private Main game;
    private Stage stage;
    private Skin skin;

    public EndScreen(Main game, int playerRoundsWon, int aiRoundsWon, GameSettings settings) {
        this.game = game;
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        String result;
        if (playerRoundsWon > aiRoundsWon) result = "Player is the Overall Champion!";
        else if (aiRoundsWon > playerRoundsWon) result = "AI is the Overall Champion!";
        else
            result = "The Match is a Tie!";

        

        // Use default style instead of "title"
        Label titleLabel = new Label("Game Over", skin);
        titleLabel.setFontScale(2.0f); // Make it bigger instead of using a special style
        table.add(titleLabel).colspan(3).padBottom(30);
        table.row();

        // Add round-by-round score breakdown
        table.add(new Label("Round", skin)).padRight(10);
        table.add(new Label("Player Score", skin)).padRight(10);
        table.add(new Label("AI Score", skin)).padBottom(10);

        // Add rows for each round's scores - need to check if you have the score lists in your settings
        if (settings.playerRoundScores != null && settings.aiRoundScores != null) {
            for (int i = 0; i < settings.playerRoundScores.size(); i++) {
                table.row();
                int playerScore = settings.playerRoundScores.get(i);
                int aiScore = settings.aiRoundScores.get(i);
                String roundResult = playerScore > aiScore ? "W" : (aiScore > playerScore ? "L" : "T");

                table.add(new Label("Round " + (i+1), skin)).padRight(10);
                table.add(new Label(playerScore + " " + (roundResult.equals("W") ? "✓" : ""), skin)).padRight(10);
                table.add(new Label(aiScore + " " + (roundResult.equals("L") ? "✓" : ""), skin));
            }
        } else {
            // Fallback if the score lists are missing
            table.row();
            table.add(new Label("Player Rounds Won: " + playerRoundsWon, skin)).colspan(3).padBottom(10);
            table.row();
            table.add(new Label("AI Rounds Won: " + aiRoundsWon, skin)).colspan(3).padBottom(10);
        }

        table.row();
        table.add().height(20); // Add some space
        table.row();

        // Add totals
        Label totalLabel = new Label("Total Rounds Won:", skin);
        totalLabel.setFontScale(1.2f); // Make it a bit bigger
        table.add(totalLabel).colspan(1).padBottom(10).padTop(10);
        table.add(new Label(Integer.toString(playerRoundsWon), skin)).padBottom(10).padTop(10);
        table.add(new Label(Integer.toString(aiRoundsWon), skin)).padBottom(10).padTop(10);
        table.row();

        Label resultLabel = new Label(result, skin);
        resultLabel.setFontScale(1.5f); // Make the result bigger
        table.add(resultLabel).colspan(3).padBottom(20);
        table.row();

        TextButton restartButton = new TextButton("Restart", skin);
        TextButton quitButton = new TextButton("Quit", skin);
        table.add(restartButton).padRight(20).colspan(1);
        table.add(quitButton).colspan(2);

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
    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() { }
    @Override public void dispose() {
        stage.dispose();
        skin.dispose();
    }
}


