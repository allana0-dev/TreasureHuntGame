package com.th.game.extenders.gamescreen;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Vector2;
import com.th.game.entities.TreasureChest;
import com.th.game.screens.GameScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TreasureExtender {
    private final GameScreen gameScreen;
    private final Random random;

    public TreasureExtender(GameScreen gameScreen) {
        this.gameScreen = gameScreen;
        this.random = new Random();
    }

    /**
     * Places treasure chests scattered across the map using a grid-based approach
     * to ensure even distribution while maintaining randomization
     */
    public void placeTreasuresScattered() {
        gameScreen.treasureChests.clear();

        // Get map dimensions
        TiledMap tiledMap = gameScreen.getTiledMap();
        int mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        int mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        int tileWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        int tileHeight = tiledMap.getProperties().get("tileheight", Integer.class);

        int mapPixelWidth = mapTileWidth * tileWidth;
        int mapPixelHeight = mapTileHeight * tileHeight;

        // Define an edge buffer so treasures aren't spawned too close to edges
        int treasureEdgeBuffer = 50;

        // Divide the map into sections based on treasure count
        int gridSize = (int) Math.ceil(Math.sqrt(gameScreen.settings.treasureCount));
        int cellWidth = (mapPixelWidth - 2 * treasureEdgeBuffer) / gridSize;
        int cellHeight = (mapPixelHeight - 2 * treasureEdgeBuffer) / gridSize;

        // Keep track of placed treasures
        int placedTreasures = 0;

        // First try to place one treasure per grid cell
        List<Integer> cellIndices = new ArrayList<>();
        for (int i = 0; i < gridSize * gridSize; i++) {
            cellIndices.add(Integer.valueOf(i));
        }
        java.util.Collections.shuffle(cellIndices, random);

        for (int index : cellIndices) {
            if (placedTreasures >= gameScreen.settings.treasureCount) break;

            int gridX = index % gridSize;
            int gridY = index / gridSize;

            // Try multiple positions within each cell
            for (int attempt = 0; attempt < 10; attempt++) {
                int x = treasureEdgeBuffer + gridX * cellWidth + random.nextInt(cellWidth);
                int y = treasureEdgeBuffer + gridY * cellHeight + random.nextInt(cellHeight);
                Vector2 pos = new Vector2(x, y);

                // Make sure it's walkable and not too close to player or other treasures
                if (gameScreen.isWalkable(pos) &&
                    pos.dst(gameScreen.player.position) > 100 &&
                    !tooCloseToOtherTreasures(pos, 100)) {
                    gameScreen.treasureChests.add(new TreasureChest(pos, 8, 0.1f));
                    placedTreasures++;
                    break;
                }
            }
        }

        // If we still need more treasures, place them randomly across the map
        while (placedTreasures < gameScreen.settings.treasureCount) {
            Vector2 pos = new Vector2(
                random.nextInt(mapPixelWidth - 2 * treasureEdgeBuffer) + treasureEdgeBuffer,
                random.nextInt(mapPixelHeight - 2 * treasureEdgeBuffer) + treasureEdgeBuffer
            );

            if (gameScreen.isWalkable(pos) &&
                pos.dst(gameScreen.player.position) > 75 &&
                !tooCloseToOtherTreasures(pos, 75)) {
                gameScreen.treasureChests.add(new TreasureChest(pos, 8, 0.1f));
                placedTreasures++;
            }
        }
    }

    /**
     * Helper method to check if a position is too close to existing treasures
     * @param pos The position to check
     * @param minDistance The minimum allowed distance from other treasures
     * @return true if the position is too close to any existing treasure, false otherwise
     */
    private boolean tooCloseToOtherTreasures(Vector2 pos, float minDistance) {
        for (TreasureChest chest : gameScreen.treasureChests) {
            if (pos.dst(chest.position) < minDistance) {
                return true;
            }
        }
        return false;
    }
}
