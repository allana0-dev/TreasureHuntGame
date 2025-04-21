package com.th.game.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.th.game.Main;
import com.th.game.util.settings.GameSettings;
import com.th.game.screens.StartScreen;

public class EndScreen implements Screen {
    private final Main game;
    private final Stage stage;
    private final Skin skin;
    private final Texture backgroundTexture;
    private Music resultMusic;

    public EndScreen(Main game, int playerRoundsWon, int aiRoundsWon, GameSettings settings) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        this.skin = new Skin(Gdx.files.internal("ui/uiskin.json"));

        backgroundTexture = new Texture(Gdx.files.internal("ui/endscreenbackground.png"));
        Image bgImage = new Image(new TextureRegionDrawable(new TextureRegion(backgroundTexture)));
        bgImage.setFillParent(true);
        bgImage.addAction(Actions.forever(
            Actions.sequence(
                Actions.moveBy(10, 0, 5f),
                Actions.moveBy(-10, 0, 5f)
            )
        ));
        stage.addActor(bgImage);

        Table table = new Table(skin);
        table.setFillParent(true);
        stage.addActor(table);

        String result;
        if (playerRoundsWon > aiRoundsWon) {
            result = "Player is the Overall Champion!";
            resultMusic = Gdx.audio.newMusic(Gdx.files.internal("music/winningmusic.ogg"));
        } else if (aiRoundsWon > playerRoundsWon) {
            result = "AI is the Overall Champion!";
            resultMusic = Gdx.audio.newMusic(Gdx.files.internal("music/lostmusic.ogg"));
        } else {
            result = "The Match is a Tie!";
            resultMusic = Gdx.audio.newMusic(Gdx.files.internal("music/winningmusic.ogg"));
        }

        if (resultMusic != null) {
            resultMusic.setVolume(0.5f);
            resultMusic.play();
        }

        Label title = new Label("GAME OVER", skin);
        title.setFontScale(10f);
        table.add(title).colspan(3).padBottom(40).padTop(60);
        table.row();

        Label roundLabel = new Label("Round", skin);
        roundLabel.setFontScale(3f);
        Label playerLabel = new Label("Player", skin);
        playerLabel.setFontScale(3f);
        Label aiLabel = new Label("AI", skin);
        aiLabel.setFontScale(3f);

        table.add(roundLabel).padRight(20);
        table.add(playerLabel).padRight(20);
        table.add(aiLabel).padBottom(20).row(); // Add space after column titles

        if (settings.playerRoundScores != null && settings.aiRoundScores != null) {
            for (int i = 0; i < settings.playerRoundScores.size(); i++) {
                int p = settings.playerRoundScores.get(i);
                int a = settings.aiRoundScores.get(i);

                Label roundText = new Label("Round " + (i + 1), skin);
                roundText.setFontScale(2.5f);

                Label playerScore = new Label(Integer.toString(p), skin);
                playerScore.setFontScale(2.5f);

                Label aiScore = new Label(Integer.toString(a), skin);
                aiScore.setFontScale(2.5f);

                table.add(roundText).padRight(10);
                table.add(playerScore).padRight(10);
                table.add(aiScore).padBottom(15).row(); // Space between each round row
            }
        } else {
            table.add(new Label("Player Rounds Won: " + playerRoundsWon, skin)).colspan(3).row();
            table.add(new Label("AI Rounds Won: " + aiRoundsWon, skin)).colspan(3).row();
        }

        table.add().height(30).row(); // Extra space before totals

        Label totalRoundsLabel = new Label("Total Rounds Won:", skin);
        totalRoundsLabel.setFontScale(3f);
        Label playerRoundsLabel = new Label(Integer.toString(playerRoundsWon), skin);
        playerRoundsLabel.setFontScale(3f);
        Label aiRoundsLabel = new Label(Integer.toString(aiRoundsWon), skin);
        aiRoundsLabel.setFontScale(3f);

        table.add(totalRoundsLabel).padBottom(10).padTop(10);
        table.add(playerRoundsLabel).padBottom(10).padTop(10);
        table.add(aiRoundsLabel).padBottom(10).padTop(10).row();

        table.add().colspan(3).height(60).row(); // Extra space between scores and result

        Label outcome = new Label(result, skin);
        outcome.setFontScale(3f);
        table.add(outcome).colspan(3).padBottom(20).row();

        TextButton restart = new TextButton("Restart", skin);
        TextButton quit = new TextButton("Quit", skin);
        restart.pad(30f);
        quit.pad(30f);
        restart.getLabel().setFontScale(2.5f);
        quit.getLabel().setFontScale(2.5f);

        restart.getLabel().setColor(1, 0.5f, 0, 1); // orange
        quit.getLabel().setColor(1, 0, 0, 1);       // red

        float buttonSize = 400;

        restart.setSize(buttonSize, buttonSize);
        quit.setSize(buttonSize, buttonSize);

        table.add(restart).padRight(20).padBottom(20).fill().uniform();
        table.add(quit).padBottom(20).fill().uniform();

        restart.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                resultMusic.stop();
                game.setScreen(new StartScreen(game));
            }
        });
        quit.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.exit();
            }
        });
    }

    @Override public void show() { }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int w, int h) {
        stage.getViewport().update(w, h, true);
    }

    @Override public void pause()  { }
    @Override public void resume() { }
    @Override public void hide()   { }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        backgroundTexture.dispose();
        if (resultMusic != null) resultMusic.dispose();
    }
}


