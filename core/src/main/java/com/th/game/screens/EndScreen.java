package com.th.game.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.th.game.util.settings.GameSettings;
import com.th.game.Main;

public class EndScreen implements Screen {

    private Main game;
    private Stage stage;
    private Skin skin;
    private Texture backgroundTexture;

    public EndScreen(Main game, int playerRoundsWon, int aiRoundsWon, GameSettings settings) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(this.stage);
        this.skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        this.backgroundTexture = new Texture(Gdx.files.internal("ui/endscreenbackground.png"));

        Image backgroundImage = new Image(this.backgroundTexture);
        backgroundImage.setFillParent(true);
        this.stage.addActor(backgroundImage);

        Table table = new Table();
        table.setFillParent(true);
        this.stage.addActor(table);

        String result;
        if (playerRoundsWon > aiRoundsWon) {
            result = "Player is the Overall Champion!";
        } else if (aiRoundsWon > playerRoundsWon) {
            result = "AI is the Overall Champion!";
        } else {
            result = "The Match is a Tie!";
        }

        // Use default style instead of "title"
        Label titleLabel = new Label("Game Over", skin);
        titleLabel.setFontScale(10.0f); // Make it bigger instead of using a special style
        table.add(titleLabel).colspan(3).padBottom(30);
        table.row();

        Pixmap pixmap = new Pixmap(1, 1, Format.RGBA8888);
        pixmap.setColor(new Color(0.36F, 0.25F, 0.2F, 1.0F));
        pixmap.fill();
        TextureRegionDrawable background = new TextureRegionDrawable(new TextureRegion(new Texture(pixmap)));
        BitmapFont font = new BitmapFont();
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        labelStyle.background = background;

        Label roundLbl = new Label("Rounds", labelStyle);
        roundLbl.setStyle(new Label.LabelStyle(roundLbl.getStyle()));
        roundLbl.setFontScale(2.0F);
        table.add(roundLbl).padRight(10.0F);

        Label playerScoreLbl = new Label("Player Score", this.skin);
        playerScoreLbl.setStyle(new Label.LabelStyle(playerScoreLbl.getStyle()));
        playerScoreLbl.setFontScale(2.0F);
        table.add(playerScoreLbl).padRight(10.0F);

        Label aiScoreLbl = new Label("AI Score", this.skin);
        aiScoreLbl.setStyle(new Label.LabelStyle(aiScoreLbl.getStyle()));
        aiScoreLbl.setFontScale(2.0F);
        table.add(aiScoreLbl).padBottom(10.0F);
        table.row();
        table.add(new Label("----", this.skin)).padRight(10.0F);
        if (settings.playerRoundScores != null && settings.aiRoundScores != null) {
            for (int i = 0; i < settings.playerRoundScores.size(); ++i) {
                table.row();
                int playerScore = (Integer) settings.playerRoundScores.get(i);
                int aiScore = (Integer) settings.aiRoundScores.get(i);
                
                
                Label roundLabel = new Label("Round " + (i + 1), this.skin);
                roundLabel.setStyle(new Label.LabelStyle(roundLabel.getStyle()));
                roundLabel.setFontScale(2.0F);
                table.add(roundLabel).padRight(10.0F);
                
                Label playerScoreLabel = new Label(Integer.toString(playerScore), this.skin);
                playerScoreLabel.setStyle(new Label.LabelStyle(playerScoreLabel.getStyle()));
                playerScoreLabel.setFontScale(2.0F);
                playerScore.setFontScale(2.0f);
                table.add(playerScoreLabel).padRight(10.0F);
                
                Label aiScoreLabel = new Label(Integer.toString(aiScore), this.skin);
                aiScoreLabel.setStyle(new Label.LabelStyle(aiScoreLabel.getStyle()));
                aiScoreLabel.setFontScale(2.0f);
                aiScore.setFontScale(2.0f);
                table.add(aiScoreLabel).padBottom(10.0F);
            }
        } else {
            table.row();
            table.add(new Label("Player Rounds Won: " + playerRoundsWon, this.skin)).colspan(3).padBottom(10.0F);
            table.row();
            table.add(new Label("AI Rounds Won: " + aiRoundsWon, this.skin)).colspan(3).padBottom(10.0F);
        }

        table.row();
        table.add().height(20.0F);
        table.row();
        
        Label totalLabel = new Label("Total Rounds Won:", this.skin);
        totalLabel.setStyle(new Label.LabelStyle(totalLabel.getStyle()));
        totalLabel.setFontScale(2.2F);
        table.add(totalLabel).colspan(1).padBottom(10.0F).padTop(10.0F);
        table.add(new Label(Integer.toString(playerRoundsWon), this.skin)).padBottom(10.0F).padTop(10.0F);
        table.add(new Label(Integer.toString(aiRoundsWon), this.skin)).padBottom(10.0F).padTop(10.0F);
        table.row();
        
        Label resultLabel = new Label(result, this.skin);
        resultLabel.setFontScale(2.5F);
        table.add(resultLabel).colspan(3).padBottom(20.0F).pad(20.0F).width(300.0F).height(80.0F);
        table.row();
        
        TextButton restartButton = new TextButton("Restart", this.skin);
        restartButton.getLabel().setFontScale(4.5F);
        restartButton.getLabel().setColor(Color.ORANGE);
        
        TextButton quitButton = new TextButton("Quit", this.skin);
        quitButton.getLabel().setFontScale(4.5F);
        quitButton.getLabel().setColor(Color.RED);
        
        table.add(restartButton).padRight(20.0F).colspan(1);
        table.add(quitButton).colspan(2);
        restartButton.addListener((event) -> {
            if (restartButton.isPressed()) {
                game.setScreen(new StartScreen(game));
            }

            return false;
        });
        quitButton.addListener((event) -> {
            if (quitButton.isPressed()) {
                Gdx.app.exit();
            }

            return false;
        });
    }

    public void show() {
    }

    public void render(float delta) {
        Gdx.gl.glClear(16384);
        this.stage.act(delta);
        this.stage.draw();
    }

    public void resize(int width, int height) {
        this.stage.getViewport().update(width, height, true);
    }

    public void pause() {
    }

    public void resume() {
    }

    public void hide() {
    }

    public void dispose() {
        this.stage.dispose();
        this.skin.dispose();
    }
}
