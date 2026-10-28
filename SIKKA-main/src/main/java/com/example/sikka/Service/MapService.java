package com.example.sikka.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.LinkRideMapDtoIn;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.Ride;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.RideRepository;
import org.springframework.transaction.annotation.Transactional;
import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.Ride;
import jakarta.validation.Validator;
import org.springframework.transaction.annotation.Transactional;
import com.example.sikka.Model.Place;
import com.example.sikka.Repository.PlaceRepository;
import org.springframework.transaction.annotation.Transactional;
import com.example.sikka.DtoOut.MapRouteDtoOut;
import com.example.sikka.Model.Place;
import com.example.sikka.Model.Ride;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.IOException;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class MapService {
    private final RideRepository rideRepository;
    private final AccountRepository accountRepository;
    private final Validator validator;
    private final PlaceRepository placeRepository;

    private final JsonMapper jsonMapper;

    private JsonNode mapData;

    @org.springframework.beans.factory.annotation.Value("${sikka.park.ride-seed-resource:maps/rides-seed.json}")
    private String rideSeedResource = "maps/rides-seed.json";

    @org.springframework.beans.factory.annotation.Value("${sikka.map.walking-meters-per-minute:70}")
    private double walkingMetersPerMinute;

    @org.springframework.beans.factory.annotation.Value("${sikka.map.entrance-node-id:-1}")
    private int entranceNodeId;

    @PostConstruct
    public void loadMapData(){
        ClassPathResource resource =
                new ClassPathResource("maps/route-data.json");

        try (InputStream inputStream = resource.getInputStream()){
            JsonNode loadedData = jsonMapper.readTree(inputStream);

            if (loadedData == null
                    || !"SIKKA_IMAGE_ROUTE_V1".equals(
                    loadedData.path("format").asText())
                    || !loadedData.path("nodes").isArray()
                    || !loadedData.path("edges").isArray()
                    || !loadedData.path("rides").isArray()
                    || loadedData.path("rides").isEmpty()){
                throw new IllegalStateException("Invalid map data file");
            }

            mapData = loadedData;

        } catch (Exception exception){
            throw new IllegalStateException(
                    "Could not load maps/route-data.json",
                    exception
            );
        }
    }

    public JsonNode getMapRides(){
        return mapData.get("rides").deepCopy();
    }

    @Transactional
    public void linkRideToMap(Integer accountId, Integer rideId,
                              LinkRideMapDtoIn dto){

        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        if (!"ADMIN".equals(account.getRole())){
            throw new ApiException("Only admins can link rides to the map");
        }

        Ride ride = rideRepository.findRideById(rideId);

        if (ride == null){
            throw new ApiException("Ride not found");
        }

        boolean foundInMap = false;

        for (JsonNode mapRide : mapData.get("rides")){
            if (dto.getSourceRideId().equals(
                    mapRide.path("sourceRideId").asText())){
                foundInMap = true;
                break;
            }
        }

        if (!foundInMap){
            throw new ApiException("Source ride ID does not exist in the map");
        }

        Ride linkedRide = rideRepository.findRideBySourceRideId(
                dto.getSourceRideId()
        );

        if (linkedRide != null && !linkedRide.getId().equals(rideId)){
            throw new ApiException(
                    "This map reference is already linked to another ride"
            );
        }

        ride.setSourceRideId(dto.getSourceRideId());

        rideRepository.save(ride);
    }

    @Transactional
    public int importMapRides(Integer accountId){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        if (!"ADMIN".equals(account.getRole())){
            throw new ApiException("Only admins can import rides");
        }

        JsonNode seedData;

        ClassPathResource resource = new ClassPathResource(rideSeedResource);

        try (InputStream inputStream = resource.getInputStream()){
            seedData = jsonMapper.readTree(inputStream);
        } catch (IOException exception){
            throw new IllegalStateException(
                    "Could not read ride seed resource: " + rideSeedResource,
                    exception
            );
        }

        if (seedData == null || !seedData.isArray() || seedData.isEmpty()){
            throw new ApiException("Ride seed file is empty or invalid");
        }

        int processedCount = 0;
        Set<String> importedSourceIds = new HashSet<>();

        for (JsonNode rideData : seedData){
            Ride ride = jsonMapper.treeToValue(rideData, Ride.class);

            if (ride.getSourceRideId() == null
                    || ride.getSourceRideId().isBlank()){
                throw new ApiException("Source ride ID is required in seed file");
            }

            if (!validator.validate(ride).isEmpty()){
                throw new ApiException(
                        "Invalid seed data for ride: " + ride.getName()
                );
            }

            if (!importedSourceIds.add(ride.getSourceRideId())){
                throw new ApiException("Duplicate source ride ID in seed file");
            }

            if (ride.getHeightRuleStatus() == null || ride.getWeightRuleStatus() == null
                    || ride.getAgeRuleStatus() == null || ride.getHealthRuleStatus() == null
                    || ride.getHealthRestrictions() == null
                    || ride.getAdditionalSafetyReviewRequired() == null){
                throw new ApiException("Incomplete requirements in seed: " + ride.getSourceRideId());
            }

            boolean hasVerifiedRules = !"UNKNOWN".equals(ride.getHeightRuleStatus())
                    || !"UNKNOWN".equals(ride.getWeightRuleStatus())
                    || !"UNKNOWN".equals(ride.getAgeRuleStatus())
                    || !"UNKNOWN".equals(ride.getHealthRuleStatus())
                    || ride.getAdultCompanionRequired() != null
                    || Boolean.FALSE.equals(ride.getAdditionalSafetyReviewRequired());
            if (hasVerifiedRules && (ride.getRequirementsVerifiedAt() == null
                    || ride.getSourceUrl() == null || ride.getSourceUrl().isBlank())){
                throw new ApiException("Verified requirements need a source and review date");
            }
            if ("SPECIFIED".equals(ride.getHealthRuleStatus()) == ride.getHealthRestrictions().isEmpty()){
                throw new ApiException("Health rule status does not match restrictions");
            }

            boolean foundInMap = false;

            for (JsonNode mapRide : mapData.get("rides")){
                if (ride.getSourceRideId().equals(
                        mapRide.path("sourceRideId").asText())){
                    foundInMap = true;
                    break;
                }
            }

            if (!foundInMap){
                throw new ApiException(
                        "Ride does not exist in map: " + ride.getSourceRideId()
                );
            }

            Ride existingRide = rideRepository.findRideBySourceRideId(
                    ride.getSourceRideId()
            );

            if (existingRide != null){
                // Only replace reviewed requirements. Preserve ID, map link, coordinates,
                // operating status, live waits, timing fields and all relationships.
                existingRide.setCategory(ride.getCategory());
                existingRide.setLand(ride.getLand());
                existingRide.setThrillLevel(ride.getThrillLevel());
                if (existingRide.getRideFeatures() == null){
                    existingRide.setRideFeatures(new HashSet<>());
                }
                existingRide.getRideFeatures().clear();
                existingRide.getRideFeatures().addAll(ride.getRideFeatures());
                existingRide.setHeightRuleStatus(ride.getHeightRuleStatus());
                existingRide.setMinHeightCm(ride.getMinHeightCm());
                existingRide.setMaxHeightCm(ride.getMaxHeightCm());
                existingRide.setWeightRuleStatus(ride.getWeightRuleStatus());
                existingRide.setMinWeightKg(ride.getMinWeightKg());
                existingRide.setMaxWeightKg(ride.getMaxWeightKg());
                existingRide.setAgeRuleStatus(ride.getAgeRuleStatus());
                existingRide.setMinAgeYears(ride.getMinAgeYears());
                existingRide.setMaxAgeYears(ride.getMaxAgeYears());
                existingRide.setAdultCompanionRequired(ride.getAdultCompanionRequired());
                existingRide.setCompanionRequiredBelowHeightCm(ride.getCompanionRequiredBelowHeightCm());
                existingRide.setRecommendedMinAgeYears(ride.getRecommendedMinAgeYears());
                existingRide.setRecommendedUnaccompaniedAgeYears(ride.getRecommendedUnaccompaniedAgeYears());
                existingRide.setHealthRuleStatus(ride.getHealthRuleStatus());
                existingRide.setAdditionalSafetyReviewRequired(ride.getAdditionalSafetyReviewRequired());
                existingRide.setRequirementsVerifiedAt(ride.getRequirementsVerifiedAt());
                existingRide.setSourceUrl(ride.getSourceUrl());
                existingRide.setWarnings(ride.getWarnings());
                if (existingRide.getHealthRestrictions() == null){
                    existingRide.setHealthRestrictions(new HashSet<>());
                }
                existingRide.getHealthRestrictions().clear();
                existingRide.getHealthRestrictions().addAll(ride.getHealthRestrictions());
                rideRepository.save(existingRide);
                processedCount++;
                continue;
            }

            if (rideRepository.existsByNameIgnoreCase(ride.getName())){
                throw new ApiException(
                        "A ride with this name already exists. Link it manually: "
                                + ride.getName()
                );
            }

            ride.setId(null);
            rideRepository.save(ride);

            processedCount++;
        }

        return processedCount;
    }



    @Transactional
    public int importMapPlaces(Integer accountId){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        if (!"ADMIN".equals(account.getRole())){
            throw new ApiException("Only admins can import places");
        }

        ClassPathResource resource =
                new ClassPathResource("maps/places-seed.json");

        JsonNode placesData;

        try (InputStream inputStream = resource.getInputStream()){
            placesData = jsonMapper.readTree(inputStream);
        } catch (IOException exception){
            throw new IllegalStateException(
                    "Could not load maps/places-seed.json", exception);
        }

        if (placesData == null || !placesData.isArray() || placesData.isEmpty()){
            throw new ApiException("Invalid places seed file");
        }

        int addedCount = 0;

        for (JsonNode placeData : placesData){
            Place place = jsonMapper.treeToValue(placeData, Place.class);

            if (place.getSourcePlaceId() == null
                    || place.getSourcePlaceId().isBlank()){
                throw new ApiException("Source place ID is required");
            }

            if (!validator.validate(place).isEmpty()){
                throw new ApiException("Invalid place data: " + place.getName());
            }

            Place existingPlace = placeRepository
                    .findPlaceBySourcePlaceId(place.getSourcePlaceId());

            if (existingPlace != null){
                continue;
            }

            if (placeRepository.existsByNameIgnoreCase(place.getName())){
                throw new ApiException(
                        "A place with this name already exists. Link it first: "
                                + place.getName());
            }

            place.setId(null);
            placeRepository.save(place);
            addedCount++;
        }

        return addedCount;
    }

    public JsonNode getMapPlaces(){
        return mapData.get("places").deepCopy();
    }

    private Integer getDestinationNodeId(String type, Integer id){
        if (id == null || id <= 0){
            throw new ApiException("Destination ID must be positive");
        }

        String sourceId;
        String collectionName;
        String sourceField;

        if ("RIDE".equals(type)){
            Ride ride = rideRepository.findRideById(id);

            if (ride == null){
                throw new ApiException("Ride not found");
            }

            sourceId = ride.getSourceRideId();
            collectionName = "rides";
            sourceField = "sourceRideId";

        } else if ("PLACE".equals(type)){
            Place place = placeRepository.findPlaceById(id);

            if (place == null){
                throw new ApiException("Place not found");
            }

            sourceId = place.getSourcePlaceId();
            collectionName = "places";
            sourceField = "sourcePlaceId";

        } else {
            throw new ApiException("Destination type must be RIDE or PLACE");
        }

        if (sourceId == null || sourceId.isBlank()){
            throw new ApiException("This destination is not linked to the map");
        }

        for (JsonNode destination : mapData.path(collectionName)){
            if (sourceId.equals(destination.path(sourceField).asText())){
                if (!destination.path("nodeId").isIntegralNumber()){
                    throw new ApiException("Invalid destination map node");
                }

                return destination.get("nodeId").asInt();
            }
        }

        throw new ApiException("Destination was not found in the map data");
    }

    public MapRouteDtoOut getRoute(
            String fromType, Integer fromId,
            String toType, Integer toId){

        Integer startNodeId = getDestinationNodeId(fromType, fromId);
        Integer endNodeId = getDestinationNodeId(toType, toId);

        return calculateRoute(fromType, fromId, toType, toId, startNodeId, endNodeId);
    }

    public MapRouteDtoOut getEntranceRoute(String toType, Integer toId) {
        return calculateRoute("ENTRANCE", entranceNodeId, toType, toId, entranceNodeId, getDestinationNodeId(toType, toId));
    }

    private MapRouteDtoOut calculateRoute(String fromType, Integer fromId, String toType, Integer toId, Integer startNodeId, Integer endNodeId) {
        Map<Integer, JsonNode> nodes = new HashMap<>();
        Map<Integer, List<JsonNode>> connectedEdges = new HashMap<>();
        Map<Integer, Double> distances = new HashMap<>();

        Map<Integer, Integer> previousNodes = new HashMap<>();
        Map<Integer, JsonNode> previousEdges = new HashMap<>();

        Set<Integer> visited = new HashSet<>();

        for (JsonNode node : mapData.path("nodes")){
            Integer nodeId = node.path("id").asInt();

            nodes.put(nodeId, node);
            connectedEdges.put(nodeId, new ArrayList<>());
            distances.put(nodeId, Double.POSITIVE_INFINITY);
        }

        if (!nodes.containsKey(startNodeId)
                || !nodes.containsKey(endNodeId)){
            throw new ApiException("Destination node was not found");
        }

        for (JsonNode edge : mapData.path("edges")){
            Integer firstNodeId = edge.path("fromNode").asInt();
            Integer secondNodeId = edge.path("toNode").asInt();
            double distance = edge.path("distanceMeters").asDouble();

            if (!nodes.containsKey(firstNodeId)
                    || !nodes.containsKey(secondNodeId)
                    || !Double.isFinite(distance)
                    || distance < 0){
                throw new ApiException("Invalid map edge");
            }

            // Walkways can be used in both directions.
            connectedEdges.get(firstNodeId).add(edge);
            connectedEdges.get(secondNodeId).add(edge);
        }

        distances.put(startNodeId, 0.0);

        while (true){
            Integer currentNodeId = null;
            double shortestDistance = Double.POSITIVE_INFINITY;

            for (Integer nodeId : nodes.keySet()){
                if (!visited.contains(nodeId)
                        && distances.get(nodeId) < shortestDistance){

                    currentNodeId = nodeId;
                    shortestDistance = distances.get(nodeId);
                }
            }

            if (currentNodeId == null){
                break;
            }

            if (currentNodeId.equals(endNodeId)){
                break;
            }

            visited.add(currentNodeId);

            for (JsonNode edge : connectedEdges.get(currentNodeId)){
                Integer firstNodeId = edge.path("fromNode").asInt();
                Integer secondNodeId = edge.path("toNode").asInt();

                Integer nextNodeId;

                if (currentNodeId.equals(firstNodeId)){
                    nextNodeId = secondNodeId;
                } else {
                    nextNodeId = firstNodeId;
                }

                if (visited.contains(nextNodeId)){
                    continue;
                }

                double newDistance = distances.get(currentNodeId)
                        + edge.path("distanceMeters").asDouble();

                if (newDistance < distances.get(nextNodeId)){
                    distances.put(nextNodeId, newDistance);
                    previousNodes.put(nextNodeId, currentNodeId);
                    previousEdges.put(nextNodeId, edge);
                }
            }
        }

        if (!Double.isFinite(distances.get(endNodeId))){
            throw new ApiException("No connected route between these destinations");
        }

        // Reconstruct the route from destination back to start.
        List<Integer> routeNodeIds = new ArrayList<>();
        Integer currentNodeId = endNodeId;
        routeNodeIds.add(currentNodeId);

        while (!currentNodeId.equals(startNodeId)){
            currentNodeId = previousNodes.get(currentNodeId);

            if (currentNodeId == null){
                throw new ApiException("Could not reconstruct the route");
            }

            routeNodeIds.add(currentNodeId);
        }

        Collections.reverse(routeNodeIds);

        List<List<Double>> points = new ArrayList<>();

        for (int i = 1; i < routeNodeIds.size(); i++){
            Integer previousNodeId = routeNodeIds.get(i - 1);
            Integer nextNodeId = routeNodeIds.get(i);

            JsonNode edge = previousEdges.get(nextNodeId);

            List<List<Double>> edgePoints = new ArrayList<>();

            for (JsonNode point : edge.path("points")){
                edgePoints.add(List.of(
                        point.get(0).asDouble(),
                        point.get(1).asDouble()
                ));
            }

            // Reverse coordinates when walking against the stored edge direction.
            if (edge.path("fromNode").asInt() != previousNodeId.intValue()){
                Collections.reverse(edgePoints);
            }

            // Skip the shared endpoint between consecutive edges.
            int firstPointIndex = points.isEmpty() ? 0 : 1;

            for (int j = firstPointIndex; j < edgePoints.size(); j++){
                points.add(edgePoints.get(j));
            }
        }

        // Two destinations may share the same access point.
        if (points.isEmpty()){
            JsonNode node = nodes.get(startNodeId);

            points.add(List.of(
                    node.path("x").asDouble(),
                    node.path("y").asDouble()
            ));
        }

        double distanceMeters =
                Math.round(distances.get(endNodeId) * 100.0) / 100.0;

        return new MapRouteDtoOut(
                fromType,
                fromId,
                toType,
                toId,
                distanceMeters,
                points
        );
    }

    public JsonNode getMapData(){
        return mapData.deepCopy();
    }

    public java.util.Map<String, Object> getWalkingContext() {
        if (!Double.isFinite(walkingMetersPerMinute) || walkingMetersPerMinute <= 0) {
            throw new ApiException("A positive walking speed is required");
        }
        List<JsonNode> nodes = new ArrayList<>();
        mapData.path("nodes").forEach(nodes::add);
        Map<Integer, Integer> indices = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            indices.put(nodes.get(index).path("id").asInt(), index);
        }
        if (!indices.containsKey(entranceNodeId)) {
            throw new ApiException("Configure sikka.map.entrance-node-id using a verified entrance node before planning");
        }
        double[][] distances = new double[nodes.size()][nodes.size()];
        for (int from = 0; from < nodes.size(); from++) {
            java.util.Arrays.fill(distances[from], Double.POSITIVE_INFINITY);
            distances[from][from] = 0;
        }
        for (JsonNode edge : mapData.path("edges")) {
            Integer from = indices.get(edge.path("fromNode").asInt());
            Integer to = indices.get(edge.path("toNode").asInt());
            double meters = edge.path("distanceMeters").asDouble();
            if (from == null || to == null || !Double.isFinite(meters) || meters < 0) {
                throw new ApiException("Invalid walking network edge");
            }
            distances[from][to] = Math.min(distances[from][to], meters);
            distances[to][from] = Math.min(distances[to][from], meters);
        }
        for (int via = 0; via < nodes.size(); via++) {
            for (int from = 0; from < nodes.size(); from++) {
                for (int to = 0; to < nodes.size(); to++) {
                    distances[from][to] = Math.min(distances[from][to], distances[from][via] + distances[via][to]);
                }
            }
        }
        Map<String, Integer> destinations = new java.util.LinkedHashMap<>();
        destinations.put("ENTRANCE", entranceNodeId);
        for (Ride ride : rideRepository.findAll()) {
            if (ride.getSourceRideId() != null) {
                destinations.put("RIDE:" + ride.getId(), getDestinationNodeId("RIDE", ride.getId()));
            }
        }
        List<Map<String, Object>> places = new ArrayList<>();
        for (Place place : placeRepository.findAll()) {
            if (!PlanningAvailability.canPlanPlace(place) || place.getSourcePlaceId() == null) {
                continue;
            }
            String key = "PLACE:" + place.getId();
            destinations.put(key, getDestinationNodeId("PLACE", place.getId()));
            places.add(Map.of("id", place.getId(), "name", place.getName(), "kind", place.getKind(), "status", "OPEN", "operationalStatus", place.getStatus(), "availabilityIsEstimate", !"OPEN".equals(place.getStatus()), "nodeKey", key, "rateable", "RESTAURANT".equals(place.getKind())));
        }
        List<Map<String, Object>> routes = new ArrayList<>();
        for (Map.Entry<String, Integer> from : destinations.entrySet()) {
            for (Map.Entry<String, Integer> to : destinations.entrySet()) {
                double meters = distances[indices.get(from.getValue())][indices.get(to.getValue())];
                if (Double.isFinite(meters)) {
                    routes.add(Map.of("fromNodeKey", from.getKey(), "toNodeKey", to.getKey(), "minutes", (int) Math.ceil(meters / walkingMetersPerMinute)));
                }
            }
        }
        return Map.of("entranceNodeKey", "ENTRANCE", "revision", "IMAGE_ROUTE_V1:" + entranceNodeId + ":" + walkingMetersPerMinute, "places", places, "routes", routes, "estimated", true);
    }

}