package com.th.game.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.th.game.util.settings.GameSettings;
import com.th.game.Main;
import com.th.game.util.map.MapManager;

import java.util.List;

/**
 * StartScreen shows the main menu where players set up a new game.
 * Players can choose number of rounds, treasures, timer, map, and hints.
 * Also provides buttons to start or quit the game, and a dialog for instructions.
 */
public class StartScreen implements Screen {
    private final Main game;
    private final Stage stage;
    private final Skin skin;
    private final Sound clickSound;
    private final Music bgMusic;
    private Dialog instructionsDialog;

    /**
     * Constructor sets up all UI widgets, background, music, and input.
     * @param game The main game instance, used for screen switching.
     */
    public StartScreen(Main game) {
        this.game = game;

        // Load VisUI skin once
        if (!VisUI.isLoaded()) {
            VisUI.load();
        }
        this.skin = VisUI.getSkin();

        // Prepare stage and input
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        // Load click sound and background music
        clickSound = Gdx.audio.newSound(Gdx.files.internal("sfx/buttonclick.ogg"));
        bgMusic = Gdx.audio.newMusic(Gdx.files.internal("music/startscreenmusic.ogg"));
        bgMusic.setLooping(true);
        bgMusic.play();

        // Animated background image
        Image background = new Image(new Texture(Gdx.files.internal("ui/startscreenbackground.png")));
        background.setFillParent(true);
        background.addAction(Actions.forever(
            Actions.sequence(
                Actions.moveBy(-10, 0, 5f),
                Actions.moveBy(10, 0, 5f)
            )
        ));
        stage.addActor(background);

        // Root layout table with padding
        Table root = new Table();
        root.setFillParent(true);
        root.pad(30);
        stage.addActor(root);

        // Animated logo at top
        Image logo = new Image(new Texture(Gdx.files.internal("ui/logo.png")));
        logo.setScaling(Scaling.fit);
        logo.setSize(300, 80);
        logo.addAction(Actions.forever(
            Actions.sequence(
                Actions.moveBy(0, 5, 1f),
                Actions.moveBy(0, -5, 1f)
            )
        ));
        root.add(logo).padBottom(40).row();

        // Form table for settings inputs
        Table form = new Table();
        form.defaults().pad(5).width(300).center();

        Color darkBrown = new Color(0.353f, 0.243f, 0.110f, 1f);

        // Number of Rounds selector
        VisLabel roundsLabel = new VisLabel("Number of Rounds:");
        roundsLabel.setColor(darkBrown);
        form.add(roundsLabel).row();
        VisSelectBox<Integer> rounds = new VisSelectBox<>();
        rounds.setItems(1, 2, 3);
        rounds.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
            }
        });
        form.add(rounds).row();

        // Number of Treasures selector
        VisLabel treasuresLabel = new VisLabel("Number of Treasures:");
        treasuresLabel.setColor(darkBrown);
        form.add(treasuresLabel).row();
        VisSelectBox<Integer> treasures = new VisSelectBox<>();
        treasures.setItems(10, 15, 20);
        treasures.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
            }
        });
        form.add(treasures).row();

        // Timer text field
        VisLabel timerLabel = new VisLabel("Timer (sec, 0 = off):");
        timerLabel.setColor(darkBrown);
        form.add(timerLabel).row();
        VisTextField timer = new VisTextField("60");
        form.add(timer).row();

        // Map choice: Random or Stored
        VisLabel mapChoiceLabel = new VisLabel("Map Choice:");
        mapChoiceLabel.setColor(darkBrown);
        form.add(mapChoiceLabel).row();
        VisSelectBox<String> mapType = new VisSelectBox<>();
        mapType.setItems("Random", "Custom");
        VisSelectBox<String> customMaps = new VisSelectBox<>();
        mapType.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
                customMaps.setVisible(mapType.getSelected().equals("Custom"));
            }
        });
        form.add(mapType).row();

        // Custom map selector, initially hidden
        List<String> names = MapManager.getMapNames();
        customMaps.setItems(names.toArray(new String[0]));
        customMaps.setVisible(false);
        customMaps.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
            }
        });
        form.add(customMaps).row();

        // Enable hints checkbox
        VisCheckBox hintCheckBox = new VisCheckBox(" Enable Hints");
        hintCheckBox.setChecked(false);
        hintCheckBox.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
            }
        });
        form.add(hintCheckBox).row();

        root.add(form).padBottom(10).row();

        // "How to Play" instructions button
        VisTextButton instrBtn = new VisTextButton("How to Play");
        instrBtn.pad(5f, 40f, 5f, 40f);
        instrBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
                showInstructions();
            }
        });
        root.add(instrBtn).padBottom(20).row();

        // Start and Quit buttons layout
        VisTextButton startBtn = new VisTextButton("Start Game");
        startBtn.pad(20f, 40f, 20f, 40f);
        startBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
                bgMusic.stop();

                GameSettings cfg = new GameSettings();
                cfg.totalRounds   = rounds.getSelected();
                cfg.treasureCount = treasures.getSelected();
                try {
                    cfg.timerDuration = Float.parseFloat(timer.getText());
                } catch (NumberFormatException e) {
                    cfg.timerDuration = 60;
                }
                cfg.gameMode = cfg.timerDuration > 0
                    ? GameSettings.GameMode.TIMER
                    : GameSettings.GameMode.FIRST_TO_HALF;

                if (mapType.getSelected().equals("Custom")) {
                    cfg.mapType = GameSettings.MapType.STORED;
                    cfg.selectedMapName = customMaps.getSelected();
                } else {
                    cfg.mapType = GameSettings.MapType.RANDOM;
                }
                cfg.hintsEnabled  = hintCheckBox.isChecked();
                cfg.playerRoundsWon = cfg.aiRoundsWon = 0;
                game.setScreen(new GameScreen(game, cfg));
            }
        });

        VisTextButton quitBtn = new VisTextButton("Quit Game");
        quitBtn.pad(20f, 40f, 20f, 40f);
        quitBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
                Gdx.app.exit();
            }
        });

        Table buttonTable = new Table();
        buttonTable.add(startBtn).pad(10).fillX();
        buttonTable.add(quitBtn).pad(10).fillX();
        root.add(buttonTable).padBottom(20).row();

        createInstructionsDialog();
    }

    /**
     * Creates the dialog that shows how to play the game.
     */
    private void createInstructionsDialog() {
        instructionsDialog = new Dialog("How to Play", skin);
        instructionsDialog.pad(40f);
        instructionsDialog.text(
            "🎮 How to Play:\n\n" +
                "- Arrow keys to move\n" +
                "- Press [SPACE] to collect when near\n" +
                "- Treasures appear only when nearby\n" +
                "- Race the AI to collect more treasures!"
        );
        instructionsDialog.button("Close");
    }

    /**
     * Displays the instructions dialog on screen.
     */
    private void showInstructions() {
        instructionsDialog.show(stage);
    }

    /**
     * Called when this screen becomes the active screen; no-op.
     */
    @Override
    public void show() { }

    /**
     * Renders the stage every frame.
     * @param delta Time in seconds since last frame
     */
    @Override
    public void render(float delta) {
        stage.act(delta);
        stage.draw();
    }

    /**
     * Called when the screen size changes; updates viewport.
     * @param width New screen width
     * @param height New screen height
     */
    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    /**
     * Called when the application is paused; no-op.
     */
    @Override
    public void pause() { }

    /**
     * Called when the application is resumed; no-op.
     */
    @Override
    public void resume() { }

    /**
     * Called when this screen is no longer active; no-op.
     */
    @Override
    public void hide() { }

    /**
     * Releases all resources when the screen is destroyed.
     */
    @Override
    public void dispose() {
        stage.dispose();
        VisUI.dispose();
        clickSound.dispose();
        bgMusic.dispose();
    }
}
