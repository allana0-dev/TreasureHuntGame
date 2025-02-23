package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
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

        Label title = new Label("AI Treasure Hunt", skin);
        table.add(title).colspan(2).padBottom(20);
        table.row();

        // Treasure count selection
        table.add(new Label("Number of Treasures:", skin));
        SelectBox<Integer> treasureSelect = new SelectBox<>(skin);
        treasureSelect.setItems(5, 10, 15);
        table.add(treasureSelect).padBottom(10);
        table.row();

        // Game mode selection
        table.add(new Label("Game Mode:", skin));
        SelectBox<String> modeSelect = new SelectBox<>(skin);
        modeSelect.setItems("Timer Mode", "First-to-Half Mode");
        table.add(modeSelect).padBottom(10);
        table.row();

        // Timer duration (only used for Timer Mode)
        table.add(new Label("Timer Duration (sec):", skin));
        TextField timerField = new TextField("60", skin);
        table.add(timerField).padBottom(10);
        table.row();

        // Start button
        TextButton startButton = new TextButton("Start Game", skin);
        table.add(startButton).colspan(2).padTop(20);

        startButton.addListener(event -> {
            if (startButton.isPressed()) {
                GameSettings settings = new GameSettings();
                settings.treasureCount = treasureSelect.getSelected();
                String mode = modeSelect.getSelected();
                if (mode.equals("Timer Mode")) {
                    settings.gameMode = GameSettings.GameMode.TIMER;
                    try {
                        settings.timerDuration = Float.parseFloat(timerField.getText());
                    } catch (NumberFormatException e) {
                        settings.timerDuration = 60;
                    }
                } else {
                    settings.gameMode = GameSettings.GameMode.FIRST_TO_HALF;
                    settings.timerDuration = 0;
                }
                game.setScreen(new GameScreen(game, settings));
            }
            return false;
        });
    }

    @Override public void show() { }
    @Override
    public void render(float delta) {
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
