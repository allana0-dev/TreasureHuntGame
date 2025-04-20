package com.th.game.ai.pathfinder;

import com.badlogic.gdx.ai.pfa.Heuristic;
/**
 * A heuristic for A* pathfinding that uses Manhattan distance
 * with diagonal movement allowed.
 */
public class MapHeuristic implements Heuristic<TiledNode> {
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
