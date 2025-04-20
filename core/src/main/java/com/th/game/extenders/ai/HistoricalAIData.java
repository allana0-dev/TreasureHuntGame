package com.th.game.extenders.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.th.game.ai.SmartAI;
import com.th.game.database.TrainingDataDAO;
import com.th.game.database.TreasureCollectionData;
import com.th.game.entities.Landmark;

import java.sql.SQLException;
import java.util.*;

/**
 * Manages AI behavior based on historical treasure collection data.
 * Uses a database to learn from past gameplay and improve treasure hunting strategy.
 */
public class HistoricalAIData {
    // Behavior constants
    private static final float DATABASE_LOCATION_WEIGHT = 1.0f;
    private static final float HINT_PRIORITY = 0.9f;
    private static final float MIN_ROAMING_DURATION = 5f;
    private static final float MAX_ROAMING_DURATION = 15f;
    private static final float LOCATION_HOTSPOT_RADIUS = 150f;
    private static final int MAX_CACHED_LOCATIONS = 50;

    // Movement state tracking
    private String currentMapName;
    private SmartAI smartAI;
    private Random random = new Random();
    private float roamingTimer = 0f;
    private float currentRoamingDuration = 15f;
    private boolean isRoaming = false;
    private boolean hasProcessedHint = false;
    private Vector2 lastDatabaseTarget = null;

    // Hint following
    private boolean isFollowingHint = false;
    private float hintFollowDuration = 30f;
    private float hintFollowTimer = 0f;
    private Vector2 hintLandmarkPosition = null;
    private float hintSearchRadius = 50f;
    private float maxHintSearchRadius = 300f;
    private float hintSearchExpansionRate = 25f;
    private float lastHintSearchTime = 0f;

    // Cached locations from database
    private List<Vector2> treasureHotspots = new ArrayList<>();
    private List<Vector2> visitedHotspots = new ArrayList<>();

    /**
     * Creates a new database manager for enhancing AI behavior
     */
    public HistoricalAIData(SmartAI smartAI, String mapName) {
        this.smartAI = smartAI;
        this.currentMapName = mapName;
        loadTreasureHotspotsFromDatabase();

        // Set initial roaming duration
        currentRoamingDuration = MIN_ROAMING_DURATION +
            random.nextFloat() * (MAX_ROAMING_DURATION - MIN_ROAMING_DURATION);
    }

    /**
     * Loads treasure collection locations from the database
     */
    private void loadTreasureHotspotsFromDatabase() {
        treasureHotspots.clear();
        visitedHotspots.clear();

        try {
            List<TreasureCollectionData> collections =
                TrainingDataDAO.getCollectionDataByMap(currentMapName);

            if (collections.isEmpty()) {
                System.out.println("No historical data found for map: " + currentMapName);
                return;
            }

            System.out.println("Loaded " + collections.size() +
                " historical treasure locations for map: " + currentMapName);

            // First, collect all potential hotspots
            List<Vector2> allTreasureLocations = new ArrayList<>();
            for (TreasureCollectionData data : collections) {
                allTreasureLocations.add(data.getTreasurePosition());
            }

            // Calculate map dimensions
            float maxX = 0, maxY = 0;
            for (Vector2 pos : allTreasureLocations) {
                maxX = Math.max(maxX, pos.x);
                maxY = Math.max(maxY, pos.y);
            }

            // Adjust hotspot limit based on map size
            int adjustedHotspotLimit = Math.min(50,
                Math.max(10, (int)(Math.sqrt(maxX * maxY) / 200)));

            // Ensure minimum distance between hotspots
            float minHotspotDistance = LOCATION_HOTSPOT_RADIUS * 1.5f;
            // Primary: well‐distributed one per quadrant
            selectDistributedHotspots(allTreasureLocations, adjustedHotspotLimit, minHotspotDistance);

            // Fallback: if distribution yielded too few, do a greedy pick
            if (treasureHotspots.size() < Math.min(3, allTreasureLocations.size())) {
                selectGreedyHotspots(allTreasureLocations, adjustedHotspotLimit, minHotspotDistance);
            }

            Collections.shuffle(treasureHotspots, random);


        } catch (SQLException e) {
            System.err.println("Error loading treasure hotspots from database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Selects hotspots that are well-distributed across the map
     */
    private void selectDistributedHotspots(List<Vector2> allLocations, int limit, float minDistance) {
        // If we have very few locations, just use them all
        if (allLocations.size() <= limit) {
            treasureHotspots.addAll(allLocations);
            return;
        }

        // Find the center of all points
        float sumX = 0, sumY = 0;
        for (Vector2 pos : allLocations) {
            sumX += pos.x;
            sumY += pos.y;
        }
        float centerX = sumX / allLocations.size();
        float centerY = sumY / allLocations.size();

        // Divide the map into quadrants
        List<List<Vector2>> quadrants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            quadrants.add(new ArrayList<>());
        }

        // Assign points to quadrants
        for (Vector2 pos : allLocations) {
            int quadrantIndex = (pos.x >= centerX ? 1 : 0) + (pos.y >= centerY ? 2 : 0);
            quadrants.get(quadrantIndex).add(pos);
        }

        // Calculate hotspots per quadrant based on density
        int[] hotspotsPerQuadrant = new int[4];
        int remainingHotspots = limit;

        for (int i = 0; i < 4; i++) {
            if (!quadrants.get(i).isEmpty()) {
                hotspotsPerQuadrant[i] = Math.max(1,
                    (int)((float)quadrants.get(i).size() / allLocations.size() * limit));
                remainingHotspots -= hotspotsPerQuadrant[i];
            }
        }

        // Redistribute any remaining
        while (remainingHotspots > 0) {
            int maxPointsQuadrant = 0;
            int maxPoints = -1;

            for (int i = 0; i < 4; i++) {
                if (!quadrants.get(i).isEmpty() && quadrants.get(i).size() > maxPoints) {
                    maxPoints = quadrants.get(i).size();
                    maxPointsQuadrant = i;
                }
            }

            hotspotsPerQuadrant[maxPointsQuadrant]++;
            remainingHotspots--;
        }
        float minHotspotDistance = LOCATION_HOTSPOT_RADIUS * 2.5f; // Increased from 1.5f


        // Select hotspots from each quadrant
// Also, when selecting the first point in each quadrant, prefer points further from the center
        for (int q = 0; q < 4; q++) {
            List<Vector2> quadrantLocations = quadrants.get(q);
            if (quadrantLocations.isEmpty()) continue;

            // Sort points by distance from map center (furthest first)
            final Vector2 mapCenter = new Vector2(centerX, centerY);
            quadrantLocations.sort(new Comparator<Vector2>() {
                @Override
                public int compare(Vector2 v1, Vector2 v2) {
                    return Float.compare(v2.dst(mapCenter), v1.dst(mapCenter));
                }
            });

            // Take the first point (furthest from center) as our starting point
            if (!quadrantLocations.isEmpty()) {
                treasureHotspots.add(new Vector2(quadrantLocations.get(0)));
            }
        }    }

    /**
     * Uses a greedy algorithm to select well-spaced hotspots
     */
    private void selectGreedyHotspots(List<Vector2> locations, int limit, float minDistance) {
        if (locations.isEmpty()) return;

        // Start with the most central point
        Vector2 center = new Vector2();
        for (Vector2 pos : locations) {
            center.add(pos);
        }
        center.scl(1f / locations.size());

        Vector2 firstHotspot = null;
        float minDistToCenter = Float.MAX_VALUE;

        for (Vector2 pos : locations) {
            float dist = pos.dst(center);
            if (dist < minDistToCenter) {
                minDistToCenter = dist;
                firstHotspot = pos;
            }
        }

        // Add the first hotspot
        List<Vector2> selectedHotspots = new ArrayList<>();
        selectedHotspots.add(new Vector2(firstHotspot));

        // Greedily add the remaining hotspots
        while (selectedHotspots.size() < limit && selectedHotspots.size() < locations.size()) {
            Vector2 bestCandidate = null;
            float maxMinDistance = -1;

            for (Vector2 candidate : locations) {
                boolean alreadySelected = false;
                for (Vector2 selected : selectedHotspots) {
                    if (candidate.dst(selected) < 0.1f) {
                        alreadySelected = true;
                        break;
                    }
                }

                if (alreadySelected) continue;

                // Find minimum distance to any already-selected hotspot
                float minDistToSelected = Float.MAX_VALUE;
                for (Vector2 selected : selectedHotspots) {
                    minDistToSelected = Math.min(minDistToSelected, candidate.dst(selected));
                }

                // Keep the candidate with the largest minimum distance
                if (minDistToSelected > maxMinDistance) {
                    maxMinDistance = minDistToSelected;
                    bestCandidate = candidate;
                }
            }

            // If we found a suitable candidate, add it
            if (bestCandidate != null) {
                if (maxMinDistance >= minDistance) {
                    selectedHotspots.add(new Vector2(bestCandidate));
                } else if (selectedHotspots.size() < limit) {
                    // If we can't maintain min distance but still need more hotspots
                    selectedHotspots.add(new Vector2(bestCandidate));
                } else {
                    // We have enough hotspots
                    break;
                }
            } else {
                // No more candidates available
                break;
            }
        }

        // Add all selected hotspots to the main list
        treasureHotspots.addAll(selectedHotspots);
    }


    /**
     * Updates the AI target based on hints and historical data
     */
    public boolean updateAITarget(float delta, String currentHint, List<Landmark> landmarks) {
        // Priority 1: If we're in "hint mode", always keep the hint target alive
        if (isFollowingHint && hintLandmarkPosition != null) {
            return updateHintFollowing(delta);
        }

        // Priority 2: Process new hints
        if (!currentHint.isEmpty() && !hasProcessedHint) {
            processHint(currentHint, landmarks);
            hasProcessedHint = true;
            return true;
        }

        // Priority 3: Use database or roaming as fallback
        if (random.nextFloat() < DATABASE_LOCATION_WEIGHT && !treasureHotspots.isEmpty()) {
            return setDatabaseTarget();
        } else {
            startRoaming();
            return false;
        }
    }

    /**
     * Forces immediate hint processing
     */
    public void forceHintProcessing(String hint, List<Landmark> landmarks) {
        // Reset all exploration/roaming states
        isRoaming = false;
        roamingTimer = 0f;
        hasProcessedHint = false;

        // Reset hint following state to start fresh
        isFollowingHint = false;
        hintFollowTimer = 0f;
        hintLandmarkPosition = null;
        hintSearchRadius = 50f;
        lastHintSearchTime = 0f;

        // Process the hint to find a matching landmark
        boolean hintProcessed = processHint(hint, landmarks);

        if (hintProcessed) {
            hasProcessedHint = true;
            isFollowingHint = true; // Make sure this is set to true
            hintFollowTimer = 0f;   // Start the timer fresh
            System.out.println("AI following hint: " + hint);
        } else {
            System.out.println("Failed to process hint: " + hint);
            // Fall back to a database target
            if (!treasureHotspots.isEmpty()) {
                setDatabaseTarget();
            }
        }
    }

    /**
     * Processes a hint and directs the AI toward the mentioned landmark
     */
    private boolean processHint(String hint, List<Landmark> landmarks) {
        if (hint == null || landmarks == null || landmarks.isEmpty()) {
            return false;
        }

        hint = hint.toLowerCase();
        System.out.println("Processing hint: " + hint);

        // Look for landmarks mentioned in the hint
        for (Landmark landmark : landmarks) {
            String landmarkName = landmark.name.replace("_", " ").toLowerCase();

            if (hint.contains(landmarkName) || landmarkName.contains(hint)) {
                // Target the landmark
                smartAI.setTarget(landmark.position, true);
                System.out.println("AI targeting landmark from hint: " + landmark.name);

                // Store the landmark position for exploration
                hintLandmarkPosition = new Vector2(landmark.position);

                // Reset hint exploration parameters
                hintSearchRadius = 50f;
                lastHintSearchTime = 0f;
                isFollowingHint = true;
                hintFollowTimer = 0f;

                return true;
            }
        }

        System.out.println("No landmarks matched the hint: " + hint);
        return false;
    }

    /**
     * Sets a target based on historical treasure locations
     */
    private boolean setDatabaseTarget() {
        if (treasureHotspots.isEmpty()) {
            return false;
        }

        // Check if all hotspots have been visited
        if (visitedHotspots.size() >= treasureHotspots.size()) {
            visitedHotspots.clear();
        }

        // Find the best hotspot target
        Vector2 bestTarget = null;
        float bestScore = Float.MAX_VALUE;

        for (Vector2 hotspot : treasureHotspots) {
            boolean alreadyVisited = false;
            for (Vector2 visited : visitedHotspots) {
                if (hotspot.dst(visited) < LOCATION_HOTSPOT_RADIUS) {
                    alreadyVisited = true;
                    break;
                }
            }

            if (!alreadyVisited) {
                // Score based on distance and randomness
                float distance = smartAI.getPosition().dst(hotspot);
                float randomFactor = random.nextFloat() * 200;
                float score = distance + randomFactor;

                // Prefer targets in the general direction we're already moving
                if (lastDatabaseTarget != null) {
                    Vector2 currentDirection = new Vector2(hotspot).sub(smartAI.getPosition()).nor();
                    Vector2 lastDirection = new Vector2(lastDatabaseTarget).sub(smartAI.getPosition()).nor();
                    float dotProduct = currentDirection.dot(lastDirection);

                    if (dotProduct > 0.7f) {
                        score *= 0.8f;
                    }
                }

                if (score < bestScore) {
                    bestScore = score;
                    bestTarget = hotspot;
                }
            }
        }

        if (bestTarget != null) {
            // Target this hotspot
            smartAI.setTarget(new Vector2(bestTarget), true);
            lastDatabaseTarget = new Vector2(bestTarget);
            visitedHotspots.add(new Vector2(bestTarget));
            return true;
        }

        // If we couldn't find an unvisited hotspot, pick one with the best score
        bestScore = Float.MAX_VALUE;
        for (Vector2 hotspot : treasureHotspots) {
            float distance = smartAI.getPosition().dst(hotspot);
            float randomFactor = random.nextFloat() * 100;
            float score = distance + randomFactor;

            if (score < bestScore) {
                bestScore = score;
                bestTarget = hotspot;
            }
        }

        if (bestTarget != null) {
            smartAI.setTarget(new Vector2(bestTarget), true);
            lastDatabaseTarget = new Vector2(bestTarget);
            return true;
        }

        return false;
    }

    /**
     * Starts a roaming period for exploration
     */
    private void startRoaming() {
        // First try to target a database location if possible
        if (!treasureHotspots.isEmpty() && random.nextFloat() < 0.7f) {
            setDatabaseTarget();
            return;
        }

        // Only roam if we couldn't find a database target
        isRoaming = true;
        roamingTimer = 0f;
        currentRoamingDuration = MIN_ROAMING_DURATION +
            random.nextFloat() * (MAX_ROAMING_DURATION - MIN_ROAMING_DURATION);
        smartAI.setExplorationMode(true);
    }

    /**
     * Notifies the manager that a treasure was collected
     */
    public void notifyTreasureCollected(Vector2 treasurePosition) {
        // Check if this is a new hotspot
        boolean isNewHotspot = true;

        // Reset hint following
        isFollowingHint = false;
        hintLandmarkPosition = null;

        for (Vector2 hotspot : treasureHotspots) {
            if (hotspot.dst(treasurePosition) < LOCATION_HOTSPOT_RADIUS) {
                isNewHotspot = false;
                break;
            }
        }

        // If it's a new hotspot, add it
        if (isNewHotspot && treasureHotspots.size() < MAX_CACHED_LOCATIONS) {
            treasureHotspots.add(new Vector2(treasurePosition));
        }

        // Reset to exploration mode
        startRoaming();
    }

    /**
     * Updates the hint exploration behavior
     */
    private boolean updateHintFollowing(float delta) {
        if (!isFollowingHint || hintLandmarkPosition == null) {
            return false;
        }

        hintFollowTimer += delta;
        lastHintSearchTime += delta;

        // Get the AI's current position
        Vector2 currentPosition = smartAI.getPosition();
        float distToLandmark = currentPosition.dst(hintLandmarkPosition);

        // First phase: Get to the landmark location
        if (distToLandmark > 30f) {
            // We're still moving toward the landmark, make sure we have a target
            if (!smartAI.hasTarget || smartAI.getTargetPosition() == null ||
                smartAI.getTargetPosition().dst(hintLandmarkPosition) > 10f) {
                smartAI.setTarget(new Vector2(hintLandmarkPosition), true);
            }
            return true;
        }

        // Second phase: Explore the area around the landmark

        // First try to target any database hotspots that are within our search radius
        if (lastHintSearchTime > 1.0f) {
            // Find the closest hotspot within our search radius
            Vector2 closestHotspot = null;
            float closestDist = Float.MAX_VALUE;

            for (Vector2 hotspot : treasureHotspots) {
                float distToHotspot = hotspot.dst(hintLandmarkPosition);
                // Only consider hotspots within our current search radius
                if (distToHotspot <= hintSearchRadius) {
                    // Check if we've already visited this hotspot
                    boolean alreadyVisited = false;
                    for (Vector2 visited : visitedHotspots) {
                        if (hotspot.dst(visited) < 20f) {
                            alreadyVisited = true;
                            break;
                        }
                    }

                    if (!alreadyVisited && distToHotspot < closestDist) {
                        closestDist = distToHotspot;
                        closestHotspot = hotspot;
                    }
                }
            }

            // If we found a valid hotspot, target it
            if (closestHotspot != null) {
                smartAI.setTarget(new Vector2(closestHotspot), true);
                visitedHotspots.add(new Vector2(closestHotspot));
                lastHintSearchTime = 0f;
                return true;
            }
        }

        // Update search radius (expand search area gradually)
        hintSearchRadius += hintSearchExpansionRate * delta;
        if (hintSearchRadius > maxHintSearchRadius) {
            hintSearchRadius = maxHintSearchRadius;
        }

        // If no valid hotspots or timer expired, pick random exploration targets
        if (lastHintSearchTime > 2.0f) {
            lastHintSearchTime = 0f;

            // Choose a random direction and distance from landmark
            float angle = random.nextFloat() * MathUtils.PI2;
            // Use sqrt for better distribution (more exploration toward the edges)
            float distance = hintSearchRadius * (float)Math.sqrt(random.nextFloat());

            // Calculate the new target position
            Vector2 targetOffset = new Vector2(
                MathUtils.cos(angle) * distance,
                MathUtils.sin(angle) * distance
            );
            Vector2 newTarget = new Vector2(hintLandmarkPosition).add(targetOffset);

            // Set the new target
            smartAI.setTarget(newTarget, true);
        }

        // If we've been exploring too long without finding anything, give up
        if (hintFollowTimer >= hintFollowDuration) {
            System.out.println("Hint follow duration expired, returning to normal targeting");
            isFollowingHint = false;
            hintLandmarkPosition = null;
            visitedHotspots.clear(); // Clear visited hotspots when we stop following the hint

            // Check nearby hotspots as a fallback
            if (random.nextFloat() < 0.7f && !treasureHotspots.isEmpty()) {
                return setDatabaseTarget();
            } else {
                startRoaming();
                return false;
            }
        }

        return true;
    }

}
