package com.th.game;

import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.ai.pfa.GraphPath;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.ai.steer.Steerable;
import com.badlogic.gdx.ai.steer.SteeringAcceleration;
import com.badlogic.gdx.ai.steer.SteeringBehavior;
import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import java.util.List;
import java.util.Random;

/**
 * A smarter AI implementation using LibGDX's built-in AI and pathfinding capabilities.
 */
public class SmartAI implements Steerable<Vector2> {
    // AI position and properties
    public Vector2 position;
    public int score = 0;
    private String currentMapName;
    private Direction currentDirection = Direction.DOWN;
    private Random random = new Random();

    // Movement and pathfinding
    private float moveSpeed = 100f;
    private float normalMoveSpeed = 100f;
    private float fastMoveSpeed = 200f;
    private boolean isSpeedBoostActive = false;
    private float speedBoostTimer = 0f;
    private float speedBoostDuration = 15f;

    // Path finding
    private TiledMapGraph mapGraph;
    private IndexedAStarPathFinder<TiledNode> pathFinder;
    private GraphPath<TiledNode> currentPath;
    private int currentPathIndex;
    private Vector2 targetPosition = new Vector2();
    private boolean hasTarget = false;
    private boolean pathNeedsRefresh = false;
    private float pathRefreshTimer = 0f;
    private final float PATH_REFRESH_INTERVAL = 1.5f;

    // Steering behavior
    private SteeringBehavior<Vector2> steeringBehavior;
    private SteeringAcceleration<Vector2> steeringOutput = new SteeringAcceleration<>(new Vector2());
    private Vector2 linearVelocity = new Vector2();
    private float maxLinearSpeed = 100f;
    private float maxLinearAcceleration = 200f;
    private float maxAngularSpeed = 0f;
    private float maxAngularAcceleration = 0f;
    private float zeroLinearSpeedThreshold = 0.1f;
    private boolean tagged = false;
    private float lastDistanceToTarget = Float.MAX_VALUE;

    // AI State
    private enum AIState {
        ROAMING,       // Random wandering behavior
        EXPLORING,     // Systematically exploring the map
        SEEKING        // Moving toward a target (landmark or treasure)
    }

    private AIState currentState = AIState.EXPLORING;
    private HistoricalAIData databaseManager;

    // Path visualization (for debugging)
    private Array<Vector2> pathVisualizer = new Array<>();

    /**
     * Creates a new AI with advanced pathfinding capabilities
     *
     * @param position Initial position
     * @param currentMapName Map identifier for database lookups
     */
    public SmartAI(Vector2 position, String currentMapName) {
        this.position = position;
        this.currentMapName = currentMapName;
        this.databaseManager = new HistoricalAIData(this, currentMapName);
        this.currentPath = new DefaultGraphPath<>();
    }

    /**
     * Scans the walkable areas of the map and initializes the pathfinding graph
     */
    public void scanWalkableAreas(GameScreen gameScreen, TiledMap tiledMap) {
        // Create the map graph for pathfinding
        mapGraph = new TiledMapGraph(gameScreen, tiledMap);
        mapGraph.buildGraph();

        // Create the pathfinder
        pathFinder = new IndexedAStarPathFinder<>(mapGraph, true);

        System.out.println("Map scanning complete. Pathfinding graph built with " +
            ((IndexedGraph<TiledNode>)mapGraph).getNodeCount() + " nodes.");
    }

    /**
     * Sets a new target position for the AI to move towards
     */
    public void setTarget(Vector2 targetPos, boolean seekMode) {
        this.targetPosition.set(targetPos);
        this.hasTarget = true;
        this.pathNeedsRefresh = true;

        if (seekMode) {
            this.currentState = AIState.SEEKING;
        }
    }

    /**
     * Sets the AI to exploration mode
     */
    public void setExplorationMode(boolean exploring) {
        if (exploring) {
            this.currentState = AIState.EXPLORING;
        }
    }

    /**
     * Updates the AI's position and behavior
     */
    public void update(float delta, GameScreen gameScreen) {
        // Update speed boost if active
        if (isSpeedBoostActive) {
            speedBoostTimer += delta;
            if (speedBoostTimer >= speedBoostDuration) {
                isSpeedBoostActive = false;
                moveSpeed = normalMoveSpeed;
                maxLinearSpeed = moveSpeed;
                System.out.println("AI speed boost expired. Speed returned to normal: " + moveSpeed);
            }
        }

        // Refresh path if needed
        pathRefreshTimer += delta;
        if ((pathNeedsRefresh || pathRefreshTimer > PATH_REFRESH_INTERVAL) && hasTarget) {
            snapToValidNode(); // Add this line
            findPathToTarget(gameScreen);
            pathRefreshTimer = 0;
            pathNeedsRefresh = false;
        }

        // Consult database manager for target updates
        boolean targetUpdated = databaseManager.updateAITarget(
            delta,
            gameScreen.getCurrentHint(),
            gameScreen.getLandmarks()
        );

        // If no active target, find one based on current state
        if (!hasTarget && !targetUpdated) {
            switch (currentState) {
                case ROAMING:
                    findRandomTarget(gameScreen);
                    break;
                case EXPLORING:
                    findExplorationTarget(gameScreen);
                    break;
                case SEEKING:
                    // If seeking with no target, fall back to exploring
                    if (!hasTarget) {
                        currentState = AIState.EXPLORING;
                        findExplorationTarget(gameScreen);
                    }
                    break;
            }
        }

        // Move along the current path
        moveAlongPath(delta, gameScreen);

        // Update animation direction based on movement
        updateDirection();
    }

    /**
     * Finds a path to the current target
     */
    private void findPathToTarget(GameScreen gameScreen) {
        // 1) Bail out if there’s no target or the graph isn’t built yet
        if (!hasTarget || mapGraph == null) return;

        // 2) If we’re already very close to the target, clear it and stop
        if (position.dst(targetPosition) < 32f) {
            hasTarget = false;
            return;
        }

        // 3) Early‐exit if we’re already on a valid, improving path
        if (currentPath.getCount() > 0 && currentPathIndex < currentPath.getCount()) {
            if (position.dst(targetPosition) < lastDistanceToTarget) {
                System.out.println("Already on valid path, continuing");
                return;
            }
        }

        // 4) Remember how far we were last time
        lastDistanceToTarget = position.dst(targetPosition);

        // 5) Clear out the old path & any debug visualization
        currentPath.clear();
        pathVisualizer.clear();

        // 6) Find the start/end nodes on the grid
        TiledNode startNode = mapGraph.getNodeAtWorldCoordinates(position.x, position.y);
        TiledNode endNode   = mapGraph.getNodeAtWorldCoordinates(targetPosition.x, targetPosition.y);

        if (startNode != null && endNode != null) {
            // 7) Run A* search into currentPath
            MapHeuristic heuristic = new MapHeuristic();
            pathFinder.searchNodePath(startNode, endNode, heuristic, currentPath);

            // 8) Reset index so we start at the first node
            currentPathIndex = 0;

            // 9) Build a simple debug list of world‐space points
            for (TiledNode node : currentPath) {
                pathVisualizer.add(new Vector2(node.x, node.y));
            }

            System.out.println("Path found with " + currentPath.getCount() + " nodes");
        } else {
            // 10) If either end is off‐grid, bail out
            System.out.println("Path finding failed – invalid start or end node");
            hasTarget = false;
        }
    }
    /**
     * Moves the AI along the current path
     */
    private void moveAlongPath(float delta, GameScreen gameScreen) {
        if (currentPath.getCount() == 0) return;
        if (currentPathIndex >= currentPath.getCount()) {
            if (position.dst(targetPosition) < 32f) hasTarget = false;
            return;
        }

        // 1) Compute direction
        TiledNode nextNode = currentPath.get(currentPathIndex);
        Vector2 nextPos = new Vector2(nextNode.x, nextNode.y);
        float dx = nextPos.x - position.x;
        float dy = nextPos.y - position.y;
        Vector2 direction = new Vector2();
        if (Math.abs(dx) > Math.abs(dy)) {
            direction.set(Math.signum(dx), 0);
        } else {
            direction.set(0, Math.signum(dy));
        }

        // 2) Build linearVelocity and proposedPos
        linearVelocity.set(direction).scl(moveSpeed);
        Vector2 oldPos = new Vector2(position);
        Vector2 proposedPos = oldPos.cpy()
            .add(linearVelocity.x * delta, linearVelocity.y * delta);

        // 3) Walkability + fallback
        if (gameScreen.isWalkable(proposedPos)) {
            position.set(proposedPos);
        } else {
            for (float scale = 0.9f; scale >= 0.1f; scale -= 0.1f) {
                Vector2 tryPos = oldPos.cpy()
                    .add(linearVelocity.x * delta * scale,
                        linearVelocity.y * delta * scale);
                if (gameScreen.isWalkable(tryPos)) {
                    position.set(tryPos);
                    break;
                }
            }
        }

        // 4) Clamp to map bounds
        int mapW = gameScreen.getTiledMap().getProperties().get("width", Integer.class)
            * gameScreen.getTiledMap().getProperties().get("tilewidth", Integer.class);
        int mapH = gameScreen.getTiledMap().getProperties().get("height", Integer.class)
            * gameScreen.getTiledMap().getProperties().get("tileheight", Integer.class);
        position.x = MathUtils.clamp(position.x, 0, mapW - 64);
        position.y = MathUtils.clamp(position.y, 0, mapH - 64);

        // 5) Advance node if “close enough”
        if (position.dst(nextPos) < 5f) {
            currentPathIndex++;
            linearVelocity.setZero();
            // optional: pathNeedsRefresh = true;
        }

        // 6) Update facing
        updateDirection();

        // ───────────────────────────────────────────────────────────
        // 7) NEW — if we never moved, force a re‐path immediately:
        if (position.epsilonEquals(oldPos, 0.01f)) {
            System.out.println("Stuck at " + oldPos + " — recalculating path");
            snapToValidNode();
            findPathToTarget(gameScreen);
        }
    }
    /**
     * Updates the AI's movement direction based on velocity
     */
    private void updateDirection() {
        if (linearVelocity.len2() > 0.01f) {
            // Determine primary cardinal direction based on velocity
            if (Math.abs(linearVelocity.x) > Math.abs(linearVelocity.y)) {
                currentDirection = linearVelocity.x > 0 ? Direction.RIGHT : Direction.LEFT;
            } else {
                currentDirection = linearVelocity.y > 0 ? Direction.UP : Direction.DOWN;
            }
        }
    }

    /**
     * Finds a random target on the map
     */
    private void findRandomTarget(GameScreen gameScreen) {
        if (mapGraph == null) return;

        // Get a random walkable node
        TiledNode randomNode = mapGraph.getRandomWalkableNode();
        if (randomNode != null) {
            targetPosition.set(randomNode.x, randomNode.y);
            hasTarget = true;
            pathNeedsRefresh = true;
            System.out.println("AI choosing random target at: " + targetPosition.x + ", " + targetPosition.y);
        }
    }

    /**
     * Finds an exploration target, preferring unexplored areas
     */
    private void findExplorationTarget(GameScreen gameScreen) {
        if (mapGraph == null) return;

        // Get a node from an unexplored area
        TiledNode explorationNode = mapGraph.getUnexploredNode(position);
        if (explorationNode != null) {
            targetPosition.set(explorationNode.x, explorationNode.y);
            hasTarget = true;
            pathNeedsRefresh = true;
            System.out.println("AI exploring new area at: " + targetPosition.x + ", " + targetPosition.y);
        } else {
            // Fall back to random target if no unexplored areas
            findRandomTarget(gameScreen);
        }
    }

    /**
     * Called when the AI collects a treasure or the player collects one
     */
    public void notifyTreasureCollected(Vector2 treasurePosition, boolean collectedByPlayer) {
        // Notify the database manager
        if (databaseManager != null) {
            databaseManager.notifyTreasureCollected(treasurePosition);
        }

        TiledNode currentNode = mapGraph.getNodeAtWorldCoordinates(position.x, position.y);
        if (currentNode == null) {
            // Try to find nearest valid node to snap to
            // ...
        }


        // If player collected the treasure, activate speed boost
        if (collectedByPlayer) {
            activateSpeedBoost();
        } else {
            // If AI collected the treasure, deactivate speed boost
            isSpeedBoostActive = false;
            moveSpeed = normalMoveSpeed;
            maxLinearSpeed = moveSpeed;
        }

        // Reset target-seeking behavior
        hasTarget = false;
        currentState = AIState.EXPLORING;
    }

    /**
     * Activates the speed boost for the AI
     */
    public void activateSpeedBoost() {
        isSpeedBoostActive = true;
        speedBoostTimer = 0f;
        moveSpeed = fastMoveSpeed;
        maxLinearSpeed = moveSpeed;
        System.out.println("AI speed boost activated! New speed: " + moveSpeed);
    }

    /**
     * Process hint information and update targeting
     */
    public void processHint(String hint, List<Landmark> landmarks) {
        // Force immediate hint processing
        if (databaseManager != null && !hint.isEmpty()) {
            databaseManager.forceHintProcessing(hint, landmarks);
        }
    }

    /**
     * Gets the current target position (for debugging)
     */
    public Vector2 getTargetPosition() {
        if (hasTarget) {
            return new Vector2(targetPosition);
        }
        return null;
    }
    // Add to SmartAI class
    private void snapToValidNode() {
        // Get the AI's current grid position
        TiledNode currentNode = mapGraph.getNodeAtWorldCoordinates(position.x, position.y);

        if (currentNode == null) {
            // Find the closest valid node
            TiledNode nearestNode = null;
            float minDistance = Float.MAX_VALUE;

            for (TiledNode node : mapGraph.getWalkableNodes()) {
                float dist = Vector2.dst(position.x, position.y, node.x, node.y);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestNode = node;
                }
            }

            if (nearestNode != null) {
                position.set(nearestNode.x, nearestNode.y);
            }
        }
    }
    /**
     * Gets the current animation direction
     */
    public Direction getCurrentDirection() {
        return currentDirection;
    }

    // Steerable interface implementation
    @Override
    public Vector2 getPosition() {
        return position;
    }

    @Override
    public float getOrientation() {
        return 0;
    }

    @Override
    public void setOrientation(float orientation) {
    }

    @Override
    public Vector2 getLinearVelocity() {
        return linearVelocity;
    }

    @Override
    public float getAngularVelocity() {
        return 0;
    }

    @Override
    public float getBoundingRadius() {
        return 32;
    }

    @Override
    public boolean isTagged() {
        return tagged;
    }

    @Override
    public void setTagged(boolean tagged) {
        this.tagged = tagged;
    }

    @Override
    public float getZeroLinearSpeedThreshold() {
        return zeroLinearSpeedThreshold;
    }

    @Override
    public void setZeroLinearSpeedThreshold(float value) {
        this.zeroLinearSpeedThreshold = value;
    }

    @Override
    public float getMaxLinearSpeed() {
        return maxLinearSpeed;
    }

    @Override
    public void setMaxLinearSpeed(float maxLinearSpeed) {
        this.maxLinearSpeed = maxLinearSpeed;
    }

    @Override
    public float getMaxLinearAcceleration() {
        return maxLinearAcceleration;
    }

    @Override
    public void setMaxLinearAcceleration(float maxLinearAcceleration) {
        this.maxLinearAcceleration = maxLinearAcceleration;
    }

    @Override
    public float getMaxAngularSpeed() {
        return maxAngularSpeed;
    }

    @Override
    public void setMaxAngularSpeed(float maxAngularSpeed) {
        this.maxAngularSpeed = maxAngularSpeed;
    }

    @Override
    public float getMaxAngularAcceleration() {
        return maxAngularAcceleration;
    }

    @Override
    public void setMaxAngularAcceleration(float maxAngularAcceleration) {
        this.maxAngularAcceleration = maxAngularAcceleration;
    }

    @Override
    public float vectorToAngle(Vector2 vector) {
        // Convert a vector to an angle
        return (float)Math.atan2(vector.y, vector.x);
    }

    @Override
    public Vector2 angleToVector(Vector2 outVector, float angle) {
        // Convert an angle to a vector
        outVector.x = MathUtils.cos(angle);
        outVector.y = MathUtils.sin(angle);
        return outVector;
    }

    @Override
    public Location<Vector2> newLocation() {
        return null;
    }

    /**
     * Returns the visualized path for debugging
     */
    public Array<Vector2> getPathVisualizer() {
        return pathVisualizer;
    }
}
