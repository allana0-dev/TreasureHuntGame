package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
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
    /** Dialog for displaying instructions */
    private Dialog instructionsDialog;

    /**
     * Constructs and initializes the start screen.
     * @param game the Main game instance used to transition screens
     */
    public StartScreen(Main game) {
        this.stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        this.skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        this.clickSound = Gdx.audio.newSound(Gdx.files.internal("sfx/buttonclick.ogg"));
        this.bgMusic = Gdx.audio.newMusic(Gdx.files.internal("music/startscreenmusic.ogg"));
        this.bgMusic.setLooping(true);
        this.bgMusic.play();

        // Define a dark brown color for labels (#5A3E1B)
        Color darkBrown = new Color(0.353f, 0.243f, 0.110f, 1f);
        Label.LabelStyle brownStyle = new Label.LabelStyle();
        brownStyle.font = skin.getFont("font");
        brownStyle.fontColor = darkBrown;

        Image background = new Image(new Texture(Gdx.files.internal("ui/startscreenbackground.png")));
        background.setFillParent(true);
        background.addAction(Actions.forever(
            Actions.sequence(
                Actions.moveBy(-10, 0, 5f),
                Actions.moveBy(10, 0, 5f)
            )
        ));
        stage.addActor(background);

        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.pad(30);
        stage.addActor(rootTable);

        Table content = new Table();
        rootTable.add(content).expand().center();

        Image logo = new Image(new Texture(Gdx.files.internal("ui/logo.png")));
        logo.setScaling(Scaling.fit);
        logo.setSize(300, 80);
        logo.addAction(Actions.forever(
            Actions.sequence(
                Actions.moveBy(0, 5, 1f),
                Actions.moveBy(0, -5, 1f)
            )
        ));
        content.add(logo).padBottom(40).row();

        Table form = new Table();
        form.defaults().pad(10).width(300).center();

        form.add(new Label("Number of Rounds (1–3):", brownStyle)).row();
        SelectBox<Integer> rounds = new SelectBox<>(skin);
        rounds.setItems(1, 2, 3);
        rounds.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
            }
        });
        form.add(rounds).row();

        form.add(new Label("Number of Treasures (10–20):", brownStyle)).row();
        SelectBox<Integer> treasures = new SelectBox<>(skin);
        treasures.setItems(10, 15, 20);
        treasures.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
            }
        });
        form.add(treasures).row();

        form.add(new Label("Timer (sec, 0 = off):", brownStyle)).row();
        TextField timer = new TextField("60", skin);
        form.add(timer).row();

        content.add(form).padBottom(30).row();

        TextButton instructions = new TextButton("How to Play", skin);
        instructions.pad(10, 25, 10, 25);
        instructions.getLabel().setFontScale(1f);
        instructions.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
                actor.addAction(Actions.sequence(
                    Actions.scaleTo(1.1f, 1.1f, 0.1f),
                    Actions.scaleTo(1f, 1f, 0.1f)
                ));
                showInstructions();
            }
        });
        content.add(instructions).padBottom(20).row();

        TextButton start = new TextButton("Start Game", skin);
        start.getLabel().setFontScale(1.2f);
        start.pad(15, 40, 15, 40);
        start.addAction(Actions.sequence(
            Actions.alpha(0f),
            Actions.fadeIn(1f),
            Actions.forever(
                Actions.sequence(
                    Actions.scaleTo(1f, 1f, 0.5f),
                    Actions.scaleTo(1.05f, 1.05f, 0.5f)
                )
            )
        ));
        start.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
                bgMusic.stop();
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

        createInstructionsDialog();
    }

    /**
     * Creates the instructions dialog.
     */
    private void createInstructionsDialog() {
        instructionsDialog = new Dialog("How to Play", skin);

        Table contentTable = new Table();
        contentTable.pad(20).defaults().space(10);

        // Instructions text should be white on the dialog
        Label.LabelStyle instructionsStyle = new Label.LabelStyle();
        instructionsStyle.font = skin.getFont("font");
        instructionsStyle.fontColor = Color.WHITE;

        Label instructionsLabel = new Label(
            "🎮 How to Play:\n\n" +
                "- Arrow keys for movement:\n" +
                "  ↑ (Up Arrow): Move Forward\n" +
                "  ↓ (Down Arrow): Move Back\n" +
                "  → (Right Arrow): Move Right\n" +
                "  ← (Left Arrow): Move Left\n\n" +
                "- Press [SPACE] to collect a treasure when near it.\n" +
                "- Treasures are invisible until you pass by them.\n" +
                "- You are playing against an AI, so move fast!\n\n" +
                "Good luck, treasure hunter! :)",
            instructionsStyle
        );
        instructionsLabel.setWrap(true);
        instructionsLabel.setAlignment(Align.left);

        contentTable.add(instructionsLabel).width(400).row();

        TextButton closeButton = new TextButton("Close", skin);
        closeButton.pad(10, 30, 10, 30);
        closeButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                clickSound.play();
                instructionsDialog.hide();
            }
        });

        contentTable.add(closeButton).padTop(20);

        instructionsDialog.getContentTable().add(contentTable);
        instructionsDialog.setMovable(true);
        instructionsDialog.setResizable(true);
    }

    /**
     * Shows the instructions dialog with a fade-in effect.
     */
    private void showInstructions() {
        instructionsDialog.show(stage);
        instructionsDialog.setColor(1, 1, 1, 0);
        instructionsDialog.addAction(Actions.fadeIn(0.3f));
    }

    /** Called when this screen becomes the current screen for a Game. */
    @Override public void show() {}

    /**
     * Renders the stage and its actors.
     * @param delta time in seconds since the last render
     */
    @Override public void render(float delta) {
        stage.act(delta);
        stage.draw();
    }

    /**
     * Resizes the viewport when the window size changes.
     * @param width new width in pixels
     * @param height new height in pixels
     */
    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    /** Called when the game is paused. */
    @Override public void pause() {}

    /** Called when the game is resumed from a paused state. */
    @Override public void resume() {}

    /** Called when this screen is no longer the current screen. */
    @Override public void hide() {}

    /**
     * Disposes of assets when they are no longer needed.
     */
    @Override public void dispose() {
        stage.dispose();
        skin.dispose();
        clickSound.dispose();
        bgMusic.dispose();
    }
}
