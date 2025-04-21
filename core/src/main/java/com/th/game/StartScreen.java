package com.th.game;

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
import com.th.game.util.map.MapManager;
import com.th.game.screens.GameScreen;

import java.util.List;

/**
 * The StartScreen class represents the main menu screen of the game.
 * It allows the player to configure settings: rounds, treasures, timer,
 * map choice, and hints, before starting the game.
 */
public class StartScreen implements Screen {
    private final Main game;
    private final Stage stage;
    private final Skin skin;
    private final Sound clickSound;
    private final Music bgMusic;
    private Dialog instructionsDialog;

    public StartScreen(Main game) {
        this.game = game;

        if (!VisUI.isLoaded()) {
            VisUI.load();
        }
        this.skin = VisUI.getSkin();

        // Set up stage and input
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        // Load sounds and music
        clickSound = Gdx.audio.newSound(Gdx.files.internal("sfx/buttonclick.ogg"));
        bgMusic    = Gdx.audio.newMusic(Gdx.files.internal("music/startscreenmusic.ogg"));
        bgMusic.setLooping(true);
        bgMusic.play();

        // Background image with pan animation
        Image background = new Image(new Texture(Gdx.files.internal("ui/startscreenbackground.png")));
        background.setFillParent(true);
        background.addAction(Actions.forever(
            Actions.sequence(
                Actions.moveBy(-10, 0, 5f),
                Actions.moveBy(10, 0, 5f)
            )
        ));
        stage.addActor(background);

        // Root layout table
        Table root = new Table();
        root.setFillParent(true);
        root.pad(30);
        stage.addActor(root);

        // Logo
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

        Table form = new Table();
        form.defaults().pad(5).width(300).center();

        Color darkBrown = new Color(0.353f, 0.243f, 0.110f, 1f);

        // Rounds
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

        // Treasures
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

        // Timer
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
                // toggle custom map selector
                boolean custom = mapType.getSelected().equals("Custom");
                customMaps.setVisible(custom);
            }
        });
        form.add(mapType).row();

        // Custom map selector (initially hidden)
        // Assume MapManager.getAvailableMapNames() returns List<String>
        List<String> names = MapManager.getMapNames();
        customMaps.setItems(names.toArray(new String[0]));
        customMaps.setVisible(false);
        customMaps.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
            }
        });
        form.add(customMaps).row();

        // Hints enabled
        VisCheckBox hintCheckBox = new VisCheckBox(" Enable Hints");
        hintCheckBox.setChecked(false);
        hintCheckBox.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
            }
        });
        form.add(hintCheckBox).row();

        root.add(form).padBottom(10).row();

        // Instructions
        VisTextButton instrBtn = new VisTextButton("How to Play");
        instrBtn.pad(5f, 40f, 5f, 40f);
        instrBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
                showInstructions();
            }
        });
        root.add(instrBtn).padBottom(20).row();

        // Start Game
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
                cfg.gameMode      = cfg.timerDuration > 0
                    ? GameSettings.GameMode.TIMER
                    : GameSettings.GameMode.FIRST_TO_HALF;

                // map settings
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




        root.add(startBtn);

        createInstructionsDialog();
    }

    private void createInstructionsDialog() {
        instructionsDialog = new Dialog("",skin);
        instructionsDialog.pad(40f);
        instructionsDialog.text(
            "🎮 How to Play:\n\n" +
                "- Arrow keys to move\n" +
                "- Press [SPACE] to collect when near\n" +
                "- Treasures are invisible until nearby\n" +
                "- Move fast, compete vs AI!"
        );
        instructionsDialog.button("Close");
    }

    private void showInstructions() {
        instructionsDialog.show(stage);
    }

    @Override public void show() {}
    @Override public void render(float delta) { stage.act(delta); stage.draw(); }
    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() {
        stage.dispose();
        VisUI.dispose();
        clickSound.dispose();
        bgMusic.dispose();
    }
}
