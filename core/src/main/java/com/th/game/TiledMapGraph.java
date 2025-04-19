package com.th.game;

import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.DefaultConnection;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A graph implementation for LibGDX A* pathfinding on TiledMaps.
 * Creates a navigable graph from walkable tiles.
 */
public class TiledMapGraph implements IndexedGraph<TiledNode> {
    private GameScreen gameScreen;
    private TiledMap tiledMap;
    private int mapWidth;
    private int mapHeight;
    private int tileWidth;
    private int tileHeight;

    // Node storage
    private Array<TiledNode> nodes;
    private Array<TiledNode> walkableNodes;
    private TiledNode[][] nodeMap; // Provides quick lookup by x,y
    private ObjectMap<TiledNode, Array<Connection<TiledNode>>> connectionMap;

    // Tracks visited nodes for efficient unexplored area finding
    private boolean[][] visitedTiles;
    private Random random = new Random();

    /**
     * Creates a new graph for the specified map
     */
    public TiledMapGraph(GameScreen gameScreen, TiledMap tiledMap) {
        this.gameScreen = gameScreen;
        this.tiledMap = tiledMap;

        // Get map dimensions
        mapWidth = tiledMap.getProperties().get("width", Integer.class);
        mapHeight = tiledMap.getProperties().get("height", Integer.class);
        tileWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        tileHeight = tiledMap.getProperties().get("tileheight", Integer.class);

        nodes = new Array<>();
        walkableNodes = new Array<>();
        nodeMap = new TiledNode[mapWidth][mapHeight];
        connectionMap = new ObjectMap<>();
        visitedTiles = new boolean[mapWidth][mapHeight];
    }

    /**
     * Builds the navigation graph from the tilemap
     */
    public void buildGraph() {
        // Create nodes for each tile position
        int nodeIndex = 0;

        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                // Convert tile position to pixel position (center of tile)
                float pixelX = x * tileWidth + (tileWidth / 2);
                float pixelY = y * tileHeight + (tileHeight / 2);

                // Create node only if position is walkable
                Vector2 pixelPos = new Vector2(pixelX, pixelY);
                if (gameScreen.isWalkable(pixelPos)) {
                    TiledNode node = new TiledNode(nodeIndex++, pixelX, pixelY, x, y);
                    nodeMap[x][y] = node;
                    nodes.add(node);
                    walkableNodes.add(node);
                }
            }
        }

        // Connect nodes to their walkable neighbors
        for (TiledNode node : nodes) {
            // Create array to hold connections from this node
            Array<Connection<TiledNode>> connections = new Array<>();

            // Get node's grid position
            int x = node.gridX;
            int y = node.gridY;

            // Check 4 adjacent neighbors
            checkAndAddConnection(connections, node, x+1, y); // Right
            checkAndAddConnection(connections, node, x-1, y); // Left
            checkAndAddConnection(connections, node, x, y+1); // Up
            checkAndAddConnection(connections, node, x, y-1); // Down

            // Optionally enable diagonal connections for smoother paths
            checkAndAddConnection(connections, node, x+1, y+1); // Upper-right
            checkAndAddConnection(connections, node, x-1, y+1); // Upper-left
            checkAndAddConnection(connections, node, x+1, y-1); // Lower-right
            checkAndAddConnection(connections, node, x-1, y-1); // Lower-left

            // Store connections for this node
            connectionMap.put(node, connections);
        }

        System.out.println("Built navigation graph with " + nodes.size + " nodes");
    }

    /**
     * Helper method to check if a neighboring tile is walkable and add a connection if it is
     */
    private void checkAndAddConnection(Array<Connection<TiledNode>> connections, TiledNode fromNode, int toX, int toY) {
        // Check if the position is valid and has a node
        if (toX >= 0 && toX < mapWidth && toY >= 0 && toY < mapHeight && nodeMap[toX][toY] != null) {
            TiledNode toNode = nodeMap[toX][toY];

            // Check if this is a cardinal direction (not diagonal)
            boolean isCardinal = (fromNode.gridX == toX || fromNode.gridY == toY);

            if (isCardinal) {
                // Add connection with cost 1.0 for cardinal directions
                connections.add(new DefaultConnection<>(fromNode, toNode));
            }
            // Don't add diagonal connections
        }
    }    /**
     * Returns all walkable nodes in the graph
     * Used for debugging visualization
     */
    public Array<TiledNode> getWalkableNodes() {
        return walkableNodes;
    }
    /**
     * Gets the node at the specified world coordinates
     */
    public TiledNode getNodeAtWorldCoordinates(float x, float y) {
        // Convert world coordinates to grid coordinates
        int gridX = (int)(x / tileWidth);
        int gridY = (int)(y / tileHeight);

        // Debug info
        System.out.println("Looking for node at grid: " + gridX + "," + gridY +
            " (world: " + x + "," + y + ")");

        // Ensure we're within bounds
        if (gridX >= 0 && gridX < mapWidth && gridY >= 0 && gridY < mapHeight) {
            TiledNode node = nodeMap[gridX][gridY];
            if (node == null) {
                System.out.println("  - No node found at this grid position");
            } else {
                System.out.println("  - Found node: " + node);
            }
            return node;
        }

        System.out.println("  - Position out of map bounds");
        return null;
    }
    /**
     * Gets a random walkable node from the graph
     */
    public TiledNode getRandomWalkableNode() {
        // Build a list of “safe” nodes, excluding the outermost tiles
        Array<TiledNode> safeNodes = new Array<>();
        for (TiledNode node : walkableNodes) {
            if (node.gridX > 0 && node.gridX < mapWidth - 1
                && node.gridY > 0 && node.gridY < mapHeight - 1) {
                safeNodes.add(node);
            }
        }
        // If for some reason we filtered everything out, fall back to any node
        if (safeNodes.size > 0) {
            return safeNodes.get(random.nextInt(safeNodes.size));
        } else {
            return walkableNodes.get(random.nextInt(walkableNodes.size));
        }
    }


    /**
     * Marks a node as having been visited
     */
    public void markNodeVisited(TiledNode node) {
        if (node != null) {
            visitedTiles[node.gridX][node.gridY] = true;
        }
    }
    // Inside TiledMapGraph.java

    /** Number of tiles horizontally in the map. */
    public int getWidth() {
        return mapWidth;
    }

    /** Number of tiles vertically in the map. */
    public int getHeight() {
        return mapHeight;
    }

    /** Width of one tile in pixels. */
    public int getTileWidth() {
        return tileWidth;
    }

    /** Height of one tile in pixels. */
    public int getTileHeight() {
        return tileHeight;
    }


    /**
     * Finds a node in an unexplored area of the map
     */
    public TiledNode getUnexploredNode(Vector2 currentPosition) {
        // Mark current node as visited
        TiledNode currentNode = getNodeAtWorldCoordinates(currentPosition.x, currentPosition.y);
        if (currentNode != null) {
            markNodeVisited(currentNode);
        }

        // Get all unexplored walkable nodes
        Array<TiledNode> unexploredNodes = new Array<>();
        for (TiledNode node : walkableNodes) {
            if (!visitedTiles[node.gridX][node.gridY]
                && node.gridX > 0 && node.gridX < mapWidth - 1
                && node.gridY > 0 && node.gridY < mapHeight - 1) {
                unexploredNodes.add(node);
            }
        }

        // If we've explored most of the map, reset the visited status
        if (unexploredNodes.size < walkableNodes.size * 0.2f) { // Less than 20% unexplored
            resetVisitedStatus();
            return getRandomWalkableNode(); // Return a random node after reset
        }

        // Return a random unexplored node with preference for nodes further away
        if (unexploredNodes.size > 0) {
            // Sort unexplored nodes by distance from current position (descending)
            unexploredNodes.sort((n1, n2) -> {
                float dist1 = Vector2.dst(currentPosition.x, currentPosition.y, n1.x, n1.y);
                float dist2 = Vector2.dst(currentPosition.x, currentPosition.y, n2.x, n2.y);
                return Float.compare(dist2, dist1); // Sort in descending order (furthest first)
            });

            // Pick from the first third of the list (furthest nodes)
            int maxIndex = Math.max(1, unexploredNodes.size / 3);
            return unexploredNodes.get(random.nextInt(maxIndex));
        }

        // Fallback to random node
        return getRandomWalkableNode();
    }

    /**
     * Resets the visited status of all nodes
     */
    public void resetVisitedStatus() {
        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                visitedTiles[x][y] = false;
            }
        }

        System.out.println("Reset exploration state");
    }

    /**
     * Returns the total number of nodes in the graph
     */
    public int getNodeCount() {
        return nodes.size;
    }

    // IndexedGraph interface implementation
    @Override
    public int getIndex(TiledNode node) {
        return node.index;
    }


    @Override
    public Array<Connection<TiledNode>> getConnections(TiledNode fromNode) {
        return connectionMap.get(fromNode);
    }
}
