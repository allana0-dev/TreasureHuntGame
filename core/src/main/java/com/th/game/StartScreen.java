package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * The StartScreen class represents the main menu screen of the game.
 * It allows the player to configure game settings such as number of rounds,
 * treasure count, and timer, and then start the game.
 */
public class StartScreen implements Screen {
    /** Stage for UI elements */
    private final Stage stage;
    /** Skin for styling UI widgets */
    private final Skin skin;
    /** Sound effect played on button or dropdown interactions */
    private final Sound clickSound;
    /** Background music for the start screen */
    private final Music bgMusic;

    /**
     * Constructs and initializes the start screen.
     * @param game the Main game instance used to transition screens
     */
    public StartScreen(Main game) {
        this.stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        // load UI skin
        this.skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        // load click sound
        this.clickSound = Gdx.audio.newSound(Gdx.files.internal("sfx/buttonclick.ogg"));
        // load and play background music
        this.bgMusic = Gdx.audio.newMusic(Gdx.files.internal("music/startscreenmusic.ogg"));
        this.bgMusic.setLooping(true);
        this.bgMusic.play();

        // 🌴 Background
        Texture backgroundTexture = new Texture(Gdx.files.internal("ui/Background2.png"));
        Image backgroundImage = new Image(backgroundTexture);
        backgroundImage.setFillParent(true);
        stage.addActor(backgroundImage);

        // 📦 Root layout with 30px margin
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.pad(30);
        stage.addActor(rootTable);

        // 📐 Center column
        Table content = new Table();
        rootTable.add(content).expand().top().padTop(20);

        // 🪧 Logo with bounce
        Texture logoTex = new Texture(Gdx.files.internal("ui/Scout Logo for game_1@4x.png"));
        Image logo = new Image(logoTex);
        logo.setScaling(Scaling.fit);
        logo.setSize(300, 80);
        logo.addAction(Actions.forever(
            Actions.sequence(
                Actions.moveBy(0, 5, 1f),
                Actions.moveBy(0, -5, 1f)
            )
        ));
        content.add(logo).center().padBottom(40).row();

        // 🧾 Form inputs
        Table form = new Table();
        form.defaults().pad(10).width(300).center();

        // Rounds dropdown
        form.add(new Label("Number of Rounds (1–3):", skin)).row();
        SelectBox<Integer> rounds = new SelectBox<>(skin);
        rounds.setItems(1, 2, 3);
        rounds.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                clickSound.play();
            }
        });
        form.add(rounds).row();

        // Treasures dropdown
        form.add(new Label("Number of Treasures (5–15):", skin)).row();
        SelectBox<Integer> treasures = new SelectBox<>(skin);
        treasures.setItems(5, 10, 15);
        treasures.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                clickSound.play();
            }
        });
        form.add(treasures).row();

        // Timer input
        form.add(new Label("Timer (sec, 0 = off):", skin)).row();
        TextField timer = new TextField("60", skin);
        form.add(timer).row();

        content.add(form).padBottom(30).row();

        // ▶ Start Game button
        TextButton start = new TextButton("Start Game", skin);
        start.getLabel().setFontScale(1.2f);
        start.pad(15, 40, 15, 40);

        // Fade‑in + subtle pulse
        start.addAction(Actions.sequence(
            Actions.alpha(0f),
            Actions.fadeIn(1f),
            Actions.forever(Actions.sequence(
                Actions.scaleTo(1f, 1f, 0.5f),
                Actions.scaleTo(1.05f, 1.05f, 0.5f)
            ))
        ));

        start.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                // play click sound
                clickSound.play();
                // stop background music
                bgMusic.stop();
                // visual feedback
                actor.addAction(Actions.sequence(
                    Actions.scaleTo(1.1f, 1.1f, 0.1f),
                    Actions.scaleTo(1f, 1f, 0.1f)
                ));

                GameSettings cfg = new GameSettings();
                cfg.totalRounds   = rounds.getSelected();
                cfg.treasureCount = treasures.getSelected();

                try {
                    cfg.timerDuration = Float.parseFloat(timer.getText());
                } catch (NumberFormatException e) {
                    cfg.timerDuration = 60;
                }

                cfg.gameMode = (cfg.timerDuration > 0)
                    ? GameSettings.GameMode.TIMER
                    : GameSettings.GameMode.FIRST_TO_HALF;
                cfg.mapType = GameSettings.MapType.RANDOM;
                cfg.playerRoundsWon = cfg.aiRoundsWon = 0;

                game.setScreen(new GameScreen(game, cfg));
            }
        });

        content.add(start).row();
    }

    /** Called when this screen becomes the current screen for a Game. */
    @Override
    public void show() {
    }

    /**
     * Renders the stage and its actors.
     * @param delta time in seconds since the last render
     */
    @Override
    public void render(float delta) {
        stage.act(delta);
        stage.draw();
    }

    /**
     * Resizes the viewport when the window size changes.
     * @param width new width in pixels
     * @param height new height in pixels
     */
    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    /** Called when the game is paused. */
    @Override
    public void pause() {
    }

    /** Called when the game is resumed from a paused state. */
    @Override
    public void resume() {
    }

    /** Called when this screen is no longer the current screen. */
    @Override
    public void hide() {
    }

    /**
     * Disposes of assets when they are no longer needed.
     */
    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        clickSound.dispose();
        bgMusic.dispose();
    }
}
