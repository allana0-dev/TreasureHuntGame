package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class StartScreen implements Screen {
    private Main game;
    private Stage stage;
    private Skin skin;

    public StartScreen(Main game) {
        this.game = game;
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        // Title
        Label title = new Label("AI Treasure Hunt", skin);
        table.add(title).colspan(2).padBottom(20);
        table.row();

        // Number of rounds
        table.add(new Label("Number of Rounds (1, 2, or 3):", skin));
        SelectBox<Integer> roundsSelect = new SelectBox<>(skin);
        roundsSelect.setItems(1, 2, 3);
        table.add(roundsSelect).padBottom(10);
        table.row();

        // Treasure count selection
        table.add(new Label("Number of Treasures (5, 10, or 15):", skin));
        SelectBox<Integer> treasureSelect = new SelectBox<>(skin);
        treasureSelect.setItems(5, 10, 15);
        table.add(treasureSelect).padBottom(10);
        table.row();

        // Timer duration selection (enter 0 or negative for no timer / First-to-Half mode)
        table.add(new Label("Timer Duration in seconds (0 for no timer):", skin));
        TextField timerField = new TextField("60", skin);
        table.add(timerField).padBottom(10);
        table.row();

        // Map choice: Stored or Random
        table.add(new Label("Choose Map:", skin));
        SelectBox<String> mapSelect = new SelectBox<>(skin);
        mapSelect.setItems("Stored", "Random");
        table.add(mapSelect).padBottom(10);
        table.row();

        // Game mode (for simplicity, we deduce mode from timer: if timer > 0 then Timer mode)
        // But you can add an extra choice if desired.
        // Start button
        TextButton startButton = new TextButton("Start Game", skin);
        table.add(startButton).colspan(2).padTop(20);

        startButton.addListener(event -> {
            if (startButton.isPressed()) {
                GameSettings settings = new GameSettings();
                settings.totalRounds = roundsSelect.getSelected();
                settings.treasureCount = treasureSelect.getSelected();
                try {
                    settings.timerDuration = Float.parseFloat(timerField.getText());
                } catch (NumberFormatException e) {
                    settings.timerDuration = 60;
                }
                settings.gameMode = (settings.timerDuration > 0) ? GameSettings.GameMode.TIMER : GameSettings.GameMode.FIRST_TO_HALF;
                String mapChoice = mapSelect.getSelected();
                settings.mapType = mapChoice.equals("Stored") ? GameSettings.MapType.STORED : GameSettings.MapType.RANDOM;

                // Initialize overall round win counts
                settings.playerRoundsWon = 0;
                settings.aiRoundsWon = 0;

                game.setScreen(new GameScreen(game, settings));
            }
            return false;
        });
    }

    @Override public void show() { }
    @Override public void render(float delta) {
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

