package com.th.game.ai.pathfinder;

import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.DefaultConnection;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.th.game.screens.GameScreen;
import java.util.Random;

/**
 * An {@link IndexedGraph} implementation for A* pathfinding on a TiledMap.
 * Nodes are created for walkable tiles and connected to adjacent walkable neighbors.
 */
public class TiledMapGraph implements IndexedGraph<TiledNode> {

    private final GameScreen gameScreen;
    private final int mapWidth;
    private final int mapHeight;
    private final int tileWidth;
    private final int tileHeight;

    private final Array<TiledNode> nodes;
    private final Array<TiledNode> walkableNodes;
    private final TiledNode[][] nodeMap;
    private final ObjectMap<TiledNode, Array<Connection<TiledNode>>> connectionMap;
    private final boolean[][] visitedTiles;
    private final Random random = new Random();

    /**
     * Constructs a graph for the given TiledMap and game screen.
     *
     * @param gameScreen the GameScreen used for walkability checks
     * @param tiledMap   the TiledMap to build the graph from
     */
    public TiledMapGraph(GameScreen gameScreen, TiledMap tiledMap) {
        this.gameScreen = gameScreen;
        this.mapWidth = tiledMap.getProperties().get("width", Integer.class);
        this.mapHeight = tiledMap.getProperties().get("height", Integer.class);
        this.tileWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        this.tileHeight = tiledMap.getProperties().get("tileheight", Integer.class);

        this.nodes = new Array<>();
        this.walkableNodes = new Array<>();
        this.nodeMap = new TiledNode[mapWidth][mapHeight];
        this.connectionMap = new ObjectMap<>();
        this.visitedTiles = new boolean[mapWidth][mapHeight];
    }

    /**
     * Builds nodes for each walkable tile and connects them to adjacent walkable neighbors.
     */
    public void buildGraph() {
        int index = 0;
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                float pixelX = x * tileWidth + tileWidth / 2f;
                float pixelY = y * tileHeight + tileHeight / 2f;
                Vector2 position = new Vector2(pixelX, pixelY);
                if (gameScreen.isWalkable(position)) {
                    TiledNode node = new TiledNode(index++, pixelX, pixelY, x, y);
                    nodeMap[x][y] = node;
                    nodes.add(node);
                    walkableNodes.add(node);
                }
            }
        }

        for (TiledNode node : nodes) {
            Array<Connection<TiledNode>> connections = new Array<>();
            int x = node.gridX;
            int y = node.gridY;
            addConnection(connections, node, x + 1, y);
            addConnection(connections, node, x - 1, y);
            addConnection(connections, node, x, y + 1);
            addConnection(connections, node, x, y - 1);
            connectionMap.put(node, connections);
        }
    }

    /**
     * Adds a connection between two nodes if the target tile is walkable.
     * Diagonal connections are not added.
     *
     * @param connections the list to add to
     * @param from        the source node
     * @param toX         grid x-coordinate of the target
     * @param toY         grid y-coordinate of the target
     */
    private void addConnection(Array<Connection<TiledNode>> connections,
                               TiledNode from, int toX, int toY) {
        if (toX < 0 || toX >= mapWidth || toY < 0 || toY >= mapHeight) {
            return;
        }
        TiledNode to = nodeMap[toX][toY];
        if (to == null) {
            return;
        }
        boolean cardinal = (from.gridX == toX || from.gridY == toY);
        if (cardinal) {
            connections.add(new DefaultConnection<>(from, to));
        }
    }

    /**
     * Returns all walkable nodes in the graph.
     *
     * @return array of walkable nodes
     */
    public Array<TiledNode> getWalkableNodes() {
        return walkableNodes;
    }

    /**
     * Retrieves the node at specified world coordinates.
     *
     * @param x world x-coordinate
     * @param y world y-coordinate
     * @return the corresponding TiledNode or null if none
     */
    public TiledNode getNodeAtWorldCoordinates(float x, float y) {
        int gridX = (int) (x / tileWidth);
        int gridY = (int) (y / tileHeight);
        if (gridX < 0 || gridX >= mapWidth || gridY < 0 || gridY >= mapHeight) {
            return null;
        }
        return nodeMap[gridX][gridY];
    }

    /**
     * Returns a random walkable node, excluding border tiles.
     *
     * @return a random TiledNode
     */
    public TiledNode getRandomWalkableNode() {
        Array<TiledNode> safe = new Array<>();
        for (TiledNode node : walkableNodes) {
            if (node.gridX > 0 && node.gridX < mapWidth - 1
                && node.gridY > 0 && node.gridY < mapHeight - 1) {
                safe.add(node);
            }
        }
        Array<TiledNode> pool = safe.size > 0 ? safe : walkableNodes;
        return pool.get(random.nextInt(pool.size));
    }

    /**
     * Marks a node as visited for unexplored-area tracking.
     *
     * @param node the node to mark
     */
    public void markNodeVisited(TiledNode node) {
        if (node != null) {
            visitedTiles[node.gridX][node.gridY] = true;
        }
    }

    /**
     * Finds an unexplored node, preferring those farthest from current.
     * Resets visited status if most nodes are explored.
     *
     * @param currentPosition current world position
     * @return an unexplored TiledNode
     */
    public TiledNode getUnexploredNode(Vector2 currentPosition) {
        TiledNode current = getNodeAtWorldCoordinates(currentPosition.x, currentPosition.y);
        markNodeVisited(current);

        Array<TiledNode> unexplored = new Array<>();
        for (TiledNode node : walkableNodes) {
            if (!visitedTiles[node.gridX][node.gridY]
                && node.gridX > 0 && node.gridX < mapWidth - 1
                && node.gridY > 0 && node.gridY < mapHeight - 1) {
                unexplored.add(node);
            }
        }
        if (unexplored.size < walkableNodes.size * 0.2f) {
            resetVisitedStatus();
            return getRandomWalkableNode();
        }
        unexplored.sort((a, b) -> Float.compare(
            Vector2.dst(currentPosition.x, currentPosition.y, b.x, b.y),
            Vector2.dst(currentPosition.x, currentPosition.y, a.x, a.y)
        ));
        int pick = Math.max(1, unexplored.size / 3);
        return unexplored.get(random.nextInt(pick));
    }

    /**
     * Resets visitation status for all nodes.
     */
    public void resetVisitedStatus() {
        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                visitedTiles[x][y] = false;
            }
        }
    }

    /**
     * Returns the total number of nodes in the graph.
     *
     * @return node count
     */
    public int getNodeCount() {
        return nodes.size;
    }

    @Override
    public int getIndex(TiledNode node) {
        return node.index;
    }

    @Override
    public Array<Connection<TiledNode>> getConnections(TiledNode fromNode) {
        return connectionMap.get(fromNode);
    }

    /**
     * @return number of tiles horizontally in the map
     */
    public int getWidth() {
        return mapWidth;
    }

    /**
     * @return number of tiles vertically in the map
     */
    public int getHeight() {
        return mapHeight;
    }

    /**
     * @return width of a single tile in pixels
     */
    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * @return height of a single tile in pixels
     */
    public int getTileHeight() {
        return tileHeight;
    }
}
