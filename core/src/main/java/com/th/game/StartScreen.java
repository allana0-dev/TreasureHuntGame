package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class StartScreen implements Screen {
    private final Main game;
    private final Stage stage;
    private final Skin skin;

    public StartScreen(Main game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        this.skin = new Skin(Gdx.files.internal("ui/uiskin.json"));

        // 🌴 Background
        Texture backgroundTexture = new Texture(Gdx.files.internal("ui/Background2.png"));
        Image backgroundImage = new Image(backgroundTexture);
        backgroundImage.setFillParent(true);
        stage.addActor(backgroundImage);

        // 📦 Root layout
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        stage.addActor(rootTable);

        // 📐 Center column content
        Table content = new Table();
        rootTable.add(content).expand().top().padTop(60); // Shifted up

        // 🪧 Logo with bounce effect
        Texture logoTexture = new Texture(Gdx.files.internal("ui/Scout Logo for game_1@4x.png"));
        Image logoImage = new Image(logoTexture);
        logoImage.setScaling(Scaling.fit);
        logoImage.setSize(300, 80);
        logoImage.setColor(1f, 1f, 1f, 1f);

        // Bouncing animation
        logoImage.addAction(Actions.forever(Actions.sequence(
            Actions.moveBy(0, 5, 1f),
            Actions.moveBy(0, -5, 1f)
        )));

        content.add(logoImage).center().padBottom(40).row();

        // 🧾 Form
        Table formTable = new Table();
        formTable.defaults().pad(10).width(300).center();

        // Number of Rounds
        formTable.add(new Label("Number of Rounds (1, 2, or 3):", skin)).center().row();
        SelectBox<Integer> roundsSelect = new SelectBox<>(skin);
        roundsSelect.setItems(1, 2, 3);
        formTable.add(roundsSelect).width(220).center().row();

        // Number of Treasures
        formTable.add(new Label("Number of Treasures (10, 15, or 20):", skin)).center().row();
        SelectBox<Integer> treasureSelect = new SelectBox<>(skin);
        treasureSelect.setItems(5, 10, 15);
        formTable.add(treasureSelect).width(220).center().row();

        // Timer
        formTable.add(new Label("Timer Duration (sec, 0 = no timer):", skin)).center().row();
        TextField timerField = new TextField("60", skin);
        formTable.add(timerField).width(220).center().row();

        content.add(formTable).center().padBottom(30).row();

        // ▶ Start Button
        TextButton startButton = new TextButton("Start Game", skin);
        TextButton.TextButtonStyle bstyle = skin.get(TextButton.TextButtonStyle.class);
        bstyle.font.getData().setScale(1.2f);
        startButton.setStyle(bstyle);
        startButton.getLabel().setColor(Color.WHITE);
        startButton.pad(15, 40, 15, 40);
        startButton.setColor(Color.valueOf("#333333"));

        startButton.addAction(Actions.sequence(
            Actions.alpha(0f),
            Actions.fadeIn(1f),
            Actions.forever(Actions.sequence(
                Actions.scaleTo(1f, 1f, 0.5f),
                Actions.scaleTo(1.05f, 1.05f, 0.5f)
            ))
        ));

        // 🎮 Start Game Logic
        startButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                actor.addAction(Actions.sequence(
                    Actions.scaleTo(1.1f, 1.1f, 0.1f),
                    Actions.scaleTo(1f, 1f, 0.1f)
                ));

                GameSettings settings = new GameSettings();
                settings.totalRounds = roundsSelect.getSelected();
                settings.treasureCount = treasureSelect.getSelected();

                try {
                    settings.timerDuration = Float.parseFloat(timerField.getText());
                } catch (NumberFormatException e) {
                    settings.timerDuration = 60;
                }

                settings.gameMode = (settings.timerDuration > 0)
                    ? GameSettings.GameMode.TIMER
                    : GameSettings.GameMode.FIRST_TO_HALF;

                settings.mapType = GameSettings.MapType.RANDOM;
                settings.playerRoundsWon = 0;
                settings.aiRoundsWon = 0;

                game.setScreen(new GameScreen(game, settings));
            }
        });

        content.add(startButton).center().padTop(10);
    }

    @Override public void show() {}
    @Override public void render(float delta) {
        stage.act(delta);
        stage.draw();
    }
    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() {
        stage.dispose();
        skin.dispose();
    }
}





