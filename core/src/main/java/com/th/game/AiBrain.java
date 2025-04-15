package com.th.game;

import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AiBrain {

    private boolean[][] walkableGrid;
    private int gridWidth;
    private int gridHeight;
    private int tilePixelWidth;
    private int tilePixelHeight;
    private int[] targetCell;  // Holds target grid cell as {x, y}
    private Random random;
    private float threshold = 5f; // When the AI is within 5 pixels of the target, we consider it "arrived"

    public AiBrain(boolean[][] walkableGrid, int gridWidth, int gridHeight, int tilePixelWidth, int tilePixelHeight) {
        this.walkableGrid = walkableGrid;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.tilePixelWidth = tilePixelWidth;
        this.tilePixelHeight = tilePixelHeight;
        this.random = new Random();
        targetCell = null;
    }

    /**
     * Updates the AI's pixel position based on grid-based random walking.
     *
     * @param currentPos The current position of the AI in pixels.
     * @param delta Time delta.
     * @return The updated position in pixels.
     */
    public Vector2 update(Vector2 currentPos, float delta) {
        // Compute the AI's current grid cell using integer division.
        int currentCellX = (int)(currentPos.x / tilePixelWidth);
        int currentCellY = (int)(currentPos.y / tilePixelHeight);

        // If we have no target or we're near the target, choose a new adjacent cell.
        if (targetCell == null) {
            targetCell = chooseRandomNeighbor(currentCellX, currentCellY);
        } else {
            float targetCenterX = targetCell[0] * tilePixelWidth + tilePixelWidth / 2f;
            float targetCenterY = targetCell[1] * tilePixelHeight + tilePixelHeight / 2f;
            Vector2 targetCenter = new Vector2(targetCenterX, targetCenterY);
            if (currentPos.dst(targetCenter) < threshold) {
                targetCell = chooseRandomNeighbor(currentCellX, currentCellY);
            }
        }

        // If we have a target, move toward its cell's center.
        if (targetCell != null) {
            float targetCenterX = targetCell[0] * tilePixelWidth + tilePixelWidth / 2f;
            float targetCenterY = targetCell[1] * tilePixelHeight + tilePixelHeight / 2f;
            Vector2 targetCenter = new Vector2(targetCenterX, targetCenterY);

            // Compute the direction toward the target.
            Vector2 direction = targetCenter.cpy().sub(currentPos);
            if (direction.len() == 0) {
                return currentPos;
            }
            direction.nor();
            float aiSpeed = 150f; // AI speed in pixels per second.
            Vector2 movement = direction.scl(aiSpeed * delta);
            Vector2 newPos = currentPos.cpy().add(movement);
            // Prevent overshooting the target.
            if (newPos.dst(targetCenter) > currentPos.dst(targetCenter)) {
                newPos = targetCenter.cpy();
            }
            return newPos;
        }
        return currentPos;
    }

    /**
     * Chooses a random adjacent grid cell that is walkable.
     * Only considers 4-neighbors (up, down, left, right).
     *
     * @param cellX The current cell's x coordinate.
     * @param cellY The current cell's y coordinate.
     * @return A two-element array {newCellX, newCellY} for the chosen neighbor.
     */
    private int[] chooseRandomNeighbor(int cellX, int cellY) {
        List<int[]> neighbors = new ArrayList<>();
        int[][] directions = { {0, 1}, {0, -1}, {-1, 0}, {1, 0} };
        for (int[] d : directions) {
            int nx = cellX + d[0];
            int ny = cellY + d[1];
            if (nx >= 0 && nx < gridWidth && ny >= 0 && ny < gridHeight && walkableGrid[nx][ny]) {
                neighbors.add(new int[]{nx, ny});
            }
        }
        if (neighbors.size() > 0) {
            return neighbors.get(random.nextInt(neighbors.size()));
        }
        // If no adjacent cell is walkable, stay in the current cell.
        return new int[]{cellX, cellY};
    }
}
