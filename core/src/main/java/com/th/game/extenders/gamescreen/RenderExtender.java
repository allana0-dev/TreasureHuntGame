package com.th.game.extenders.gamescreen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.th.game.entities.TreasureChest;
import com.th.game.screens.GameScreen;
import com.th.game.util.settings.GameSettings;

public class RenderExtender {

    private final GameScreen gameScreen;
    private static final float HINT_THRESHOLD = 150f;
    private static final float ROUND_POPUP_DURATION = 2.5f;

    public RenderExtender(GameScreen gameScreen) {
        this.gameScreen = gameScreen;
    }
    /**
     * Main render method that handles all game rendering operations.
     * Updates game logic, clears screen, renders all game elements, and manages visual effects.
     *
     * @param delta Time since last frame in seconds
     */
    public void render(float delta) {
        // Update countdown timer only when in TIMER mode
        if (gameScreen.settings.gameMode == GameSettings.GameMode.TIMER && gameScreen.gameStarted) {
            gameScreen.countdownTimer -= delta;
            if (gameScreen.countdownTimer <= 0) {
                gameScreen.countdownTimer = 0;
                gameScreen.endRound();
            }
        }

        // Update game logic
        gameScreen.update(delta);

        // Clear screen
        clearScreen();

        // Update camera and render map
        updateCameraAndRenderMap();

        // Begin sprite batch rendering
        beginBatchRendering();

        // Render game elements
        renderTreasureChests();
        renderPlayer();
        renderAI();
        renderSpeedBoostEffect(delta);
        renderHUD();
        renderRoundPopup(delta);
        renderCountdown(delta);
        renderHints();

        // End batch rendering
        endBatchRendering();
    }

    /**
     * Clears the screen with a blue background color.
     */
    private void clearScreen() {
        Gdx.gl.glClearColor(0, 0, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    /**
     * Updates the camera's matrices and renders the tiled map.
     * This sets up the view for proper map rendering.
     */
    private void updateCameraAndRenderMap() {
        gameScreen.camera.update();
        gameScreen.mapRenderer.setView(gameScreen.camera);
        gameScreen.mapRenderer.render();
    }

    /**
     * Begins the sprite batch rendering process by setting up the projection matrix.
     */
    private void beginBatchRendering() {
        gameScreen.batch.setProjectionMatrix(gameScreen.camera.combined);
        gameScreen.batch.begin();
    }

    /**
     * Ends the current sprite batch rendering operation.
     */
    private void endBatchRendering() {
        gameScreen.batch.end();
    }

    /**
     * Renders all treasure chests with appropriate visibility based on proximity to player.
     * Closed chests become more visible as player approaches.
     */
    private void renderTreasureChests() {
        for (TreasureChest chest : gameScreen.treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED) {
                float dist = gameScreen.player.position.dst(chest.position);
                if (dist < HINT_THRESHOLD) {
                    float alpha = 1f - (dist / HINT_THRESHOLD);
                    chest.render(gameScreen.batch, alpha);
                }
            } else {
                chest.render(gameScreen.batch, 1f);
            }
        }
    }

    /**
     * Renders the player sprite with appropriate directional animation and "YOU" label.
     */
    private void renderPlayer() {
        gameScreen.batch.setColor(Color.WHITE);
        TextureRegion playerFrame;
        switch (gameScreen.playerDirection) {
            case LEFT:
                playerFrame = gameScreen.playerWalkLeft.getKeyFrame(gameScreen.playerStateTime, true);
                break;
            case RIGHT:
                playerFrame = gameScreen.playerWalkRight.getKeyFrame(gameScreen.playerStateTime, true);
                break;
            case UP:
                playerFrame = gameScreen.playerWalkUp.getKeyFrame(gameScreen.playerStateTime, true);
                break;
            case DOWN:
            default:
                playerFrame = gameScreen.playerWalkDown.getKeyFrame(gameScreen.playerStateTime, true);
                break;
        }
        gameScreen.batch.draw(playerFrame, gameScreen.player.position.x, gameScreen.player.position.y, 40, 50);

        // Draw "YOU" label above player
        gameScreen.font.setColor(Color.GREEN);
        gameScreen.font.draw(gameScreen.batch, "YOU", gameScreen.player.position.x + 10, gameScreen.player.position.y + 70);
        gameScreen.font.setColor(Color.WHITE);
    }

    /**
     * Renders the AI sprite with appropriate directional animation and "AI" label.
     */
    private void renderAI() {
        TextureRegion aiFrame;
        switch (gameScreen.aiDirection) {
            case LEFT:
                aiFrame = gameScreen.aiWalkLeft.getKeyFrame(gameScreen.aiStateTime, true);
                break;
            case RIGHT:
                aiFrame = gameScreen.aiWalkRight.getKeyFrame(gameScreen.aiStateTime, true);
                break;
            case UP:
                aiFrame = gameScreen.aiWalkUp.getKeyFrame(gameScreen.aiStateTime, true);
                break;
            case DOWN:
            default:
                aiFrame = gameScreen.aiWalkDown.getKeyFrame(gameScreen.aiStateTime, true);
                break;
        }
        gameScreen.batch.draw(aiFrame, gameScreen.ai.position.x, gameScreen.ai.position.y, 64, 64);

        // Draw "AI" label above the AI
        gameScreen.font.setColor(Color.RED);
        gameScreen.font.draw(gameScreen.batch, "AI", gameScreen.ai.position.x + 25, gameScreen.ai.position.y + 80);
        gameScreen.font.setColor(Color.WHITE);
    }


    /**
     * Renders the AI speed boost visual effect with pulsing animation.
     *
     * @param delta Time since last frame for effect animation
     */
    private void renderSpeedBoostEffect(float delta) {
        if (gameScreen.showingAISpeedBoost) {
            gameScreen.speedBoostEffectTimer += Gdx.graphics.getDeltaTime();
            updatePulseEffect(delta);

            if (gameScreen.speedBoostEffectTimer >= gameScreen.SPEED_BOOST_EFFECT_DURATION) {
                gameScreen.showingAISpeedBoost = false;
            } else {
                drawSpeedBoostGlow();
            }
        }
    }

    /**
     * Updates the pulse effect alpha value for speed boost animation.
     *
     * @param delta Time since last frame for smooth animation
     */
    private void updatePulseEffect(float delta) {
        if (gameScreen.pulseDirectionUp) {
            gameScreen.pulseAlpha += delta * 1.5f;
            if (gameScreen.pulseAlpha >= 0.8f) {
                gameScreen.pulseAlpha = 0.8f;
                gameScreen.pulseDirectionUp = false;
            }
        } else {
            gameScreen.pulseAlpha -= delta * 1.5f;
            if (gameScreen.pulseAlpha <= 0.4f) {
                gameScreen.pulseAlpha = 0.4f;
                gameScreen.pulseDirectionUp = true;
            }
        }
    }
    /**
     * Draws the actual speed boost visual effect including glow and text.
     */
    private void drawSpeedBoostGlow() {
        float alpha = 1.0f;
        if (gameScreen.speedBoostEffectTimer > gameScreen.SPEED_BOOST_EFFECT_DURATION - 0.5f) {
            alpha = (gameScreen.SPEED_BOOST_EFFECT_DURATION - gameScreen.speedBoostEffectTimer) / 0.5f;
        }

        Color prevColor = gameScreen.batch.getColor().cpy();

        // Draw the glow effect
        Color effectColor = new Color(gameScreen.SPEED_BOOST_COLOR);
        effectColor.a = gameScreen.pulseAlpha * alpha;
        gameScreen.batch.setColor(effectColor);

        // Draw outer glow
        float glowSize = 90f;
        gameScreen.batch.draw(
            gameScreen.getSpeedBoostTexture(),
            gameScreen.ai.position.x + 32 - glowSize/2,
            gameScreen.ai.position.y + 32 - glowSize/2,
            glowSize, glowSize
        );

        // Draw speed boost text
        gameScreen.font.setColor(1f, 0.8f, 0.2f, alpha);
        GlyphLayout layout = new GlyphLayout(gameScreen.font, "SPEED BOOST!");
        gameScreen.font.draw(gameScreen.batch, "SPEED BOOST!",
            gameScreen.ai.position.x + 32 - layout.width/2,
            gameScreen.ai.position.y + 95);

        // Restore colors
        gameScreen.batch.setColor(prevColor);
        gameScreen.font.setColor(Color.WHITE);
    }

    /**
     * Renders the heads-up display including scores, round information, and timer.
     */
    private void renderHUD() {
        Vector2 cameraPos = new Vector2(gameScreen.camera.position.x, gameScreen.camera.position.y);
        float viewportHeight = gameScreen.camera.viewportHeight;

        // Draw scores
        gameScreen.font.draw(gameScreen.batch, "Player Score: " + gameScreen.player.score,
            cameraPos.x - 380, cameraPos.y + (viewportHeight / 2f) - 20);
        gameScreen.font.draw(gameScreen.batch, "AI Score: " + gameScreen.ai.score,
            cameraPos.x - 380, cameraPos.y + (viewportHeight / 2f) - 40);

        // Draw round info
        gameScreen.font.draw(gameScreen.batch, "Round: " + gameScreen.currentRound + "/" + gameScreen.settings.totalRounds,
            cameraPos.x + 250, cameraPos.y + (viewportHeight / 2f) - 40);

        // Draw timer if in timer mode
        if (gameScreen.settings.gameMode == GameSettings.GameMode.TIMER) {
            int timeLeft = (int) gameScreen.countdownTimer;
            gameScreen.font.draw(gameScreen.batch, "Time Left: " + timeLeft,
                cameraPos.x + 250, cameraPos.y + (viewportHeight / 2f) - 20);
        }
    }

    /**
     * Renders the round transition popup with fade effects.
     *
     * @param delta Time since last frame for animation timing
     */
    private void renderRoundPopup(float delta) {
        if (gameScreen.showingRoundPopup) {
            gameScreen.roundPopupTimer += delta;
            if (gameScreen.roundPopupTimer >= ROUND_POPUP_DURATION) {
                gameScreen.showingRoundPopup = false;
                gameScreen.countdownActive = true;
                gameScreen.countdownNumberTimer = 0f;
                gameScreen.currentCountdownNumber = 3;
            }

            float alpha = calculateFadeAlpha(gameScreen.roundPopupTimer, ROUND_POPUP_DURATION);
            drawPopupBackground(alpha, 400, 100);
            drawRoundText(alpha);
        }
    }

    /**
     * Renders the countdown sequence at the start of each round.
     *
     * @param delta Time since last frame for countdown timing
     */
    private void renderCountdown(float delta) {
        if (gameScreen.countdownActive) {
            gameScreen.countdownNumberTimer += delta;
            if (gameScreen.countdownNumberTimer >= gameScreen.COUNTDOWN_NUMBER_DURATION) {
                gameScreen.countdownNumberTimer = 0f;
                gameScreen.currentCountdownNumber--;

                if (gameScreen.currentCountdownNumber < 0) {
                    gameScreen.countdownActive = false;
                    gameScreen.gameStarted = true;
                }
            }

            float alpha = calculateFadeAlpha(gameScreen.countdownNumberTimer, gameScreen.COUNTDOWN_NUMBER_DURATION);
            drawPopupBackground(alpha, 150, 150);
            drawCountdownText(alpha);
        }
    }

    /**
     * Renders hint availability status and active hints.
     */
    private void renderHints() {
        if (gameScreen.hintEnabled) {
            if (gameScreen.hintAvailable) {
                gameScreen.font.draw(gameScreen.batch, "Press H for Hint",
                    gameScreen.camera.position.x - 380,
                    gameScreen.camera.position.y + (gameScreen.camera.viewportHeight / 2f) - 60);
            } else {
                gameScreen.font.draw(gameScreen.batch, "Hint available in: " + (int)gameScreen.hintCooldown,
                    gameScreen.camera.position.x - 380,
                    gameScreen.camera.position.y + (gameScreen.camera.viewportHeight / 2f) - 60);
            }
        }

        if (gameScreen.hintVisible && !gameScreen.getCurrentHint().isEmpty()) {
            float alpha = 1.0f;
            Color prevColor = gameScreen.font.getColor();
            gameScreen.font.setColor(prevColor.r, prevColor.g, prevColor.b, alpha);
            gameScreen.font.draw(gameScreen.batch, gameScreen.getCurrentHint(),
                gameScreen.camera.position.x - 200,
                gameScreen.camera.position.y + (gameScreen.camera.viewportHeight / 2f) - 20);
            gameScreen.font.setColor(prevColor);
        }
    }

    /**
     * Calculates fade alpha value for popup effects based on timer and duration.
     *
     * @param timer Current timer value
     * @param duration Total duration of the effect
     * @return Alpha value between 0.0 and 1.0
     */
    private float calculateFadeAlpha(float timer, float duration) {
        float alpha = 1.0f;
        if (timer < 0.5f) {
            alpha = timer / 0.5f;
        } else if (timer > duration - 0.5f) {
            alpha = (duration - timer) / 0.5f;
        }
        return alpha;
    }

    /**
     * Draws a semi-transparent background for popup messages.
     *
     * @param alpha Transparency value for the background
     * @param width Width of the popup background
     * @param height Height of the popup background
     */
    private void drawPopupBackground(float alpha, float width, float height) {
        gameScreen.batch.end();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        gameScreen.shapeRenderer.setProjectionMatrix(gameScreen.camera.combined);
        gameScreen.shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        gameScreen.shapeRenderer.setColor(0, 0, 0, 0.7f * alpha);
        gameScreen.shapeRenderer.rect(
            gameScreen.camera.position.x - width/2,
            gameScreen.camera.position.y - height/2,
            width, height
        );
        gameScreen.shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
        gameScreen.batch.begin();
    }

    /**
     * Draws the round number text for the round popup.
     *
     * @param alpha Transparency value for the text
     */
    private void drawRoundText(float alpha) {
        Color prevColor = gameScreen.largeFont.getColor();
        gameScreen.largeFont.setColor(1, 1, 1, alpha);

        String roundText = "ROUND " + gameScreen.currentRound;
        GlyphLayout layout = new GlyphLayout(gameScreen.largeFont, roundText);

        gameScreen.largeFont.draw(gameScreen.batch, roundText,
            gameScreen.camera.position.x - layout.width / 2,
            gameScreen.camera.position.y + 10
        );

        gameScreen.largeFont.setColor(prevColor);
    }

    /**
     * Draws the countdown text (3, 2, 1, GO!) during countdown sequence.
     *
     * @param alpha Transparency value for the text
     */
    private void drawCountdownText(float alpha) {
        Color prevColor = gameScreen.largeFont.getColor();
        gameScreen.largeFont.setColor(1, 1, 0, alpha);

        String countdownText = gameScreen.currentCountdownNumber > 0 ?
            String.valueOf(gameScreen.currentCountdownNumber) : "GO!";

        GlyphLayout layout = new GlyphLayout(gameScreen.largeFont, countdownText);
        gameScreen.largeFont.draw(gameScreen.batch, countdownText,
            gameScreen.camera.position.x - layout.width / 2,
            gameScreen.camera.position.y + layout.height / 2
        );

        gameScreen.largeFont.setColor(prevColor);
    }
}
