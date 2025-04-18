package com.th.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Manages advanced AI movement behavior based on historical treasure collection data
 * stored in the database. This class enhances SmartAI with more intelligent
 * target selection based on previous successful treasure hunts.
 */
public class HistoricalAIData {
    // Constants for behavior tuning
    private static final float DATABASE_LOCATION_WEIGHT = 0.9f; // 90% chance to use database location
    private static final float HINT_PRIORITY = 0.9f; // 90% chance to prioritize hint when available
    // Reduce the roaming duration ranges
    private static final float MIN_ROAMING_DURATION = 5f; // Reduced from 10f
    private static final float MAX_ROAMING_DURATION = 15f; // Reduced from 25f
    private static final float LOCATION_HOTSPOT_RADIUS = 150f; // Radius to consider as a "hotspot" area
    private static final int MAX_CACHED_LOCATIONS = 50; // Maximum number of locations to keep in memory


    // Movement state tracking
    private String currentMapName;
    private SmartAI smartAI;
    private Random random = new Random();
    private float roamingTimer = 0f;
    private float currentRoamingDuration = 15f; // Default duration
    private boolean isRoaming = false;
    private boolean hasProcessedHint = false;
    private Vector2 lastDatabaseTarget = null;
    private boolean isFollowingHint = false;
    private float hintFollowDuration = 30f; // Follow hint for 30 seconds before considering other targets
    private float hintFollowTimer = 0f;
    // Add these as new fields in HistoricalAIData
// Add these as new fields in HistoricalAIData
    private Vector2 hintLandmarkPosition = null;
    private float hintSearchRadius = 50f; // Initial search radius
    private float maxHintSearchRadius = 300f; // Maximum search radius
    private float hintSearchExpansionRate = 25f; // Radius expansion per second
    private float lastHintSearchTime = 0f; // Track when we last searched around hint
    private TreasureChest targetedTreasureChest = null; // Track which treasure the hint is referring to



    // Cached locations from database for current map
    private List<Vector2> treasureHotspots = new ArrayList<>();
    private List<Vector2> visitedHotspots = new ArrayList<>();

    /**
     * Creates a new DatabaseAIManager for enhanced AI movement behavior
     *
     * @param smartAI The SmartAI instance to enhance
     * @param mapName The current map name for context
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
     * Loads the treasure collection locations from the database for the current map
     * and identifies "hotspot" areas with successful treasure finds
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

            // Calculate map dimensions by finding max X and Y from treasure positions
            float maxX = 0, maxY = 0;
            for (Vector2 pos : allTreasureLocations) {
                maxX = Math.max(maxX, pos.x);
                maxY = Math.max(maxY, pos.y);
            }

            // Adjust hotspot limit based on map size
            int adjustedHotspotLimit = Math.min(50,
                Math.max(10, (int)(Math.sqrt(maxX * maxY) / 200)));

            // Ensure minimum distance between hotspots (more spread)
            float minHotspotDistance = LOCATION_HOTSPOT_RADIUS * 1.5f;

            // Use a greedy algorithm to select well-distributed hotspots
            selectDistributedHotspots(allTreasureLocations, adjustedHotspotLimit, minHotspotDistance);

            // Shuffle the hotspots for less predictable targeting
            Collections.shuffle(treasureHotspots, random);

            System.out.println("Selected " + treasureHotspots.size() +
                " distributed treasure hotspots on " + currentMapName);

        } catch (SQLException e) {
            System.err.println("Error loading treasure hotspots from database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Selects hotspots that are well-distributed across the map
     * using a greedy algorithm that prioritizes coverage
     */
    private void selectDistributedHotspots(List<Vector2> allLocations, int limit, float minDistance) {
        // If we have very few locations, just use them all
        if (allLocations.size() <= limit) {
            treasureHotspots.addAll(allLocations);
            return;
        }

        // Divide the map into quadrants for better distribution
        List<List<Vector2>> quadrants = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            quadrants.add(new ArrayList<>());
        }

        // Find the center of all points
        float sumX = 0, sumY = 0;
        for (Vector2 pos : allLocations) {
            sumX += pos.x;
            sumY += pos.y;
        }
        float centerX = sumX / allLocations.size();
        float centerY = sumY / allLocations.size();

        // Assign points to quadrants
        for (Vector2 pos : allLocations) {
            int quadrantIndex = (pos.x >= centerX ? 1 : 0) + (pos.y >= centerY ? 2 : 0);
            quadrants.get(quadrantIndex).add(pos);
        }

        // Calculate hotspots per quadrant based on point density
        int[] hotspotsPerQuadrant = new int[4];
        int remainingHotspots = limit;

        for (int i = 0; i < 4; i++) {
            // Allocate proportionally with a minimum of 1 if there are any points
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

        // Select hotspots from each quadrant
        for (int q = 0; q < 4; q++) {
            List<Vector2> quadrantLocations = quadrants.get(q);
            if (quadrantLocations.isEmpty()) continue;

            // If we need all or most points in this quadrant, just take them
            if (hotspotsPerQuadrant[q] >= quadrantLocations.size() * 0.8) {
                treasureHotspots.addAll(quadrantLocations);
                continue;
            }

            // Otherwise use greedy selection
            selectGreedyHotspots(quadrantLocations, hotspotsPerQuadrant[q], minDistance);
        }
    }

    /**
     * Uses a greedy algorithm to select well-spaced hotspots
     */
    private void selectGreedyHotspots(List<Vector2> locations, int limit, float minDistance) {
        if (locations.isEmpty()) return;

        // Start with the most central point in this set
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
        selectedHotspots.add(firstHotspot);

        // Greedily add the remaining hotspots
        while (selectedHotspots.size() < limit && selectedHotspots.size() < locations.size()) {
            Vector2 bestCandidate = null;
            float maxMinDistance = -1;

            // For each candidate position
            for (Vector2 candidate : locations) {
                // Skip if already selected
                if (selectedHotspots.contains(candidate)) continue;

                // Find minimum distance to any already-selected hotspot
                float minDistToSelected = Float.MAX_VALUE;
                for (Vector2 selected : selectedHotspots) {
                    float dist = candidate.dst(selected);
                    minDistToSelected = Math.min(minDistToSelected, dist);
                }

                // Keep the candidate with the largest minimum distance
                if (minDistToSelected > maxMinDistance) {
                    maxMinDistance = minDistToSelected;
                    bestCandidate = candidate;
                }
            }

            // If we found a suitable candidate, add it
            if (bestCandidate != null && maxMinDistance >= minDistance) {
                selectedHotspots.add(bestCandidate);
            } else {
                // If we can't maintain minimum distance, take best available
                if (bestCandidate != null) {
                    selectedHotspots.add(bestCandidate);
                } else {
                    // No more candidates available
                    break;
                }
            }
        }

        // Add all selected hotspots to the main list
        treasureHotspots.addAll(selectedHotspots);
    }
    /**
     * Updates the AI target based on hints, database locations, and roaming behavior
     *
     * @param delta The time since the last update
     * @param currentHint The current hint text (empty if no hint)
     * @param landmarks The list of landmarks in the current map
     * @return true if the target was updated, false otherwise
     */
    public boolean updateAITarget(float delta, String currentHint, List<Landmark> landmarks) {
        // First, handle hint following if active
        if (isFollowingHint && hintLandmarkPosition != null) {
            boolean updated = updateHintFollowing(delta);
            if (updated) {
                return true;
            }
        } else {
            // Reset hint follow state if we're not following a hint
            isFollowingHint = false;
            hintLandmarkPosition = null;
        }

        // Update roaming timer if we're in roaming mode
        if (isRoaming) {
            roamingTimer += delta;

            // Continue roaming until timer expires
            if (roamingTimer < currentRoamingDuration) {
                return false; // No target update needed, continue current behavior
            }

            // Roaming period ended
            isRoaming = false;
            roamingTimer = 0f;
            System.out.println("AI roaming period ended, checking for new targets");
        }

        // Process hint with high priority (if we haven't already)
        if (!currentHint.isEmpty() && !hasProcessedHint && random.nextFloat() < HINT_PRIORITY) {
            boolean hintProcessed = processHint(currentHint, landmarks);

            if (hintProcessed) {
                hasProcessedHint = true;
                System.out.println("AI following hint: " + currentHint);
                return true;
            }
        }

        // Reset hint processing flag if hint is empty
        if (currentHint.isEmpty()) {
            hasProcessedHint = false;
        }

        // Choose between database location or exploration
        if (random.nextFloat() < DATABASE_LOCATION_WEIGHT && !treasureHotspots.isEmpty()) {
            return setDatabaseTarget();
        } else {
            // Start roaming for variety
            startRoaming();
            return false;
        }
    }

    public void forceHintProcessing(String hint, List<Landmark> landmarks) {
        // Reset states to ensure hint is processed
        isRoaming = false;
        roamingTimer = 0f;
        hasProcessedHint = false;
        isFollowingHint = false;
        hintLandmarkPosition = null;

        // Try to process the hint
        boolean hintProcessed = processHint(hint, landmarks);

        if (hintProcessed) {
            hasProcessedHint = true;
            System.out.println("AI following hint (forced): " + hint);
        } else {
            System.out.println("Failed to process hint: " + hint);
            // If hint processing failed, fall back to a database target
            if (!treasureHotspots.isEmpty()) {
                setDatabaseTarget();
            }
        }
    }
    /**
     * Processes a hint and directs the AI toward the mentioned landmark
     * with an intelligent exploration pattern
     *
     * @param hint The hint text to process
     * @param landmarks The list of landmarks in the map
     * @return true if a landmark was found and targeted, false otherwise
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

            System.out.println("Checking landmark: " + landmarkName);

            if (hint.contains(landmarkName) || landmarkName.contains(hint)) {
                // First target the landmark itself
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
     * Sets a target based on historical treasure locations from the database
     *
     * @return true if a target was set, false otherwise
     */
// In the HistoricalAIData class, modify the setDatabaseTarget method
    private boolean setDatabaseTarget() {
        if (treasureHotspots.isEmpty()) {
            return false;
        }

        // First, check if all hotspots have been visited
        if (visitedHotspots.size() >= treasureHotspots.size()) {
            visitedHotspots.clear(); // Reset visited status
            System.out.println("All database hotspots visited, resetting visited status");
        }

        // Find the closest hotspot that hasn't been visited recently
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
                // Calculate score based on distance and randomness
                float distance = smartAI.getPosition().dst(hotspot);
                float randomFactor = random.nextFloat() * 200; // Add some randomness
                float score = distance + randomFactor;

                // Prefer targets that are in the general direction we're already moving
                if (lastDatabaseTarget != null) {
                    Vector2 currentDirection = new Vector2(hotspot).sub(smartAI.getPosition()).nor();
                    Vector2 lastDirection = new Vector2(lastDatabaseTarget).sub(smartAI.getPosition()).nor();
                    float dotProduct = currentDirection.dot(lastDirection);

                    // If the new target is in a similar direction, give it a better score
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
            smartAI.setTarget(bestTarget, true);
            lastDatabaseTarget = new Vector2(bestTarget);
            visitedHotspots.add(bestTarget);
            System.out.println("AI targeting database hotspot at: " + bestTarget.x + ", " + bestTarget.y);
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
            smartAI.setTarget(bestTarget, true);
            lastDatabaseTarget = new Vector2(bestTarget);
            System.out.println("AI targeting random database hotspot at: " + bestTarget.x + ", " + bestTarget.y);
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
        System.out.println("AI entering roaming mode for " + currentRoamingDuration + " seconds");
    }

    // Add this to HistoricalAIData
    public boolean checkForNearbyHotspots(Vector2 currentPosition) {
        if (treasureHotspots.isEmpty()) {
            return false;
        }

        // Find the closest database hotspot
        Vector2 closest = null;
        float minDist = Float.MAX_VALUE;

        for (Vector2 hotspot : treasureHotspots) {
            float dist = currentPosition.dst(hotspot);
            if (dist < minDist) {
                minDist = dist;
                closest = hotspot;
            }
        }

        // If we're within a certain distance of a hotspot, target it
        if (closest != null && minDist < 300f) {
            smartAI.setTarget(closest, true);
            lastDatabaseTarget = new Vector2(closest);
            return true;
        }

        return false;
    }

    /**
     * Notifies the manager that a treasure was collected
     *
     * @param treasurePosition The position of the collected treasure
     */
    public void notifyTreasureCollected(Vector2 treasurePosition) {
        // Check if this is a new hotspot not already in our database list
        boolean isNewHotspot = true;

        // Reset hint following when a treasure is collected
        isFollowingHint = false;
        hintLandmarkPosition = null;

        for (Vector2 hotspot : treasureHotspots) {
            if (hotspot.dst(treasurePosition) < LOCATION_HOTSPOT_RADIUS) {
                isNewHotspot = false;
                break;
            }
        }

        // If it's a new hotspot, add it to our list
        if (isNewHotspot && treasureHotspots.size() < MAX_CACHED_LOCATIONS) {
            treasureHotspots.add(new Vector2(treasurePosition));
            System.out.println("New treasure hotspot added at: " + treasurePosition.x + ", " + treasurePosition.y);
        }

        // Reset AI to exploration mode after collecting
        startRoaming();
    }
    /**
     * Updates the hint exploration behavior
     *
     * @param delta Time since last update
     * @return true if the AI's target was updated, false otherwise
     */
    private boolean updateHintFollowing(float delta) {
        if (!isFollowingHint || hintLandmarkPosition == null) {
            return false;
        }

        hintFollowTimer += delta;
        lastHintSearchTime += delta;

        // If we've been following this hint too long, give up
        if (hintFollowTimer >= hintFollowDuration) {
            isFollowingHint = false;
            hintLandmarkPosition = null;
            System.out.println("Hint follow duration expired, returning to normal targeting");
            // Check nearby hotspots as a fallback
            if (random.nextFloat() < 0.7f && !treasureHotspots.isEmpty()) {
                return setDatabaseTarget();
            } else {
                startRoaming();
                return false;
            }
        }

        // Get the AI's current position
        Vector2 currentPosition = smartAI.getPosition();
        float distToLandmark = currentPosition.dst(hintLandmarkPosition);

        // If we're at the landmark center, start expanding search pattern
        if (distToLandmark < 30f) {
            // We've reached the landmark, now explore around it
            // Update search radius over time (expand search area)
            hintSearchRadius += hintSearchExpansionRate * delta;
            if (hintSearchRadius > maxHintSearchRadius) {
                hintSearchRadius = maxHintSearchRadius;
            }

            // Every 3 seconds, pick a new target point in the search radius
            if (lastHintSearchTime > 3.0f) {
                lastHintSearchTime = 0f;

                // Choose a random direction and distance within current search radius
                float angle = random.nextFloat() * 360f * MathUtils.degreesToRadians;
                float distance = random.nextFloat() * hintSearchRadius;

                // Calculate the new target position
                Vector2 targetOffset = new Vector2(
                    MathUtils.cos(angle) * distance,
                    MathUtils.sin(angle) * distance
                );
                Vector2 newTarget = new Vector2(hintLandmarkPosition).add(targetOffset);

                // Set the new target
                smartAI.setTarget(newTarget, true);
                System.out.println("Exploring near hint landmark, radius: " + hintSearchRadius);

                // 10% chance to check for hotspots in the area too
                if (random.nextFloat() < 0.1f) {
                    boolean foundHotspot = checkForNearbyHotspots(hintLandmarkPosition);
                    if (foundHotspot) {
                        System.out.println("Found hotspot near hint landmark");
                        // Keep following hint but with new target
                        return true;
                    }
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Updates the current map and reloads database locations when map changes
     *
     * @param newMapName The new map name
     */
    public void updateMap(String newMapName) {
        if (!currentMapName.equals(newMapName)) {
            currentMapName = newMapName;
            loadTreasureHotspotsFromDatabase();
            visitedHotspots.clear();
            lastDatabaseTarget = null;
            hasProcessedHint = false;
        }
    }
}
