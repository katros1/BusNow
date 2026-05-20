import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:client/features/journey_planner/data/datasources/journey_planner_remote_datasource.dart';
import 'package:client/features/journey_planner/data/repositories/journey_planner_repository_impl.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/domain/repositories/journey_planner_repository.dart';
import 'package:client/features/journey_planner/domain/usecases/find_nearest_stop_usecase.dart';
import 'package:client/features/journey_planner/domain/usecases/plan_journey_usecase.dart';
import 'package:client/features/journey_planner/presentation/notifiers/journey_plan_notifier.dart';

// ─── HTTP clients ────────────────────────────────────────────────────────────

final dioProvider = Provider<Dio>((ref) => Dio(BaseOptions(
      baseUrl: 'http://localhost:8087/api/v1',
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
    )));

// ─── Data / Domain ───────────────────────────────────────────────────────────

final journeyPlannerDataSourceProvider = Provider<JourneyPlannerRemoteDataSource>(
  (ref) => JourneyPlannerRemoteDataSource(ref.watch(dioProvider)),
);

final journeyPlannerRepositoryProvider = Provider<JourneyPlannerRepository>(
  (ref) => JourneyPlannerRepositoryImpl(ref.watch(journeyPlannerDataSourceProvider)),
);

final planJourneyUseCaseProvider = Provider<PlanJourneyUseCase>(
  (ref) => PlanJourneyUseCase(ref.watch(journeyPlannerRepositoryProvider)),
);

final findNearestStopUseCaseProvider = Provider<FindNearestStopUseCase>(
  (ref) => FindNearestStopUseCase(ref.watch(journeyPlannerRepositoryProvider)),
);

// ─── GPS location ────────────────────────────────────────────────────────────

final locationProvider = FutureProvider<Position?>((ref) async {
  LocationPermission permission = await Geolocator.checkPermission();
  if (permission == LocationPermission.denied) {
    permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.denied) return null;
  }
  if (permission == LocationPermission.deniedForever) return null;
  return Geolocator.getCurrentPosition(
    locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
  );
});

// ─── Place autocomplete via Nominatim (OSM) ──────────────────────────────────
// Nominatim is OSM's official geocoding API. Restricted to Rwanda via
// countrycodes=rw. Requires a descriptive User-Agent per OSM policy.

final osmSearchProvider =
    FutureProvider.family<List<OsmPlace>, String>((ref, query) async {
  final q = query.trim();
  if (q.length < 2) return [];

  // Debounce: provider is disposed on each keystroke, so the HTTP call only
  // fires after the user pauses.
  await Future.delayed(const Duration(milliseconds: 400));

  final response = await Dio(BaseOptions(
    baseUrl: 'https://nominatim.openstreetmap.org',
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 10),
    headers: {
      'User-Agent': 'IOTS-Rwanda-Transit/1.0 (lambertbayiringire@gmail.com)',
      'Accept-Language': 'en',
    },
  )).get('/search', queryParameters: {
    'q': q,
    'format': 'json',
    'limit': 8,
    'countrycodes': 'rw',
    'addressdetails': 1,
  });

  final list = response.data as List<dynamic>;
  return list
      .map((e) => OsmPlace.fromJson(e as Map<String, dynamic>))
      .where((p) => p.name.isNotEmpty)
      .toList();
});

// ─── Selected route suggestion ───────────────────────────────────────────────

final class _SuggestionNotifier extends Notifier<RouteSuggestion?> {
  @override
  RouteSuggestion? build() => null;
  void select(RouteSuggestion? suggestion) => state = suggestion;
}

final selectedSuggestionProvider =
    NotifierProvider<_SuggestionNotifier, RouteSuggestion?>(_SuggestionNotifier.new);

// ─── Route stops ─────────────────────────────────────────────────────────────
// Fetches all stops for a given routeId from GET /routes/{id}/stops.
// The response is a list of {id, name, sequence, coordinates} objects.
// coordinates is a GeoJSON polygon (List<List<Double>>); we compute the centroid.

final routeStopsProvider =
    FutureProvider.family<List<RouteStopPoint>, String>((ref, routeId) async {
  final response = await ref.read(dioProvider).get('/routes/$routeId/stops');
  final list = response.data as List<dynamic>;
  final stops = list
      .map((e) => RouteStopPoint.fromJson(e as Map<String, dynamic>))
      .toList()
    ..sort((a, b) => a.sequence.compareTo(b.sequence));
  return stops;
});

// ─── Selected places (origin + destination) ──────────────────────────────────

final class _OsmPlaceNotifier extends Notifier<OsmPlace?> {
  @override
  OsmPlace? build() => null;
  void select(OsmPlace? place) => state = place;
}

final selectedOriginProvider =
    NotifierProvider<_OsmPlaceNotifier, OsmPlace?>(_OsmPlaceNotifier.new);

final selectedDestinationProvider =
    NotifierProvider<_OsmPlaceNotifier, OsmPlace?>(_OsmPlaceNotifier.new);

// ─── Journey plan ─────────────────────────────────────────────────────────────

final journeyPlanNotifierProvider =
    AsyncNotifierProvider<JourneyPlanNotifier, JourneyPlan?>(JourneyPlanNotifier.new);

// ─── Nearest stop ─────────────────────────────────────────────────────────────

final nearestStopProvider =
    FutureProvider.family<NearestStop?, (double, double)>((ref, loc) async {
  final useCase = ref.watch(findNearestStopUseCaseProvider);
  final result = await useCase.call([loc.$1, loc.$2]);
  return result.fold((_) => null, (stop) => stop);
});
