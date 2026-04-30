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

// ─── Place autocomplete via Photon (komoot) ──────────────────────────────────
// Photon is built on OSM data, has no strict rate-limit, and is optimised for
// autocomplete. Rwanda bounding box: west=28.85, south=-2.84, east=30.90, north=-1.04

final osmSearchProvider =
    FutureProvider.family<List<OsmPlace>, String>((ref, query) async {
  final q = query.trim();
  if (q.length < 2) return [];

  // Debounce: if the user keeps typing the old provider is disposed before
  // this delay expires, so the HTTP call is never made for intermediate chars.
  await Future.delayed(const Duration(milliseconds: 450));

  final response = await Dio(BaseOptions(
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 10),
  )).get(
    'https://photon.komoot.io/api/',
    queryParameters: {
      'q': q,
      'limit': '6',
      'lang': 'en',
      'bbox': '28.85,-2.84,30.90,-1.04',
    },
  );

  final features =
      (response.data as Map<String, dynamic>)['features'] as List<dynamic>;
  return features
      .map((f) => OsmPlace.fromPhoton(f as Map<String, dynamic>))
      .where((p) => p.name.isNotEmpty)
      .toList();
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
