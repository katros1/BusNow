import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';
import 'package:client/features/journey_planner/data/datasources/journey_planner_remote_datasource.dart';
import 'package:client/features/journey_planner/data/repositories/journey_planner_repository_impl.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/domain/repositories/journey_planner_repository.dart';
import 'package:client/features/journey_planner/domain/usecases/find_nearest_stop_usecase.dart';
import 'package:client/features/journey_planner/domain/usecases/plan_journey_usecase.dart';
import 'package:client/features/journey_planner/presentation/notifiers/journey_plan_notifier.dart';
import 'package:client/utils/app_constants.dart';

// ─── HTTP clients ────────────────────────────────────────────────────────────

final dioProvider = Provider<Dio>((ref) => Dio(BaseOptions(
      baseUrl: AppConstants.apiBaseUrl,
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
      'User-Agent': 'BusNow-Transit/1.0 (lambertbayiringire@gmail.com)',
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

// ─── Realtime vehicle snapshot model (passenger-facing) ──────────────────────

class RouteVehicleSnap {
  final String plateNumber;
  final double lat;
  final double lon;
  final double? headingDeg;
  final double? speedKmh;
  final bool gpsValid;
  final bool gpsStale;
  final String? currentStopName;
  final String? nextStopName;
  final int passengersOnBoard;
  final int lastPassedStopSeq;
  final bool hasTrip;

  const RouteVehicleSnap({
    required this.plateNumber,
    required this.lat,
    required this.lon,
    this.headingDeg,
    this.speedKmh,
    this.gpsValid = false,
    this.gpsStale = false,
    this.currentStopName,
    this.nextStopName,
    this.passengersOnBoard = 0,
    this.lastPassedStopSeq = 0,
    this.hasTrip = false,
  });

  factory RouteVehicleSnap.fromJson(Map<String, dynamic> j) => RouteVehicleSnap(
        plateNumber: j['plateNumber'] as String? ?? '',
        lat: (j['latitude'] as num?)?.toDouble() ?? 0,
        lon: (j['longitude'] as num?)?.toDouble() ?? 0,
        headingDeg: (j['headingDeg'] as num?)?.toDouble(),
        speedKmh: (j['speedKmh'] as num?)?.toDouble(),
        gpsValid: j['gpsValid'] as bool? ?? false,
        gpsStale: j['gpsStale'] as bool? ?? false,
        currentStopName: j['currentStopName'] as String?,
        nextStopName: j['nextStopName'] as String?,
        passengersOnBoard: j['passengersOnBoard'] as int? ?? 0,
        lastPassedStopSeq: j['lastPassedStopSeq'] as int? ?? 0,
        hasTrip: j['tripId'] != null,
      );

  bool get hasPosition => lat != 0 || lon != 0;
  LatLng get latLng => LatLng(lat, lon);
}

// ─── Continued-tracking plates ────────────────────────────────────────────────
// Plates the passenger has chosen to keep visible after passing their boarding stop.

final class _ContinuedTrackingNotifier extends Notifier<Set<String>> {
  @override
  Set<String> build() => const {};
  void add(String plate)    => state = {...state, plate};
  void remove(String plate) => state = state.where((p) => p != plate).toSet();
}

final continuedTrackingPlatesProvider =
    NotifierProvider<_ContinuedTrackingNotifier, Set<String>>(
        _ContinuedTrackingNotifier.new);

// ─── Realtime passenger vehicles provider ─────────────────────────────────────
// Connects via WebSocket, subscribes to ALL buses on the given routeId, and
// emits every bus that has an active trip + valid non-stale GPS.
// Boarding-stop filtering and "continue tracking" logic live in the UI layer
// so they can react to user decisions without restarting the WebSocket.

final routeVehiclesForPassengerProvider =
    StreamProvider.family<List<RouteVehicleSnap>, String>((ref, routeId) {
  final dio = ref.read(dioProvider);

  final base = dio.options.baseUrl;
  final wsUrl = base
      .replaceFirst(RegExp(r'/api/v1/?$'), '')
      .replaceFirst('http://', 'ws://')
      .replaceFirst('https://', 'wss://');

  final controller = StreamController<List<RouteVehicleSnap>>();
  final snapsByPlate = <String, RouteVehicleSnap>{};
  WebSocket? ws;
  Timer? pingTimer;
  Timer? retryTimer;
  bool disposed = false;

  void emit() {
    if (controller.isClosed || disposed) return;
    final active = snapsByPlate.values
        .where((s) => s.hasTrip && s.gpsValid && !s.gpsStale && s.hasPosition)
        .toList();
    controller.add(active);
  }

  Future<void> connect() async {
    if (disposed) return;
    try {
      ws = await WebSocket.connect('$wsUrl/ws/tracking');
      if (disposed) { ws?.close(); return; }

      ws!.add(jsonEncode({'type': 'subscribeRoute', 'routeIds': [routeId]}));

      pingTimer = Timer.periodic(const Duration(seconds: 25), (_) {
        if (!disposed) ws?.add(jsonEncode({'type': 'ping'}));
      });

      ws!.listen(
        (data) {
          if (disposed) return;
          try {
            final msg = jsonDecode(data as String) as Map<String, dynamic>;
            if (msg['type'] == 'snapshot' && msg['data'] != null) {
              final snap = RouteVehicleSnap.fromJson(
                  msg['data'] as Map<String, dynamic>);
              snapsByPlate[snap.plateNumber] = snap;
              emit();
            }
          } catch (_) {}
        },
        onDone: () {
          if (!disposed) {
            retryTimer = Timer(const Duration(seconds: 3), connect);
          }
        },
        cancelOnError: true,
      );
    } catch (_) {
      if (!disposed) {
        controller.add([]);
        retryTimer = Timer(
          Duration(seconds: math.min(snapsByPlate.isEmpty ? 2 : 10, 30)),
          connect,
        );
      }
    }
  }

  connect();

  ref.onDispose(() {
    disposed = true;
    pingTimer?.cancel();
    retryTimer?.cancel();
    ws?.close();
    if (!controller.isClosed) controller.close();
  });

  return controller.stream;
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

// ─── AI Recommendations ───────────────────────────────────────────────────────

const kAiDestinations = [
  'Nyabugogo',
  'Kigali Heights',
  'Kacyiru',
  'Kisimenti',
  'Remera',
  'Chez Lando',
  'Kimironko',
];

class AiStopRecommendation {
  final String stopName;
  final double latitude;
  final double longitude;
  final double confidence;
  final bool recommended;
  final int waitTime;
  final int busFrequency;
  final int fare;
  final double distanceKm;
  final int walkingTime;

  const AiStopRecommendation({
    required this.stopName,
    required this.latitude,
    required this.longitude,
    required this.confidence,
    required this.recommended,
    required this.waitTime,
    required this.busFrequency,
    required this.fare,
    required this.distanceKm,
    required this.walkingTime,
  });

  factory AiStopRecommendation.fromJson(Map<String, dynamic> j) =>
      AiStopRecommendation(
        stopName: j['stop_name'] as String? ?? '',
        latitude: (j['latitude'] as num?)?.toDouble() ?? 0,
        longitude: (j['longitude'] as num?)?.toDouble() ?? 0,
        confidence: (j['confidence'] as num?)?.toDouble() ?? 0,
        recommended: j['recommended'] as bool? ?? false,
        waitTime: j['wait_time'] as int? ?? 0,
        busFrequency: j['bus_frequency'] as int? ?? 0,
        fare: j['fare'] as int? ?? 0,
        distanceKm: (j['distance_km'] as num?)?.toDouble() ?? 0,
        walkingTime: j['walking_time'] as int? ?? 0,
      );
}

class AiRecommendationResult {
  final bool success;
  final String destination;
  final String? timeContext;
  final List<AiStopRecommendation> recommendations;
  final AiStopRecommendation? bestStop;
  final String? error;

  const AiRecommendationResult({
    required this.success,
    required this.destination,
    this.timeContext,
    required this.recommendations,
    this.bestStop,
    this.error,
  });

  factory AiRecommendationResult.fromJson(Map<String, dynamic> j) =>
      AiRecommendationResult(
        success: j['success'] as bool? ?? false,
        destination: j['destination'] as String? ?? '',
        timeContext: j['time_context'] as String?,
        recommendations: (j['recommendations'] as List<dynamic>?)
                ?.map((e) => AiStopRecommendation.fromJson(
                    e as Map<String, dynamic>))
                .toList() ??
            [],
        bestStop: j['best_stop'] != null
            ? AiStopRecommendation.fromJson(
                j['best_stop'] as Map<String, dynamic>)
            : null,
        error: j['error'] as String?,
      );
}

class AiRecommendationNotifier
    extends AsyncNotifier<AiRecommendationResult?> {
  @override
  Future<AiRecommendationResult?> build() async => null;

  Future<void> fetch({
    required String destination,
    required double lat,
    required double lon,
  }) async {
    state = const AsyncValue.loading();
    try {
      final dio = ref.read(dioProvider);
      final response = await dio.post(
        '/recommendations',
        data: {
          'destination': destination,
          'userLatitude': lat,
          'userLongitude': lon,
        },
      );
      state = AsyncValue.data(AiRecommendationResult.fromJson(
          response.data as Map<String, dynamic>));
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }
}

final aiRecommendationNotifierProvider =
    AsyncNotifierProvider<AiRecommendationNotifier, AiRecommendationResult?>(
        AiRecommendationNotifier.new);
