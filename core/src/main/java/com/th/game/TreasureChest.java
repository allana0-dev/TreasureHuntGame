package com.th.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Vector2;

public class TreasureChest {
    public enum ChestState {
        CLOSED,
        OPENING,
        OPEN
    }

    public Vector2 position;
    public ChestState state;
    public float width, height;  // dimensions of the chest
    public float animationTime;
    public Animation<TextureRegion> animation;

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

    public void open() {
        if (state == ChestState.CLOSED) {
            state = ChestState.OPENING;
            animationTime = 0f;
        }
    }

    public void update(float delta) {
        if (state == ChestState.OPENING) {
            animationTime += delta;
            if (animation.isAnimationFinished(animationTime)) {
                state = ChestState.OPEN;
            }
        }
    }

    // Render with a custom alpha.
    public void render(SpriteBatch batch, float alpha) {
        // Save current color.
        Color oldColor = batch.getColor();
        batch.setColor(oldColor.r, oldColor.g, oldColor.b, alpha);

        TextureRegion currentFrame;
        if (state == ChestState.CLOSED) {
            currentFrame = animation.getKeyFrame(0);
        } else if (state == ChestState.OPENING) {
            currentFrame = animation.getKeyFrame(animationTime);
        } else { // OPEN
            currentFrame = animation.getKeyFrame(animation.getAnimationDuration());
        }
        batch.draw(currentFrame, position.x, position.y, width, height);

        // Restore the original color.
        batch.setColor(oldColor);
    }

    // Optionally, if you want a default render (always fully visible):
    public void render(SpriteBatch batch) {
        render(batch, 1f);
    }

    public void dispose() {
        for (TextureRegion region : animation.getKeyFrames()) {
            region.getTexture().dispose();
        }
    }
}


