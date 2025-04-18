package com.th.game;

import com.badlogic.gdx.math.Vector2;
import java.util.LinkedList;
import java.util.Queue;
import java.util.HashSet;
import java.util.Set;

/**
 * Helper class to validate spawn locations and ensure entities don't get trapped
 * in small enclosed areas.
 */
public class SpawnLocationValidator {

    // Constants for validation
    private static final int MIN_WALKABLE_AREA = 25; // Minimum number of connected walkable tiles
    private static final int MAX_VALIDATION_DISTANCE = 20; // Maximum distance to check in tiles

    /**
     * Validates a potential spawn position by ensuring it has enough connected walkable area
     *
     * @param gameScreen The GameScreen to check walkability with
     * @param position The position to validate
     * @param tileWidth Width of a map tile
     * @param tileHeight Height of a map tile
     * @return true if the position is in a sufficiently large walkable area
     */
    public static boolean isValidSpawnLocation(GameScreen gameScreen, Vector2 position, int tileWidth, int tileHeight) {
        // First, check basic walkability
        if (!gameScreen.isWalkable(position)) {
            return false;
        }

        // Convert position to tile coordinates
        int startTileX = (int)(position.x / tileWidth);
        int startTileY = (int)(position.y / tileHeight);

        // Breadth-first search to find connected walkable area
        Queue<TileCoord> tilesToCheck = new LinkedList<>();
        Set<TileCoord> checkedTiles = new HashSet<>();

        // Start from spawn position
        TileCoord startTile = new TileCoord(startTileX, startTileY);
        tilesToCheck.add(startTile);
        checkedTiles.add(startTile);

        // Direction vectors for adjacent tiles
        int[] dx = {0, 1, 0, -1}; // right, down, left, up
        int[] dy = {1, 0, -1, 0};

        int walkableAreaSize = 0;

        while (!tilesToCheck.isEmpty() && walkableAreaSize < MIN_WALKABLE_AREA) {
            TileCoord current = tilesToCheck.poll();
            walkableAreaSize++;

            // Check all adjacent tiles
            for (int i = 0; i < 4; i++) {
                int newX = current.x + dx[i];
                int newY = current.y + dy[i];
                TileCoord nextTile = new TileCoord(newX, newY);

                // Skip if we've already checked this tile
                if (checkedTiles.contains(nextTile)) {
                    continue;
                }

                // Skip if we're too far from the start
                int distFromStart = Math.abs(newX - startTileX) + Math.abs(newY - startTileY);
                if (distFromStart > MAX_VALIDATION_DISTANCE) {
                    continue;
                }

                // Convert tile coordinates to world position (center of tile)
                Vector2 tilePos = new Vector2(
                    newX * tileWidth + tileWidth / 2,
                    newY * tileHeight + tileHeight / 2
                );

                // Check if this tile is walkable
                if (gameScreen.isWalkable(tilePos)) {
                    tilesToCheck.add(nextTile);
                    checkedTiles.add(nextTile);
                }
            }
        }

        // Is the walkable area large enough?
        return walkableAreaSize >= MIN_WALKABLE_AREA;
    }

    /**
     * Helper class to track tile coordinates and enable hashing for the visited set
     */
    private static class TileCoord {
        final int x;
        final int y;

        TileCoord(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TileCoord)) return false;
            TileCoord that = (TileCoord) o;
            return x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }

    /**
     * Finds a valid spawn location by testing random positions until one passes validation
     *
     * @param gameScreen The GameScreen for walkability checks
     * @param mapWidth The map width in pixels
     * @param mapHeight The map height in pixels
     * @param tileWidth The tile width in pixels
     * @param tileHeight The tile height in pixels
     * @param maxAttempts Maximum number of attempts before falling back to simple walkability check
     * @return A Vector2 containing a valid spawn location
     */
    public static Vector2 findValidSpawnLocation(
        GameScreen gameScreen, int mapWidth, int mapHeight,
        int tileWidth, int tileHeight, int maxAttempts) {

        java.util.Random random = new java.util.Random();

        // Try to find a location in an open area
        for (int i = 0; i < maxAttempts; i++) {
            Vector2 testPos = new Vector2(
                random.nextInt(mapWidth - 32) + 16,  // Avoid edge by 16px
                random.nextInt(mapHeight - 32) + 16  // Avoid edge by 16px
            );

            if (isValidSpawnLocation(gameScreen, testPos, tileWidth, tileHeight)) {
                System.out.println("Found valid spawn location after " + (i + 1) + " attempts");
                return testPos;
            }
        }

        // If we couldn't find an ideal location, fall back to just finding a walkable spot
        System.out.println("Warning: Couldn't find ideal spawn location, falling back to simple walkability check");
        Vector2 fallbackPos;
        do {
            fallbackPos = new Vector2(
                random.nextInt(mapWidth - 32) + 16,
                random.nextInt(mapHeight - 32) + 16
            );
        } while (!gameScreen.isWalkable(fallbackPos));

        return fallbackPos;
    }
}
