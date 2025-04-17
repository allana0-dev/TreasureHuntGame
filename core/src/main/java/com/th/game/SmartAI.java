package com.th.game;

import com.badlogic.gdx.math.*;
import com.badlogic.gdx.ai.steer.behaviors.*;
import com.badlogic.gdx.ai.steer.*;
import com.badlogic.gdx.ai.utils.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.sql.SQLException;

public class SmartAI extends AIPlayer implements Steerable<Vector2> {
    private final String currentMapName;
    private Random random;
    private Vector2 targetPosition;
    private Vector2 linearVelocity;
    private float angularVelocity;
    private float maxLinearSpeed = 140f;
    private float maxLinearAcceleration = 500f;
    private float maxAngularSpeed = 5f;
    private float maxAngularAcceleration = 10f;
    private float zeroLinearSpeedThreshold = 0.1f;
    private boolean tagged;
    private float boundingRadius = 32f;

    private float changeDirectionTimer = 0;
    private final float DIRECTION_CHANGE_TIME = 2.5f; // Change direction every 2.5 seconds
    private List<Vector2> historicalTreasureLocations;
    private int currentPathIndex = 0;
    private boolean isFollowingHistoricalPath = false;
    private Direction currentDirection = Direction.DOWN;
    private float pathFollowingProbability = 0.7f; // 70% chance to follow historical paths
    private float stuckTimer = 0f;
    private Vector2 lastPosition = new Vector2();
    private SteeringBehavior<Vector2> steeringBehavior;
    private SteeringAcceleration<Vector2> steeringOutput;

    // Define possible movement directions
    private final Vector2[] DIRECTIONS = {
        new Vector2(0, 1),   // UP
        new Vector2(0, -1),  // DOWN
        new Vector2(-1, 0),  // LEFT
        new Vector2(1, 0)    // RIGHT
    };

    public SmartAI(Vector2 startPos, String mapName) {
        super(startPos);
        this.currentMapName = mapName;
        this.random = new Random();
        this.targetPosition = new Vector2(startPos);
        this.linearVelocity = new Vector2();
        this.historicalTreasureLocations = new ArrayList<>();
        this.steeringOutput = new SteeringAcceleration<>(new Vector2());
        this.lastPosition.set(startPos);

        // Load historical treasure locations from database
        loadHistoricalTreasureLocations();
    }

    private void loadHistoricalTreasureLocations() {
        try {
            List<TreasureCollectionData> collections = TrainingDataDAO.getCollectionDataByMap(currentMapName);
            for (TreasureCollectionData data : collections) {
                historicalTreasureLocations.add(data.getTreasurePosition());
            }
            System.out.println("Loaded " + historicalTreasureLocations.size() + " historical treasure locations for map: " + currentMapName);
        } catch (SQLException e) {
            System.err.println("Failed to load historical treasure data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void update(float delta, GameScreen gameScreen) {
        // Check if we're stuck
        checkIfStuck(delta);

        // Update timers
        changeDirectionTimer += delta;

        // Check if it's time to change direction or target
        if (changeDirectionTimer >= DIRECTION_CHANGE_TIME ||
            position.dst(targetPosition) < 10 ||
            stuckTimer > 1.0f) {

            changeDirectionTimer = 0;
            stuckTimer = 0;
            lastPosition.set(position);

            // Decide whether to follow historical paths or explore randomly
            if (historicalTreasureLocations.size() > 0 && random.nextFloat() < pathFollowingProbability) {
                // Follow historical treasure locations
                if (!isFollowingHistoricalPath || currentPathIndex >= historicalTreasureLocations.size()) {
                    isFollowingHistoricalPath = true;
                    currentPathIndex = random.nextInt(historicalTreasureLocations.size());
                }

                Vector2 historicalTarget = historicalTreasureLocations.get(currentPathIndex);

                // Adjust target if not walkable
                if (!gameScreen.isWalkable(historicalTarget)) {
                    // Find nearby walkable location
                    float searchRadius = 50f;
                    Vector2 bestTarget = findNearbyWalkablePosition(historicalTarget, searchRadius, gameScreen);
                    if (bestTarget != null) {
                        targetPosition = bestTarget;
                    } else {
                        // If can't find walkable position near historical target, choose random
                        chooseRandomDirection(gameScreen);
                    }
                } else {
                    targetPosition = historicalTarget;
                }

                // Move to next location in history for next update
                currentPathIndex = (currentPathIndex + 1) % historicalTreasureLocations.size();
            } else {
                // Random exploration
                isFollowingHistoricalPath = false;
                chooseRandomDirection(gameScreen);
            }

            // Setup steering behavior
            setupSteeringBehavior(gameScreen);
        }

        // Apply steering behavior
        if (steeringBehavior != null) {
            steeringBehavior.calculateSteering(steeringOutput);
            applySteering(steeringOutput, delta, gameScreen);
        } else {
            // Fallback to basic movement
            moveTowardsTarget(delta, gameScreen);
        }

        // Update direction for animation based on velocity
        updateDirection();
    }

    private void checkIfStuck(float delta) {
        if (position.dst(lastPosition) < 2f) {
            stuckTimer += delta;
        } else {
            stuckTimer = 0;
            lastPosition.set(position);
        }
    }

    private Vector2 findNearbyWalkablePosition(Vector2 center, float radius, GameScreen gameScreen) {
        Vector2 bestPos = null;
        float bestDistance = Float.MAX_VALUE;

        // Try the 4 cardinal directions at different distances
        for (int dist = 10; dist <= radius; dist += 10) {
            for (Vector2 dir : DIRECTIONS) {
                Vector2 testPos = new Vector2(center).add(dir.x * dist, dir.y * dist);
                if (gameScreen.isWalkable(testPos)) {
                    float distance = center.dst(testPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = new Vector2(testPos);
                    }
                }
            }

            // If we found a position, return it
            if (bestPos != null) {
                return bestPos;
            }
        }

        // No walkable position found
        return null;
    }

    private void setupSteeringBehavior(final GameScreen gameScreen) {
        // Create a target for the AI to move towards
        final Steerable<Vector2> targetActor = new Steerable<Vector2>() {
            private Vector2 position = new Vector2(targetPosition);

            @Override public Vector2 getPosition() { return position; }
            @Override public float getOrientation() { return 0; }

            @Override
            public void setOrientation(float v) {

            }

            @Override public Vector2 getLinearVelocity() { return new Vector2(); }
            @Override public float getAngularVelocity() { return 0; }
            @Override public float getBoundingRadius() { return 0; }
            @Override public boolean isTagged() { return false; }
            @Override public void setTagged(boolean tagged) { }
            @Override public float getMaxLinearSpeed() { return 0; }
            @Override public void setMaxLinearSpeed(float maxLinearSpeed) { }
            @Override public float getMaxLinearAcceleration() { return 0; }
            @Override public void setMaxLinearAcceleration(float maxLinearAcceleration) { }
            @Override public float getMaxAngularSpeed() { return 0; }
            @Override public void setMaxAngularSpeed(float maxAngularSpeed) { }
            @Override public float getMaxAngularAcceleration() { return 0; }
            @Override public void setMaxAngularAcceleration(float maxAngularAcceleration) { }
            @Override public float getZeroLinearSpeedThreshold() { return 0; }
            @Override public void setZeroLinearSpeedThreshold(float value) { }
            public Vector2 newVector() { return new Vector2(); }
            @Override public float vectorToAngle(Vector2 vector) { return (float)Math.atan2(-vector.x, vector.y); }
            @Override public Vector2 angleToVector(Vector2 outVector, float angle) {
                outVector.x = -(float)Math.sin(angle);
                outVector.y = (float)Math.cos(angle);
                return outVector;
            }

            @Override
            public Location<Vector2> newLocation() {
                return null;
            }
        };

        // Create a seek behavior (tries to reach the target)
        Seek<Vector2> seekBehavior = new Seek<>(this, targetActor);

        // Create the arrive behavior (slows down as it approaches target)
        Arrive<Vector2> arriveBehavior = new Arrive<>(this, targetActor)
            .setTimeToTarget(0.1f)
            .setArrivalTolerance(5f)
            .setDecelerationRadius(50f);

        // Set the seek behavior as our steering behavior
        steeringBehavior = arriveBehavior;
    }

    private void applySteering(SteeringAcceleration<Vector2> steering, float delta, GameScreen gameScreen) {
        // Update position and linear velocity
        Vector2 oldPosition = new Vector2(position);

        // Apply linear acceleration
        linearVelocity.x += steering.linear.x * delta;
        linearVelocity.y += steering.linear.y * delta;

        // Cap the linear speed
        float speed = linearVelocity.len();
        if (speed > maxLinearSpeed) {
            linearVelocity.scl(maxLinearSpeed / speed);
        }

        // Apply damping to gradually reduce speed when not accelerating
        if (steering.linear.len() < 0.01f) {
            float dampingFactor = 0.9f;
            linearVelocity.scl(dampingFactor);
        }

        // CARDINAL MOVEMENT FIX: Force movement to be cardinal (horizontal OR vertical, not both)
        if (Math.abs(linearVelocity.x) > Math.abs(linearVelocity.y)) {
            // Horizontal movement is dominant, zero out vertical
            linearVelocity.y = 0;
        } else {
            // Vertical movement is dominant, zero out horizontal
            linearVelocity.x = 0;
        }

        // Move position based on velocity
        position.x += linearVelocity.x * delta;
        position.y += linearVelocity.y * delta;

        // Check if the new position is walkable
        if (!gameScreen.isWalkable(position)) {
            position.set(oldPosition);
            linearVelocity.set(0, 0);
            changeDirectionTimer = DIRECTION_CHANGE_TIME; // Force direction change
        }
    }
    private void chooseRandomDirection(GameScreen gameScreen) {
        // Shuffle the directions to try them in a random order
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < DIRECTIONS.length; i++) {
            indices.add(i);
        }

        java.util.Collections.shuffle(indices, random);

        // Try different directions and distances
        float[] distances = {200f, 160f, 100f, 64f, 32f};

        for (float moveDistance : distances) {
            for (int i : indices) {
                Vector2 direction = DIRECTIONS[i];
                Vector2 newTarget = new Vector2(position).add(direction.x * moveDistance, direction.y * moveDistance);

                // Check if the path is walkable
                boolean pathClear = true;
                Vector2 step = new Vector2();
                int steps = 5; // Check 5 points along the path

                for (int j = 1; j <= steps; j++) {
                    float t = j / (float)steps;
                    step.set(position).lerp(newTarget, t);

                    if (!gameScreen.isWalkable(step)) {
                        pathClear = false;
                        break;
                    }
                }

                if (pathClear) {
                    targetPosition = newTarget;

                    // Update direction for animation
                    if (direction.y > 0) currentDirection = Direction.UP;
                    else if (direction.y < 0) currentDirection = Direction.DOWN;
                    else if (direction.x < 0) currentDirection = Direction.LEFT;
                    else if (direction.x > 0) currentDirection = Direction.RIGHT;

                    return;
                }
            }
        }

        // If all attempts failed, stay in place but reset the stuck timer
        targetPosition = new Vector2(position);
    }

    private void moveTowardsTarget(float delta, GameScreen gameScreen) {
        Vector2 oldPosition = new Vector2(position);

        // Calculate vector to target
        Vector2 toTarget = new Vector2(targetPosition).sub(position);

        // Determine cardinal direction (moving only horizontally OR vertically)
        Vector2 moveDir = new Vector2(0, 0);

        // Decide which cardinal direction to move based on distance
        if (Math.abs(toTarget.x) > Math.abs(toTarget.y)) {
            // Move horizontally
            if (toTarget.x > 0) {
                moveDir.x = 1;
                currentDirection = Direction.RIGHT;
            } else if (toTarget.x < 0) {
                moveDir.x = -1;
                currentDirection = Direction.LEFT;
            }
        } else {
            // Move vertically
            if (toTarget.y > 0) {
                moveDir.y = 1;
                currentDirection = Direction.UP;
            } else if (toTarget.y < 0) {
                moveDir.y = -1;
                currentDirection = Direction.DOWN;
            }
        }

        // Move in the chosen cardinal direction
        float speed = 120f;
        position.add(moveDir.x * speed * delta, moveDir.y * speed * delta);

        // If new position is not walkable, revert to old position
        if (!gameScreen.isWalkable(position)) {
            position.set(oldPosition);
            changeDirectionTimer = DIRECTION_CHANGE_TIME; // Force direction change
        }
    }

    private void updateDirection() {
        // Update direction based on velocity for more responsive animation
        if (linearVelocity.len2() > 0.1f) {
            if (Math.abs(linearVelocity.x) > Math.abs(linearVelocity.y)) {
                if (linearVelocity.x > 0) {
                    currentDirection = Direction.RIGHT;
                } else {
                    currentDirection = Direction.LEFT;
                }
            } else {
                if (linearVelocity.y > 0) {
                    currentDirection = Direction.UP;
                } else {
                    currentDirection = Direction.DOWN;
                }
            }
        }
    }

    public Direction getCurrentDirection() {
        return currentDirection;
    }

    // Steerable interface methods
    @Override
    public Vector2 getPosition() {
        return position;
    }

    @Override
    public float getOrientation() {
        return 0;
    }

    @Override
    public void setOrientation(float v) {

    }

    @Override
    public Vector2 getLinearVelocity() {
        return linearVelocity;
    }

    @Override
    public float getAngularVelocity() {
        return angularVelocity;
    }

    @Override
    public float getBoundingRadius() {
        return boundingRadius;
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
    public float getZeroLinearSpeedThreshold() {
        return zeroLinearSpeedThreshold;
    }

    @Override
    public void setZeroLinearSpeedThreshold(float value) {
        this.zeroLinearSpeedThreshold = value;
    }

    public Vector2 newVector() {
        return new Vector2();
    }

    @Override
    public float vectorToAngle(Vector2 vector) {
        return (float)Math.atan2(-vector.x, vector.y);
    }

    @Override
    public Vector2 angleToVector(Vector2 outVector, float angle) {
        outVector.x = -(float)Math.sin(angle);
        outVector.y = (float)Math.cos(angle);
        return outVector;
    }

    @Override
    public Location<Vector2> newLocation() {
        return null;
    }
}
