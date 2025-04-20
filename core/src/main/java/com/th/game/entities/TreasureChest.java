package com.th.game.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Vector2;

/**
 * Class for anything related to the treasure chest, with opening animation and state management.
 */
public class TreasureChest {

    /** Possible states of the chest's animation and interaction. */
    public enum ChestState {
        CLOSED,
        OPENING,
        OPEN
    }

    /** The world position (bottom-left) of this chest. */
    public Vector2 position;
    /** The current state of the chest (closed, opening, or open). */
    public ChestState state;
    /** Width of the chest sprite in world units. */
    public float width;
    /** Height of the chest sprite in world units. */
    public float height;
    /** Accumulated time since the opening animation started. */
    public float animationTime;
    /** The animation sequence used when the chest is opening. */
    public Animation<TextureRegion> animation;

    /**
     * Constructs a new TreasureChest at a given position.
     *
     * @param position      the bottom-left world coordinate for this chest
     * @param frameCount    the number of animation frames in the opening sequence
     * @param frameDuration the duration (in seconds) of each animation frame
     */
    public TreasureChest(Vector2 position, int frameCount, float frameDuration) {
        this.position = position;
        this.state = ChestState.CLOSED;
        this.animationTime = 0f;
        TextureRegion[] frames = new TextureRegion[frameCount];
        for (int i = 0; i < frameCount; i++) {
            String filename = String.format("treasurechest/treasurechest%04d.png", i);
            Texture texture = new Texture(Gdx.files.internal(filename));
            frames[i] = new TextureRegion(texture);
            if (i == 0) {
                this.width = texture.getWidth();
                this.height = texture.getHeight();
            }
        }
        animation = new Animation<>(frameDuration, frames);
        animation.setPlayMode(Animation.PlayMode.NORMAL);
    }

    /**
     * Initiates the opening animation if the chest is currently closed.
     */
    public void open() {
        if (state == ChestState.CLOSED) {
            state = ChestState.OPENING;
            animationTime = 0f;
        }
    }

    /**
     * Updates the chest's animation state based on elapsed time.
     *
     * @param delta time elapsed since last frame (in seconds)
     */
    public void update(float delta) {
        if (state == ChestState.OPENING) {
            animationTime += delta;
            if (animation.isAnimationFinished(animationTime)) {
                state = ChestState.OPEN;
            }
        }
    }

    /**
     * Renders the chest with a given transparency.
     *
     * @param batch the SpriteBatch used for drawing
     * @param alpha the opacity (0 = fully transparent, 1 = fully opaque)
     */
    public void render(SpriteBatch batch, float alpha) {
        Color oldColor = batch.getColor();
        batch.setColor(oldColor.r, oldColor.g, oldColor.b, alpha);

        TextureRegion currentFrame;
        if (state == ChestState.CLOSED) {
            currentFrame = animation.getKeyFrame(0);
        } else if (state == ChestState.OPENING) {
            currentFrame = animation.getKeyFrame(animationTime);
        } else {
            currentFrame = animation.getKeyFrame(animation.getAnimationDuration());
        }
        batch.draw(currentFrame, position.x, position.y, width, height);

        batch.setColor(oldColor);
    }

    /**
     * Renders the chest fully opaque.
     *
     * @param batch the SpriteBatch used for drawing
     */
    public void render(SpriteBatch batch) {
        render(batch, 1f);
    }

    /**
     * Disposes of all textures used by this chest's animation frames.
     */
    public void dispose() {
        for (TextureRegion region : animation.getKeyFrames()) {
            region.getTexture().dispose();
        }
    }
}
