package com.th.game;

import com.badlogic.gdx.math.Vector2;
import java.util.Random;

public class AIPlayer extends GameCharacter {
    private Vector2 target;
    private Random random;

    public AIPlayer(Vector2 position) {
        super(position, 100);
        random = new Random();
        // Initially, no target is set so the AI can pick one in update()
        target = null;
    }

    public void setTarget(Vector2 target) {
        this.target = target;
    }

    public void update(float delta, Maze maze) {
        // If no target or reached current target, pick a new random destination.
        if (target == null || position.dst(target) < 5) {
            target = generateRandomTarget(maze);
        }

        // Calculate the direction towards the target.
        Vector2 direction = new Vector2(target).sub(position);
        if (direction.len() > 0) {
            direction.nor();
            Vector2 oldPos = new Vector2(position);
            position.add(direction.scl(speed * delta));
            // If the new position is not walkable, revert and choose a new target.
            if (!maze.isWalkable(position)) {
                position.set(oldPos);
                target = generateRandomTarget(maze);
            }
        }
    }

    // Generates a random walkable target within the maze bounds.
    private Vector2 generateRandomTarget(Maze maze) {
        Vector2 pos;
        do {
            pos = new Vector2(random.nextInt(maze.getWidth()), random.nextInt(maze.getHeight()));
        } while (!maze.isWalkable(pos));
        return pos;
    }
}

