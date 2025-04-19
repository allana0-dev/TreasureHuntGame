package com.th.game;

import com.badlogic.gdx.ai.pfa.Heuristic;

/**
 * A node in the navigation graph used for pathfinding.
 * Represents a walkable position on the tile map.
 */
public class TiledNode {
    // Unique index for the node in the graph
    public final int index;

    // World coordinates (pixel position)
    public final float x;
    public final float y;

    // Grid coordinates (tile position)
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

/**
 * A heuristic for A* pathfinding that uses Manhattan distance
 * with diagonal movement allowed.
 */
class MapHeuristic implements Heuristic<TiledNode> {
    @Override
    public float estimate(TiledNode node, TiledNode endNode) {
        // Use Manhattan distance (cardinal directions only)
        int dx = Math.abs(node.gridX - endNode.gridX);
        int dy = Math.abs(node.gridY - endNode.gridY);

        // Manhattan distance
        float h = dx + dy;

        // Small tie-breaking factor
        h *= 1.0f + 0.001f;

        return h;
    }

}
