package com.th.game.ai;

import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.ai.pfa.GraphPath;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import com.badlogic.gdx.ai.steer.Steerable;
import com.badlogic.gdx.ai.steer.SteeringAcceleration;
import com.badlogic.gdx.ai.steer.SteeringBehavior;
import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.th.game.extenders.ai.HistoricalAIData;
import com.th.game.entities.Landmark;
import com.th.game.util.Direction;
import com.th.game.ai.pathfinder.MapHeuristic;
import com.th.game.ai.pathfinder.TiledMapGraph;
import com.th.game.ai.pathfinder.TiledNode;
import com.th.game.screens.GameScreen;

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
    private Direction desiredDirection = currentDirection;
    private float directionTimer = 0f;
    private static final float DIRECTION_CONFIRM_TIME = 0.1f;
    private final Vector2 previousPosition = new Vector2();
    private final Vector2 linearVelocity = new Vector2();
    private boolean movedThisFrame = false;



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
    private static final float DIRECTION_SWITCH_THRESHOLD = 5f;
    private int currentPathIndex;
    private Vector2 targetPosition = new Vector2();
    public boolean hasTarget = false;
    private boolean pathNeedsRefresh = false;
    private float pathRefreshTimer = 0f;
    private final float PATH_REFRESH_INTERVAL = 1.5f;

    // Steering behavior
    private SteeringBehavior<Vector2> steeringBehavior;
    private SteeringAcceleration<Vector2> steeringOutput = new SteeringAcceleration<>(new Vector2());
    private float maxLinearSpeed = 100f;
    private float maxLinearAcceleration = 200f;
    private float maxAngularSpeed = 0f;
    private float maxAngularAcceleration = 0f;
    private float zeroLinearSpeedThreshold = 0.1f;
    private boolean tagged = false;
    private float lastDistanceToTarget = Float.MAX_VALUE;
    private float stuckTimer = 0f;
    private static final float STUCK_THRESHOLD = 1.0f;
    private int repathAttempts = 0;
    private static final int MAX_REPATH_ATTEMPTS = 3;



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
    }

    /**
     * Sets a new target position for the AI to move towards
     */

    public void setTarget(Vector2 targetPos, boolean seekMode) {
        // clamp the incoming position to one‐tile margin
        Vector2 safePos = clampInside(targetPos.cpy());

        this.targetPosition.set(safePos);
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
    private void nudgePositionOffNode() {
        // Only nudge along one axis: pick X or Y at random
        boolean nudgeX = random.nextBoolean();
        float offset = (random.nextBoolean() ? 1 : -1) * 2f; // 2px in either direction

        if (nudgeX) {
            position.x += offset;
        } else {
            position.y += offset;
        }

        // Reset velocity so we don't treat this as diagonal movement
        linearVelocity.setZero();
        // Also reset previousPosition so stuck detection doesn't immediately retrigger
        previousPosition.set(position);
    }

    /**
     * Updates the AI's position and behavior
     */
    public void update(float delta, GameScreen gameScreen) {
        // --- 1) STUCK DETECTION ---
        // --- 8) STUCK DETECTION (after movement) ---
        // --- 4) DATABASE / HINT‑BASED TARGET UPDATES ---
        boolean targetUpdated = databaseManager.updateAITarget(
            delta,
            gameScreen.getCurrentHint(),
            gameScreen.getLandmarks()
        );
        if (hasTarget) {
            if (!movedThisFrame) {
                stuckTimer += delta;
            } else {
                stuckTimer = 0f;
            }

            if (stuckTimer > STUCK_THRESHOLD) {
                onStuck(gameScreen);
                stuckTimer = 0f;
            }
        }

        previousPosition.set(position);

        if (stuckTimer > STUCK_THRESHOLD) {
            onStuck(gameScreen);
            stuckTimer = 0f;
        }

        // --- 2) SPEED BOOST UPDATE ---
        if (isSpeedBoostActive) {
            speedBoostTimer += delta;
            if (speedBoostTimer >= speedBoostDuration) {
                isSpeedBoostActive = false;
                moveSpeed = normalMoveSpeed;
                maxLinearSpeed = moveSpeed;
            }
        }

        // --- 3) PATH REFRESH LOGIC ---
        pathRefreshTimer += delta;
        if ((pathNeedsRefresh || pathRefreshTimer > PATH_REFRESH_INTERVAL) && hasTarget) {
            snapToValidNode();
            findPathToTarget(gameScreen);
            pathRefreshTimer = 0f;
            pathNeedsRefresh = false;
        }

        // --- 5) FALLBACK TARGET SELECTION ---
        if (!hasTarget && !targetUpdated) {
            switch (currentState) {
                case ROAMING:
                    findRandomTarget(gameScreen);
                    break;
                case EXPLORING:
                    findExplorationTarget(gameScreen);
                    break;
                case SEEKING:
                    // If seeking but no target, revert to exploring
                    if (!hasTarget) {
                        currentState = AIState.EXPLORING;
                        findExplorationTarget(gameScreen);
                    }
                    break;
            }
        }

        // --- 6) MOVE ALONG CURRENT PATH ---
        moveAlongPath(delta, gameScreen);

        // --- 7) UPDATE VISUAL FACING DIRECTION ---
        updateDirection(delta);
    }
    private Vector2 clampInside(Vector2 pos) {
        // Grab map and tile dims from your graph
        int mapW = mapGraph.getWidth();   // you may need to add getters in TiledMapGraph
        int mapH = mapGraph.getHeight();
        int tW   = mapGraph.getTileWidth();
        int tH   = mapGraph.getTileHeight();

        float minX = tW;
        float maxX = mapW * tW - tW;
        float minY = tH;
        float maxY = mapH * tH - tH;

        pos.x = MathUtils.clamp(pos.x, minX, maxX);
        pos.y = MathUtils.clamp(pos.y, minY, maxY);
        return pos;
    }

    private void onStuck(GameScreen gameScreen) {
        repathAttempts++;

        // 1) Snap or nudge off the exact same spot
        //    Option A: just snap to nearest valid node:
        snapToValidNode();
        //    Option B: nudgePositionOffNode();
        // nudgePositionOffNode();

        // 2) Clear the old path and target
        currentPath.clear();
        pathNeedsRefresh = false;
        hasTarget = false;

        // 3) Pick a fresh goal
        if (repathAttempts >= MAX_REPATH_ATTEMPTS) {
            repathAttempts = 0;
            findRandomTarget(gameScreen);
        } else {
            findExplorationTarget(gameScreen);
        }

        // 4) Reset velocity again just in case
        linearVelocity.setZero();
        previousPosition.set(position);
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
        movedThisFrame = false;
        if (currentPath.getCount() == 0) return;
        if (currentPathIndex >= currentPath.getCount()) {
            if (position.dst(targetPosition) < 32f) hasTarget = false;
            return;
        }

        // 1) Figure out which cardinal direction we _should_ be heading
        TiledNode nextNode = currentPath.get(currentPathIndex);
        Vector2 nextPos = new Vector2(nextNode.x, nextNode.y);
        float dx = nextPos.x - position.x;
        float dy = nextPos.y - position.y;

        Direction desiredMove;
        if (Math.abs(dx) > Math.abs(dy)) {
            desiredMove = (dx > 0) ? Direction.RIGHT : Direction.LEFT;
        } else {
            desiredMove = (dy > 0) ? Direction.UP : Direction.DOWN;
        }

        // 2) If we're not yet facing that way, just turn—no movement this frame
        if (currentDirection != desiredMove) {
            currentDirection = desiredMove;
            linearVelocity.setZero();
            return;
        }

        // 3) Now that we're facing correctly, compute our velocity vector
        Vector2 moveVec = new Vector2(
            (currentDirection == Direction.RIGHT ? 1 : (currentDirection == Direction.LEFT ? -1 : 0)),
            (currentDirection == Direction.UP    ? 1 : (currentDirection == Direction.DOWN  ? -1 : 0))
        );
        linearVelocity.set(moveVec).scl(moveSpeed);

        // 4) Attempt to move (with your walkability checks)
        Vector2 oldPos     = new Vector2(position);
        Vector2 proposed   = oldPos.cpy().add(linearVelocity.x * delta, linearVelocity.y * delta);
        if (gameScreen.isWalkable(proposed)) {
            position.set(proposed);
            movedThisFrame = true;

        } else {
            // fallback sliding
            for (float scale = 0.9f; scale >= 0.1f; scale -= 0.1f) {
                Vector2 tryPos = oldPos.cpy()
                    .add(linearVelocity.x * delta * scale,
                        linearVelocity.y * delta * scale);
                if (gameScreen.isWalkable(tryPos)) {
                    position.set(proposed);
                    movedThisFrame = true;

                    break;
                }
            }
        }

        // 5) Clamp inside map bounds
        position.set(clampInside(position.cpy()));


        // 6) If we got close enough, advance to the next node
        if (position.dst(nextPos) < 5f) {
            currentPathIndex++;
            linearVelocity.setZero();
            repathAttempts = 0;  // made progress
        }
    }
    /**
     * Call each frame (pass in delta), to debounce flickery direction changes
     */
    private void updateDirection(float delta) {
        float vx = linearVelocity.x;
        float vy = linearVelocity.y;

        // If almost stationary, do nothing
        if (Math.abs(vx) < zeroLinearSpeedThreshold && Math.abs(vy) < zeroLinearSpeedThreshold) {
            return;
        }

        // Compute the “raw” new direction based on which axis dominates
        Direction raw;
        if (Math.abs(vx) > Math.abs(vy)) {
            raw = vx > 0 ? Direction.RIGHT : Direction.LEFT;
        } else {
            raw = vy > 0 ? Direction.UP : Direction.DOWN;
        }

        // If it’s the same as our last raw read, accumulate time
        if (raw == desiredDirection) {
            directionTimer += delta;
        } else {
            // New candidate direction — reset timer
            desiredDirection = raw;
            directionTimer = 0f;
        }

        // Only commit to the new direction once we’ve been in it long enough
        if (desiredDirection != currentDirection && directionTimer >= DIRECTION_CONFIRM_TIME) {
            currentDirection = desiredDirection;
        }
    }


    /**
     * Finds a random target on the map
     */
    private void findRandomTarget(GameScreen gameScreen) {
        if (mapGraph == null) return;

        TiledNode node = mapGraph.getRandomWalkableNode();
        if (node != null) {
            // centralizes clamping + flag‐setting
            setTarget(new Vector2(node.x, node.y), false);
        }
    }

    /**
     * Finds an exploration target, preferring unexplored areas
     */
    private void findExplorationTarget(GameScreen gameScreen) {
        if (mapGraph == null) return;

        TiledNode node = mapGraph.getUnexploredNode(position);
        if (node != null) {
            setTarget(new Vector2(node.x, node.y), false);
        } else {
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
        // Only process non-empty hints
        if (hint == null || hint.isEmpty()) {
            return;
        }

        System.out.println("SmartAI processing hint: " + hint);

        // Force immediate hint processing
        if (databaseManager != null) {
            databaseManager.forceHintProcessing(hint, landmarks);

            // We need to ensure the AI is in SEEKING state when a hint is processed
            this.currentState = AIState.SEEKING;
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
