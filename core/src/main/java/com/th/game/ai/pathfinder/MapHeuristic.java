package com.th.game.ai.pathfinder;

import com.badlogic.gdx.ai.pfa.Heuristic;

/**
 * A helper for the A* pathfinding algorithm that gives a simple, easy-to-understand
 * estimate of how far two grid tiles are from each other.
 * <p>
 * Think of it like walking on a city block grid: you count how many blocks east/west
 * and north/south you need to travel. We also add a tiny extra amount to help
 * the algorithm pick one route when two look equally good.
 */
public class MapHeuristic implements Heuristic<TiledNode> {

    /**
     * Estimates the “distance” between the current tile and the target tile.
     * <p>
     * We look at how many steps (tiles) you have to go horizontally
     * (left or right) and vertically (up or down), then add them together. This is
     * just like counting blocks on a grid. We then multiply by a tiny factor to
     * break ties when two paths are equally long.
     *
     * @param node    the tile where our search is currently at
     * @param endNode the tile we want to reach
     * @return a simple number representing grid steps plus a small tie-breaking amount
     */
    @Override
    public float estimate(TiledNode node, TiledNode endNode) {
        // Calculate horizontal and vertical differences
        int dx = Math.abs(node.gridX - endNode.gridX);
        int dy = Math.abs(node.gridY - endNode.gridY);

        // Base estimate is just the sum of those differences
        float h = dx + dy;

        // Add a tiny extra so that if two paths have exactly the same distance,
        // the algorithm has a consistent way to choose one over the other.
        h *= 1.0f + 0.001f;

        return h;
    }
}
