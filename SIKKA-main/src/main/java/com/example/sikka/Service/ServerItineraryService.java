package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoOut.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.util.*;

@Service
public class ServerItineraryService {
    private static final int SEARCH_WIDTH = 192;

    private record State(String node, int minute, Set<Integer> rides, Set<Integer> meals, Map<Integer, Integer> mealPositions, List<AiPlanItemDtoOut> activities, int must, int preferred, int walk, int waitMinutes, boolean ended) {}

    public AiPlanDtoOut selectInitialPlan(JsonNode input) {
        List<Integer> members = new ArrayList<>();
        boolean supervised = false;
        for (JsonNode member : input.path("members")) {
            members.add(member.path("visitMemberId").asInt());
            supervised |= !member.path("ageKnown").asBoolean() || member.path("ageYears").asInt() < 18;
        }
        members.sort(Integer::compareTo);
        List<List<Integer>> groups = planningGroups(input, members, supervised);
        List<AiPlanItemDtoOut> activities = new ArrayList<>();
        int deadline = input.path("durationMinutes").asInt() - (input.path("includePrayer").asBoolean() ? input.path("prayerReserveMinutes").asInt(15) : 0);
        for (List<Integer> group : input.path("allowSplit").asBoolean() && !supervised ? List.of(members) : groups) {
            State result = input.path("allowSplit").asBoolean() && !supervised ? searchSplitPlan(input, members, deadline) : searchGroup(input, group, deadline, supervised);
            if (result == null) {
                AiPlanDtoOut failure = new AiPlanDtoOut();
                failure.setFeasible(false);
                failure.setActivities(List.of());
                failure.setWarnings(List.of("لم تُقبل خطة فارغة أو متعارضة؛ لم تُفترض قيم انتظار أو مشي مفقودة."));
                failure.setExcludedRides(explainRequiredExclusions(input, List.of()));
                String reason = "لم يجد البحث ترتيبًا يتسع للألعاب والوجبات والمشي والانتظار ضمن " + input.path("durationMinutes").asInt() + " دقيقة؛ قلّل الاختيارات أو عدّل الوجبات والمدة.";
                for (JsonNode member : input.path("members")) {
                    int memberId = member.path("visitMemberId").asInt();
                    boolean alternative = false;
                    boolean eligibleBeforeFilters = false;
                    for (JsonNode ride : input.path("rides")) {
                        eligibleBeforeFilters |= Set.of("ELIGIBLE", "UNKNOWN").contains(input.path("eligibilityByRideAndMember").path(String.valueOf(ride.path("id").asInt())).path(String.valueOf(memberId)).asString());
                        alternative |= canRide(input, ride, List.of(memberId));
                    }
                    if (!alternative) {
                        reason = "ما بقيت ألعاب مناسبة للمشارك " + member.path("name").asString(String.valueOf(memberId)) + (eligibleBeforeFilters ? " بعد تطبيق الأهلية وفلاتر الأنواع والتجنّب. راجع الاستبعادات؛ الألعاب غير المؤهل لها لن تدخل الخطة." : " بحسب نتائج الأهلية الحالية. راجع بياناته وشروط الألعاب؛ لا يمكن تجاوز تعارض أهلية مؤكد.");
                        break;
                    }
                }
                failure.setInfeasibilityReason(reason);
                return failure;
            }
            activities.addAll(result.activities());
        }
        activities.sort(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes).thenComparing(activity -> activity.getParticipantVisitMemberIds().get(0)));
        AiPlanDtoOut plan = new AiPlanDtoOut();
        plan.setFeasible(true);
        plan.setActivities(activities);
        plan.setWarnings(new ArrayList<>(List.of("تم اختيار جدول قابل للتنفيذ بالخادم بأولوية الألعاب المطلوبة ثم المفضلة وزيادة عدد الألعاب، مع مراعاة هدف الزيارة؛ البحث محدود ولا يدّعي إثبات الحل الأمثل عالميًا.")));
        plan.setExcludedRides(explainRequiredExclusions(input, activities));
        return plan;
    }

    private record SplitPath(State state, Map<Integer, String> nodes, Map<Integer, Integer> available) {}

    private State searchSplitPlan(JsonNode input, List<Integer> members, int deadline) {
        List<JsonNode> rides = new ArrayList<>();
        input.path("rides").forEach(rides::add);
        List<JsonNode> meals = new ArrayList<>();
        input.path("mealPolicy").path("breaks").forEach(meals::add);
        Map<Integer, String> entrance = new TreeMap<>();
        for (Integer member : members) entrance.put(member, input.path("walking").path("entranceNodeKey").asString());
        Comparator<State> order = stateOrder(input);
        List<SplitPath> frontier = List.of(new SplitPath(new State("", 0, Set.of(), Set.of(), Map.of(), List.of(), 0, 0, 0, 0, false), entrance, new TreeMap<>()));
        State best = null;
        for (int depth = 0; depth <= rides.size() + meals.size(); depth++) {
            Map<String, SplitPath> next = new LinkedHashMap<>();
            for (SplitPath path : frontier) {
                State state = path.state();
                if (!state.activities().isEmpty() && state.meals().size() == meals.size() && state.activities().stream().flatMap(activity -> activity.getParticipantVisitMemberIds().stream()).collect(java.util.stream.Collectors.toSet()).containsAll(members) && splitMealsMatchMemberOrder(state, meals) && (best == null || order.compare(state, best) < 0)) best = state;
                for (int index = 0; index < rides.size() + meals.size(); index++) {
                    boolean food = index >= rides.size();
                    int mealIndex = index - rides.size();
                    JsonNode resource = food ? meals.get(mealIndex) : rides.get(index);
                    int rideId = food ? 0 : resource.path("id").asInt();
                    if (food ? state.meals().contains(mealIndex) : state.ended() || state.rides().contains(rideId) || hasPendingStartMeal(state, meals)) continue;
                    String timing = food ? resource.path("timingPreference").asString() : "";
                    if (food && ("START".equals(timing) && state.activities().stream().anyMatch(activity -> "RIDE".equals(activity.getActivityKind())) || "MIDDLE".equals(timing) && state.rides().isEmpty())) continue;
                    List<Integer> eligible = food ? new ArrayList<>(ids(resource.path("participantVisitMemberIds"))) : members.stream().filter(member -> canRide(input, resource, List.of(member))).toList();
                    List<Integer> requested = food ? eligible : eligible.stream().filter(member -> !"OPTIONAL".equals(priority(input, member, rideId))).toList();
                    List<List<Integer>> participantOptions = requested.isEmpty() || requested.equals(eligible) ? List.of(eligible) : List.of(requested, eligible);
                    for (List<Integer> participants : participantOptions) {
                        if (participants.isEmpty() || food && !new TreeSet<>(participants).equals(new TreeSet<>(members))) continue;
                        Integer placeId = food && !resource.path("restaurantId").isNull() ? resource.path("restaurantId").asInt() : null;
                        String node = food ? placeId == null ? null : placeNode(input, placeId) : resource.path("nodeKey").asString();
                        if (food && placeId != null && node == null) continue;
                        int walk = 0;
                        boolean connected = true;
                        for (Integer member : participants) {
                            Integer distance = node == null ? 0 : walking(input, path.nodes().get(member), node);
                            if (distance == null) { connected = false; break; }
                            walk = Math.max(walk, distance);
                        }
                        if (!connected) continue;
                        int waiting = food ? 0 : resource.path("planningWaitMinutes").asInt();
                        int duration = food ? resource.path("durationMinutes").asInt() : resource.path("planningActivityMinutes").asInt();
                        int ready = participants.stream().mapToInt(member -> path.available().getOrDefault(member, 0)).max().orElse(0);
                        int start = food ? Math.max(ready, resource.path("earliestStartMinutes").asInt()) : ready;
                        long end = (long) start + walk + waiting + duration;
                        int reserve = 0;
                        for (int meal = 0; meal < meals.size(); meal++) if (!state.meals().contains(meal) && (!food || meal != mealIndex)) reserve += meals.get(meal).path("durationMinutes").asInt();
                        if (Math.max(state.minute(), end) + reserve > deadline || food && start > resource.path("latestStartMinutes").asInt()) continue;
                        AiPlanItemDtoOut activity = activity(food ? "BREAK" : "RIDE", food ? null : rideId, placeId, food ? resource.path("breakType").asString() : null, participants, start, (int) end, walk, waiting, duration);
                        int must = state.must();
                        int preferred = state.preferred();
                        if (!food) for (Integer member : participants) {
                            if ("MUST_RIDE".equals(priority(input, member, rideId))) must++;
                            if ("PREFERRED".equals(priority(input, member, rideId))) preferred++;
                        }
                        State candidate = append(state, activity, "", food ? null : rideId, food ? mealIndex : null, must, preferred, state.ended() || food && "END".equals(timing));
                        candidate = new State(candidate.node(), Math.max(state.minute(), candidate.minute()), candidate.rides(), candidate.meals(), candidate.mealPositions(), candidate.activities(), candidate.must(), candidate.preferred(), candidate.walk(), candidate.waitMinutes(), candidate.ended());
                        Map<Integer, Integer> available = new TreeMap<>(path.available());
                        for (Integer member : participants) available.put(member, (int) end);
                        Map<Integer, String> nodes = new TreeMap<>(path.nodes());
                        if (node != null) for (Integer member : participants) nodes.put(member, node);
                        String key = candidate.rides() + ":" + candidate.meals() + ":" + candidate.mealPositions() + ":" + nodes + ":" + available + ":" + candidate.minute() + ":" + candidate.ended();
                        SplitPath existing = next.get(key);
                        if (existing == null || order.compare(candidate, existing.state()) < 0) next.put(key, new SplitPath(candidate, nodes, available));
                    }
                }
            }
            frontier = next.values().stream().sorted((left, right) -> order.compare(left.state(), right.state())).limit(SEARCH_WIDTH).toList();
            if (frontier.isEmpty()) break;
        }
        return best;
    }

    private boolean splitMealsMatchMemberOrder(State state, List<JsonNode> meals) {
        for (int index = 0; index < meals.size(); index++) {
            JsonNode meal = meals.get(index);
            if (!"MIDDLE".equals(meal.path("timingPreference").asString())) continue;
            Integer position = state.mealPositions().get(index);
            if (position == null) return false;
            AiPlanItemDtoOut food = state.activities().get(position);
            for (Integer member : ids(meal.path("participantVisitMemberIds"))) {
                boolean before = state.activities().stream().anyMatch(activity -> "RIDE".equals(activity.getActivityKind()) && activity.getParticipantVisitMemberIds().contains(member) && activity.getRelativeEndMinutes() <= food.getRelativeStartMinutes());
                boolean after = state.activities().stream().anyMatch(activity -> "RIDE".equals(activity.getActivityKind()) && activity.getParticipantVisitMemberIds().contains(member) && activity.getRelativeStartMinutes() >= food.getRelativeEndMinutes());
                if (!before || !after) return false;
            }
        }
        return true;
    }

    private List<List<Integer>> planningGroups(JsonNode input, List<Integer> members, boolean supervised) {
        if (!input.path("allowSplit").asBoolean() || supervised) return List.of(members);
        List<Set<Integer>> groups = new ArrayList<>();
        for (Integer member : members) groups.add(new TreeSet<>(Set.of(member)));
        for (JsonNode meal : input.path("mealPolicy").path("breaks")) {
            Set<Integer> connected = ids(meal.path("participantVisitMemberIds"));
            Iterator<Set<Integer>> iterator = groups.iterator();
            while (iterator.hasNext()) {
                Set<Integer> group = iterator.next();
                if (!Collections.disjoint(group, connected)) { connected.addAll(group); iterator.remove(); }
            }
            groups.add(connected);
        }
        return groups.stream().<List<Integer>>map(ArrayList::new).toList();
    }

    public List<AiPlanItemDtoOut> selectAdditionalRides(JsonNode input, List<AiPlanItemDtoOut> baseline, List<Integer> affected, int currentMinute) {
        List<JsonNode> catalog = new ArrayList<>();
        input.path("rides").forEach(catalog::add);
        catalog.sort(Comparator.comparingInt(ride -> ride.path("id").asInt()));
        List<Integer> all = new ArrayList<>();
        boolean supervised = false;
        for (JsonNode member : input.path("members")) {
            all.add(member.path("visitMemberId").asInt());
            supervised |= !member.path("ageKnown").asBoolean() || member.path("ageYears").asInt() < 18;
        }
        all.sort(Integer::compareTo);
        List<Integer> sortedAffected = affected.stream().sorted().toList();
        if (supervised && !sortedAffected.equals(all)) return List.of();
        List<List<Integer>> groups = !input.path("allowSplit").asBoolean() || supervised ? List.of(all) : sortedAffected.stream().map(List::of).toList();
        List<AiPlanItemDtoOut> result = new ArrayList<>();
        for (List<Integer> group : groups) {
            if (!sortedAffected.containsAll(group)) continue;
            List<AiPlanItemDtoOut> commitments = baseline.stream().filter(activity -> !Collections.disjoint(group, activity.getParticipantVisitMemberIds())).sorted(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes)).toList();
            if (commitments.stream().anyMatch(activity -> !activity.getParticipantVisitMemberIds().containsAll(group))) continue;
            Set<Integer> used = new TreeSet<>();
            for (AiPlanItemDtoOut activity : baseline) if (activity.getRideId() != null) used.add(activity.getRideId());
            for (AiPlanItemDtoOut activity : result) if (activity.getRideId() != null) used.add(activity.getRideId());
            for (AiPlanItemDtoOut activity : commitments) if (activity.getRideId() != null) used.add(activity.getRideId());
            String node = input.path("walking").path("entranceNodeKey").asString();
            int available = 0;
            boolean endFoodSeen = false;
            for (int window = 0; window <= commitments.size(); window++) {
                AiPlanItemDtoOut following = window == commitments.size() ? null : commitments.get(window);
                int start = Math.max(currentMinute, available);
                int deadline = following == null ? input.path("durationMinutes").asInt() : following.getRelativeStartMinutes();
                boolean freeLocation = following != null && following.getRideId() == null && following.getPlaceId() == null;
                String nextNode = following == null || freeLocation ? null : destinationNode(input, following, node);
                boolean room = node != null && start < deadline && !endFoodSeen && (following == null || freeLocation || nextNode != null) && (!supervised || start == available);
                boolean leadingFood = following != null && isStartFood(input, following);
                if (room && !leadingFood) {
                    State initial = new State(node, start, used, Set.of(), Map.of(), List.of(), 0, 0, 0, 0, false);
                    State selected = searchGroup(input, group, deadline, supervised, catalog, List.of(), initial, nextNode, following == null ? null : following.getWalkingMinutes());
                    if (selected != null) {
                        result.addAll(selected.activities());
                        used.addAll(selected.rides());
                        node = selected.node();
                    }
                }
                if (following != null) {
                    available = supervised || following.getExecutionEndMinutes() == null ? following.getRelativeEndMinutes() : following.getExecutionEndMinutes();
                    node = destinationNode(input, following, node);
                    for (JsonNode meal : input.path("mealPolicy").path("breaks")) if ("END".equals(meal.path("timingPreference").asString()) && Objects.equals(following.getBreakType(), meal.path("breakType").asString()) && following.getRelativeStartMinutes() >= meal.path("earliestStartMinutes").asInt() && following.getRelativeStartMinutes() <= meal.path("latestStartMinutes").asInt()) endFoodSeen = true;
                }
            }
        }
        result.sort(Comparator.comparing(AiPlanItemDtoOut::getRelativeStartMinutes));
        return result;
    }

    public String destinationNode(JsonNode input, AiPlanItemDtoOut activity, String previousNode) {
        if (activity.getRideId() != null) {
            for (JsonNode ride : input.path("rides")) if (ride.path("id").asInt() == activity.getRideId()) return ride.path("nodeKey").asString();
            return null;
        }
        return activity.getPlaceId() == null ? previousNode : placeNode(input, activity.getPlaceId());
    }

    private boolean isStartFood(JsonNode input, AiPlanItemDtoOut activity) {
        for (JsonNode meal : input.path("mealPolicy").path("breaks")) if ("START".equals(meal.path("timingPreference").asString()) && Objects.equals(activity.getBreakType(), meal.path("breakType").asString())) return true;
        return false;
    }

    private State searchGroup(JsonNode input, List<Integer> group, int deadline, boolean supervised) {
        List<JsonNode> catalog = new ArrayList<>();
        input.path("rides").forEach(catalog::add);
        catalog.sort(Comparator.comparingInt(ride -> ride.path("id").asInt()));
        List<JsonNode> meals = new ArrayList<>();
        for (JsonNode meal : input.path("mealPolicy").path("breaks")) {
            Set<Integer> participants = ids(meal.path("participantVisitMemberIds"));
            if (Collections.disjoint(group, participants)) continue;
            if (!participants.equals(new HashSet<>(group))) throw new ApiException("Meal participant groups require separate supervised planning");
            meals.add(meal);
        }
        return searchGroup(input, group, deadline, supervised, catalog, meals, new State(input.path("walking").path("entranceNodeKey").asString(), 0, Set.of(), Set.of(), Map.of(), List.of(), 0, 0, 0, 0, false), null, null);
    }

    private State searchGroup(JsonNode input, List<Integer> group, int deadline, boolean supervised, List<JsonNode> catalog, List<JsonNode> meals, State initial, String nextNode, Integer nextWalk) {
        Map<String, Integer> routes = new HashMap<>();
        for (JsonNode route : input.path("walking").path("routes")) if (route.path("minutes").isIntegralNumber() && route.path("minutes").asInt() >= 0) routes.put(route.path("fromNodeKey").asString() + ">" + route.path("toNodeKey").asString(), route.path("minutes").asInt());
        catalog = catalog.stream().filter(ride -> canRide(input, ride, group)).toList();
        Comparator<State> order = stateOrder(input);
        List<State> frontier = List.of(initial);
        State best = null;
        for (int depth = 0; depth <= catalog.size() + meals.size(); depth++) {
            Map<String, State> next = new LinkedHashMap<>();
            for (State state : frontier) {
                Integer connection = nextNode == null ? null : state.node().equals(nextNode) ? Integer.valueOf(0) : routes.get(state.node() + ">" + nextNode);
                if (!state.activities().isEmpty() && (nextNode == null || Objects.equals(connection, nextWalk)) && state.meals().size() == meals.size() && middleMealsHaveFollowingRide(state, meals) && (best == null || order.compare(state, best) < 0)) best = state;
                if (!state.ended() && !hasPendingStartMeal(state, meals)) {
                    for (JsonNode ride : catalog) {
                        int rideId = ride.path("id").asInt();
                        if (state.rides().contains(rideId)) continue;
                        Integer walk = state.node().equals(ride.path("nodeKey").asString()) ? Integer.valueOf(0) : routes.get(state.node() + ">" + ride.path("nodeKey").asString());
                        if (walk == null) continue;
                        int wait = ride.path("planningWaitMinutes").asInt();
                        int duration = ride.path("planningActivityMinutes").asInt();
                        long end = (long) state.minute() + walk + wait + duration;
                        long reserved = 0;
                        for (int mealIndex = 0; mealIndex < meals.size(); mealIndex++) if (!state.meals().contains(mealIndex)) reserved += meals.get(mealIndex).path("durationMinutes").asInt();
                        if (end + reserved > deadline) continue;
                        AiPlanItemDtoOut activity = rideActivity(ride, group, state.minute(), walk);
                        int must = 0;
                        int preferred = 0;
                        for (Integer member : group) {
                            String priority = priority(input, member, rideId);
                            if ("MUST_RIDE".equals(priority)) must++;
                            if ("PREFERRED".equals(priority)) preferred++;
                        }
                        State candidate = append(state, activity, ride.path("nodeKey").asString(), rideId, null, state.must() + must, state.preferred() + preferred, false);
                        keepState(next, candidate, order);
                    }
                }
                for (int index = 0; index < meals.size(); index++) {
                    if (state.meals().contains(index)) continue;
                    JsonNode meal = meals.get(index);
                    String timing = meal.path("timingPreference").asString();
                    if ("START".equals(timing) && !state.rides().isEmpty() || "MIDDLE".equals(timing) && (state.rides().isEmpty() || state.ended())) continue;
                    String destination = state.node();
                    Integer placeId = meal.path("restaurantId").isNull() ? null : meal.path("restaurantId").asInt();
                    if (placeId != null) destination = placeNode(input, placeId);
                    if (destination == null) continue;
                    Integer walk = state.node().equals(destination) ? Integer.valueOf(0) : routes.get(state.node() + ">" + destination);
                    if (walk == null) continue;
                    int start = Math.max(state.minute(), meal.path("earliestStartMinutes").asInt());
                    if (supervised && start != state.minute() || start > meal.path("latestStartMinutes").asInt()) continue;
                    long end = (long) start + walk + meal.path("durationMinutes").asInt();
                    if (end > deadline) continue;
                    AiPlanItemDtoOut activity = activity("BREAK", null, placeId, meal.path("breakType").asString(), group, start, (int) end, walk, 0, meal.path("durationMinutes").asInt());
                    State candidate = append(state, activity, destination, null, index, state.must(), state.preferred(), state.ended() || "END".equals(timing));
                    keepState(next, candidate, order);
                }
            }
            Map<String, List<State>> byMeals = new TreeMap<>();
            for (State state : next.values()) byMeals.computeIfAbsent(state.meals().toString(), key -> new ArrayList<>()).add(state);
            List<State> expanded = new ArrayList<>();
            for (List<State> bucket : byMeals.values()) expanded.addAll(bucket.stream().sorted(order).limit(SEARCH_WIDTH).toList());
            frontier = expanded;
            if (frontier.isEmpty()) break;
        }
        return best;
    }

    private Comparator<State> stateOrder(JsonNode input) {
        Comparator<State> order = stateOrder(input.path("primaryGoal").asString());
        int seed = input.path("variationSeed").asInt();
        return seed == 0 ? order : order.thenComparingInt(state -> state.activities().stream().map(activity -> String.valueOf(activity.getRideId())).toList().hashCode() ^ seed * 0x9e3779b9);
    }

    private Comparator<State> stateOrder(String goal) {
        boolean lessWalking = Set.of("MIN_WALKING", "LESS_WALKING", "MINIMIZE_WALKING", "مشي أقل").contains(goal);
        return Comparator.comparingInt(State::must).reversed().thenComparing(Comparator.comparingInt(State::preferred).reversed()).thenComparing(Comparator.comparingInt((State state) -> state.rides().size()).reversed()).thenComparingInt(state -> lessWalking ? state.walk() : state.minute()).thenComparingInt(state -> lessWalking ? state.minute() : state.walk()).thenComparingInt(State::waitMinutes).thenComparing(state -> state.rides().toString());
    }

    private void keepState(Map<String, State> states, State candidate, Comparator<State> order) {
        String key = candidate.rides() + ":" + candidate.meals() + ":" + candidate.node() + ":" + candidate.mealPositions() + ":" + candidate.minute() + ":" + candidate.ended();
        State previous = states.get(key);
        if (previous == null || order.compare(candidate, previous) < 0) states.put(key, candidate);
    }

    private State append(State state, AiPlanItemDtoOut activity, String node, Integer rideId, Integer mealId, int must, int preferred, boolean ended) {
        Set<Integer> rides = new TreeSet<>(state.rides());
        Set<Integer> meals = new TreeSet<>(state.meals());
        if (rideId != null) rides.add(rideId);
        if (mealId != null) meals.add(mealId);
        List<AiPlanItemDtoOut> activities = new ArrayList<>(state.activities());
        Map<Integer, Integer> positions = new TreeMap<>(state.mealPositions());
        if (mealId != null) positions.put(mealId, activities.size());
        activities.add(activity);
        return new State(node, activity.getRelativeEndMinutes(), rides, meals, positions, activities, must, preferred, state.walk() + activity.getWalkingMinutes(), state.waitMinutes() + activity.getWaitingMinutes(), ended);
    }

    private boolean hasPendingStartMeal(State state, List<JsonNode> meals) {
        for (int index = 0; index < meals.size(); index++) if (!state.meals().contains(index) && "START".equals(meals.get(index).path("timingPreference").asString())) return true;
        return false;
    }

    private boolean middleMealsHaveFollowingRide(State state, List<JsonNode> meals) {
        for (int mealIndex = 0; mealIndex < meals.size(); mealIndex++) {
            if (!"MIDDLE".equals(meals.get(mealIndex).path("timingPreference").asString())) continue;
            Integer position = state.mealPositions().get(mealIndex);
            if (position == null || state.activities().subList(position + 1, state.activities().size()).stream().noneMatch(later -> "RIDE".equals(later.getActivityKind()))) return false;
        }
        return true;
    }

    public boolean canRide(JsonNode input, JsonNode ride, List<Integer> members) {
        if (ride == null || !"OPEN".equals(ride.path("status").asString()) || !ride.path("planningWaitMinutes").isIntegralNumber() || ride.path("planningWaitMinutes").asInt() < 0 || !ride.path("planningActivityMinutes").isIntegralNumber() || ride.path("planningActivityMinutes").asInt() <= 0) return false;
        for (Integer memberId : members) {
            String eligibility = input.path("eligibilityByRideAndMember").path(String.valueOf(ride.path("id").asInt())).path(String.valueOf(memberId)).asString();
            if (!Set.of("ELIGIBLE", "UNKNOWN").contains(eligibility) || "AVOID".equals(input.path("ridePreferencesByRideAndMember").path(String.valueOf(ride.path("id").asInt())).path(String.valueOf(memberId)).asString())) return false;
            for (JsonNode member : input.path("members")) {
                if (member.path("visitMemberId").asInt() != memberId) continue;
                for (JsonNode excluded : member.path("excludedRideIds")) if (excluded.asInt() == ride.path("id").asInt()) return false;
                for (JsonNode excluded : member.path("excludedRideTypes")) if (excluded.asString().equals(ride.path("category").asString())) return false;
            }
            for (JsonNode member : input.path("members")) if (member.path("visitMemberId").asInt() == memberId) for (JsonNode feature : member.path("dislikedFeatures")) for (JsonNode rideFeature : ride.path("features")) if (feature.asString().equals(rideFeature.asString())) return false;
        }
        return true;
    }

    public Integer walking(JsonNode input, String from, String to) {
        if (from == null || to == null || from.isBlank() || to.isBlank()) return null;
        if (from.equals(to)) return 0;
        for (JsonNode route : input.path("walking").path("routes")) if (from.equals(route.path("fromNodeKey").asString()) && to.equals(route.path("toNodeKey").asString()) && route.path("minutes").isIntegralNumber() && route.path("minutes").asInt() >= 0) return route.path("minutes").asInt();
        return null;
    }

    public String placeNode(JsonNode input, Integer placeId) {
        for (JsonNode place : input.path("places")) if (place.path("id").asInt() == placeId && "OPEN".equals(place.path("status").asString())) return place.path("nodeKey").asString();
        return null;
    }

    public String priority(JsonNode input, Integer memberId, Integer rideId) {
        if (!input.path("allowSplit").asBoolean() && !"SOLO".equals(input.path("tripType").asString())) {
            for (JsonNode selection : input.path("sharedSelectedRides")) if (selection.path("rideId").asInt() == rideId) return selection.path("priority").asString();
            for (JsonNode selected : input.path("sharedSelectedRideIds")) if (selected.asInt() == rideId) return "MUST_RIDE";
            return "OPTIONAL";
        }
        for (JsonNode member : input.path("members")) if (member.path("visitMemberId").asInt() == memberId) for (JsonNode selection : member.path("selectedRides")) if (selection.path("rideId").asInt() == rideId) return selection.path("priority").asString();
        return "OPTIONAL";
    }

    public List<ExcludedRideDtoOut> explainRequiredExclusions(JsonNode input, List<AiPlanItemDtoOut> activities) {
        List<ExcludedRideDtoOut> exclusions = new ArrayList<>();
        Set<Integer> requests = new TreeSet<>();
        for (JsonNode member : input.path("members")) requests.add(member.path("visitMemberId").asInt());
        Set<Integer> requestedRides = new TreeSet<>();
        input.path("sharedSelectedRideIds").forEach(id -> requestedRides.add(id.asInt()));
        for (JsonNode member : input.path("members")) member.path("selectedRideIds").forEach(id -> requestedRides.add(id.asInt()));
        for (Integer memberId : requests) for (Integer rideId : requestedRides) {
            if (!"MUST_RIDE".equals(priority(input, memberId, rideId)) || activities.stream().anyMatch(activity -> rideId.equals(activity.getRideId()) && activity.getParticipantVisitMemberIds().contains(memberId))) continue;
            JsonNode ride = null;
            for (JsonNode resource : input.path("rides")) if (resource.path("id").asInt() == rideId) ride = resource;
            String reason = "SCHEDULE_CONFLICT";
            String message = "لم تُدرج ضمن المجموعة المختارة بعد احتساب قيود الجدول والأولوية وهدف الزيارة؛ لا يعني ذلك استحالة جميع الترتيبات الأخرى.";
            if (ride == null || !"OPEN".equals(ride.path("status").asString())) { reason = "CLOSED"; message = "اللعبة غير موجودة ضمن كتالوج الألعاب المفتوحة الحالي."; }
            else if ("NOT_ELIGIBLE".equals(input.path("eligibilityByRideAndMember").path(String.valueOf(rideId)).path(String.valueOf(memberId)).asString())) { reason = "NOT_ELIGIBLE"; message = "بيانات الأهلية الحالية لا تسمح بإدراج اللعبة لهذا العضو."; }
            else if (!ride.path("planningWaitMinutes").isIntegralNumber()) { reason = "MISSING_WAIT_DATA"; message = "لا يوجد تقدير انتظار صالح من المصدر؛ لم يُفترض أن الانتظار صفر."; }
            else if (!canRide(input, ride, List.of(memberId))) { reason = "PREFERENCE_CONFLICT"; message = "تتعارض مع تفضيلات التجنب أو بيانات الأهلية المتاحة."; }
            else if (walking(input, input.path("walking").path("entranceNodeKey").asString(), ride.path("nodeKey").asString()) == null) { reason = "MISSING_ROUTE"; message = "لا يوجد مسار دخول متصل في بيانات المشي المتاحة."; }
            exclusions.add(new ExcludedRideDtoOut(rideId, ride == null ? "" : ride.path("name").asString(), memberId, "MUST_RIDE", reason, message));
        }
        return exclusions;
    }

    public AiPlanItemDtoOut rideActivity(JsonNode ride, List<Integer> members, int start, int walk) {
        int waiting = ride.path("planningWaitMinutes").asInt();
        int duration = ride.path("planningActivityMinutes").asInt();
        return activity("RIDE", ride.path("id").asInt(), null, null, members, start, Math.toIntExact((long) start + walk + waiting + duration), walk, waiting, duration);
    }

    private AiPlanItemDtoOut activity(String kind, Integer rideId, Integer placeId, String breakType, List<Integer> members, int start, int end, int walk, int wait, int duration) {
        AiPlanItemDtoOut activity = new AiPlanItemDtoOut();
        activity.setActivityKind(kind);
        activity.setRideId(rideId);
        activity.setPlaceId(placeId);
        activity.setBreakType(breakType);
        activity.setParticipantVisitMemberIds(new ArrayList<>(members));
        activity.setSubgroupKey(members.size() == 1 ? "m" + members.get(0) : "g" + Integer.toUnsignedString(members.hashCode(), 16));
        activity.setRelativeStartMinutes(start);
        activity.setRelativeEndMinutes(end);
        activity.setWalkingMinutes(walk);
        activity.setWaitingMinutes(wait);
        activity.setActivityMinutes(duration);
        return activity;
    }

    private Set<Integer> ids(JsonNode values) {
        Set<Integer> result = new TreeSet<>();
        values.forEach(value -> result.add(value.asInt()));
        return result;
    }
}
