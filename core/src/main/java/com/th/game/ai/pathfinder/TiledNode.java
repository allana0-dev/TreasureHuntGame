package com.th.game.ai.pathfinder;
/**
 * A node in the navigation graph used for pathfinding.
 * Represents a walkable position on the tile map.
 */
public class TiledNode {
    public final int index;
    public final float x;
    public final float y;
    public final int gridX;
    public final int gridY;

    /**
     * Creates a new node at the specified position
     *
     * @param index Unique index for this node in the graph
     * @param x World X coordinate (in pixels)
     * @param y World Y coordinate (in pixels)
     * @param gridX Grid X coordinate (in tiles)
     * @param gridY Grid Y coordinate (in tiles)
     */
    public TiledNode(int index, float x, float y, int gridX, int gridY) {
        this.index = index;
        this.x = x;
        this.y = y;
        this.gridX = gridX;
        this.gridY = gridY;
    }

    @Override
    public String toString() {
        return "Node[" + gridX + "," + gridY + "]";
    }
}

