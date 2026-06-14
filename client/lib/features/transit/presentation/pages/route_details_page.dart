import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:latlong2/latlong.dart';
import 'package:lucide_icons/lucide_icons.dart';

import 'package:client/core/services/notification_service.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/presentation/providers/journey_planner_providers.dart';

// ─── Palette ─────────────────────────────────────────────────────────────────

const _kRouteBlue    = AppColors.primary;
const _kWalkGreen    = AppColors.primaryLight;
const _kAlightOrange = AppColors.primaryDark;

Color _tierColor(String tier) => switch (tier) {
      'TIER_1' => AppColors.primary,
      'TIER_2' => AppColors.primaryLight,
      _ => AppColors.error,
    };
String _tierLabel(String tier) => switch (tier) {
      'TIER_1' => 'Excellent',
      'TIER_2' => 'Good',
      _ => 'Long walk',
    };

// ─── Map layer provider ───────────────────────────────────────────────────────

enum MapLayer { osm, satellite }

final class _MapLayerNotifier extends Notifier<MapLayer> {
  @override
  MapLayer build() => MapLayer.osm;
  void toggle() => state = state == MapLayer.osm ? MapLayer.satellite : MapLayer.osm;
}

final _mapLayerProvider =
    NotifierProvider<_MapLayerNotifier, MapLayer>(_MapLayerNotifier.new);

// ─── Follow-vehicle provider (plate being followed, null = free camera) ───────

final class _FollowPlateNotifier extends Notifier<String?> {
  @override
  String? build() => null;
  void follow(String? plate) => state = plate;
}

final _followPlateProvider =
    NotifierProvider<_FollowPlateNotifier, String?>(_FollowPlateNotifier.new);

// ─── Notified plates (fire alert only once per session per plate) ─────────────

final class _NotifiedPlatesNotifier extends Notifier<Set<String>> {
  @override
  Set<String> build() => const {};
  void add(String plate) => state = {...state, plate};
}

final _notifiedPlatesProvider =
    NotifierProvider<_NotifiedPlatesNotifier, Set<String>>(
        _NotifiedPlatesNotifier.new);

// ─── ETA helper ──────────────────────────────────────────────────────────────

/// Haversine distance in km between two points.
double _haversineKm(LatLng a, LatLng b) {
  const r = 6371.0;
  final dLat = (b.latitude - a.latitude) * math.pi / 180;
  final dLng = (b.longitude - a.longitude) * math.pi / 180;
  final x = math.sin(dLat / 2) * math.sin(dLat / 2) +
      math.cos(a.latitude * math.pi / 180) *
          math.cos(b.latitude * math.pi / 180) *
          math.sin(dLng / 2) *
          math.sin(dLng / 2);
  return r * 2 * math.atan2(math.sqrt(x), math.sqrt(1 - x));
}

/// Returns ETA in minutes for a vehicle to reach [boardingPoint].
/// Uses speed if available, otherwise falls back to a ~20 km/h average.
int _etaMinutes(RouteVehicleSnap v, LatLng boardingPoint) {
  final distKm = _haversineKm(v.latLng, boardingPoint);
  final speedKmh = (v.speedKmh != null && v.speedKmh! > 2) ? v.speedKmh! : 20.0;
  return (distKm / speedKmh * 60).round().clamp(0, 9999);
}

// ─── Page ────────────────────────────────────────────────────────────────────

class RouteDetailsPage extends ConsumerStatefulWidget {
  const RouteDetailsPage({super.key});

  @override
  ConsumerState<RouteDetailsPage> createState() => _RouteDetailsPageState();
}

class _RouteDetailsPageState extends ConsumerState<RouteDetailsPage> {
  final _mapController = MapController();
  final _scaffoldKey = GlobalKey<ScaffoldState>();
  bool _userInteractingWithMap = false;

  @override
  void dispose() {
    _mapController.dispose();
    super.dispose();
  }

  LatLng? _originLatLng() {
    final place = ref.read(selectedOriginProvider);
    if (place != null) return LatLng(place.lat, place.lon);
    final pos = ref.read(locationProvider).value;
    if (pos != null) return LatLng(pos.latitude, pos.longitude);
    return null;
  }

  LatLng? _destLatLng() {
    final place = ref.read(selectedDestinationProvider);
    if (place != null) return LatLng(place.lat, place.lon);
    return null;
  }

  void _fitMap(RouteSuggestion s, LatLng? origin, LatLng? dest) {
    if (_userInteractingWithMap) return;
    final all = [
      ...s.routeCoordinates,
      s.boardingPoint.coordinates,
      s.destinationPoint.coordinates,
      ?origin,
      ?dest,
    ];
    if (all.isEmpty) return;
    double minLat = all.first.latitude, maxLat = all.first.latitude;
    double minLng = all.first.longitude, maxLng = all.first.longitude;
    for (final p in all) {
      minLat = math.min(minLat, p.latitude);
      maxLat = math.max(maxLat, p.latitude);
      minLng = math.min(minLng, p.longitude);
      maxLng = math.max(maxLng, p.longitude);
    }
    _mapController.fitCamera(CameraFit.bounds(
      bounds: LatLngBounds(
          LatLng(minLat, minLng), LatLng(maxLat, maxLng)),
      padding: const EdgeInsets.fromLTRB(40, 80, 40, 360),
    ));
  }

  void _maybeFollowVehicle(List<RouteVehicleSnap> vehicles) {
    final followPlate = ref.read(_followPlateProvider);
    if (followPlate == null) return;
    final target = vehicles.where((v) => v.plateNumber == followPlate).firstOrNull;
    if (target == null) return;
    final currentZoom = _mapController.camera.zoom;
    _mapController.move(target.latLng, currentZoom);
  }

  void _checkArrivalAlerts(
      List<RouteVehicleSnap> vehicles, LatLng boardingPoint, BuildContext ctx) {
    final notified = ref.read(_notifiedPlatesProvider);
    for (final v in vehicles) {
      if (notified.contains(v.plateNumber)) continue;
      final eta = _etaMinutes(v, boardingPoint);
      if (eta <= 2) {
        // Mark as notified immediately to avoid repeat
        ref.read(_notifiedPlatesProvider.notifier).add(v.plateNumber);
        // Local notification (works backgrounded)
        NotificationService.instance.showBusApproaching(v.plateNumber, eta);
        // In-app SnackBar (guaranteed foreground fallback)
        if (ctx.mounted) {
          ScaffoldMessenger.of(ctx).showSnackBar(
            SnackBar(
              behavior: SnackBarBehavior.floating,
              backgroundColor: AppColors.primary,
              duration: const Duration(seconds: 5),
              content: Row(
                children: [
                  const Icon(LucideIcons.bus, color: Colors.white, size: 18),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      '${v.plateNumber} is ~$eta min away! Head to your boarding stop.',
                      style: const TextStyle(color: Colors.white, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final suggestion = ref.watch(selectedSuggestionProvider);
    if (suggestion == null) {
      return Scaffold(
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(LucideIcons.alertCircle,
                  size: 40, color: AppColors.onSurfaceVariant),
              const SizedBox(height: 12),
              const Text('No route selected'),
              const SizedBox(height: 16),
              FilledButton(
                  onPressed: () => context.pop(),
                  child: const Text('Go Back')),
            ],
          ),
        ),
      );
    }

    final originLatLng = _originLatLng();
    final destLatLng = _destLatLng();
    final origin = ref.watch(selectedOriginProvider);
    final dest = ref.watch(selectedDestinationProvider);
    final originLabel = origin?.name ?? 'My Location';
    final destLabel = dest?.name ?? 'Destination';

    final stopsAsync = ref.watch(
        routeStopsProvider(suggestion.routeId.toString()));

    final allLiveVehicles = ref
        .watch(routeVehiclesForPassengerProvider(suggestion.routeId.toString()))
        .value ?? [];
    final continuedPlates = ref.watch(continuedTrackingPlatesProvider);
    final boardingSeq = suggestion.boardingPoint.sequence;
    final liveVehicles = allLiveVehicles.where((v) =>
        v.lastPassedStopSeq <= boardingSeq ||
        continuedPlates.contains(v.plateNumber)).toList();

    final mapLayer = ref.watch(_mapLayerProvider);
    final followPlate = ref.watch(_followPlateProvider);

    // Follow vehicle camera
    _maybeFollowVehicle(liveVehicles);

    // 2-min arrival alerts
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkArrivalAlerts(liveVehicles, suggestion.boardingPoint.coordinates, context);
      if (followPlate == null) {
        _fitMap(suggestion, originLatLng, destLatLng);
      }
    });

    return Scaffold(
      key: _scaffoldKey,
      endDrawer: _StepsDrawer(
        suggestion: suggestion,
        originLabel: originLabel,
        destLabel: destLabel,
        originLatLng: originLatLng,
        destLatLng: destLatLng,
      ),
      body: Stack(
        children: [
          // ── Full-screen map ───────────────────────────────────────────────
          _RouteMap(
            suggestion: suggestion,
            originLatLng: originLatLng,
            destLatLng: destLatLng,
            mapController: _mapController,
            stopsAsync: stopsAsync,
            liveVehicles: liveVehicles,
            mapLayer: mapLayer,
            followPlate: followPlate,
            onMapInteract: () => _userInteractingWithMap = true,
          ),

          // ── Top overlay ───────────────────────────────────────────────────
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(
                  horizontal: 12, vertical: 8),
              child: Row(
                children: [
                  _MapButton(
                    icon: LucideIcons.arrowLeft,
                    onTap: () => context.pop(),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 14, vertical: 9),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(20),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.12),
                            blurRadius: 10,
                          ),
                        ],
                      ),
                      child: Text(
                        suggestion.routeName,
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: AppColors.onSurface,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  _MapButton(
                    icon: LucideIcons.layoutList,
                    onTap: () =>
                        _scaffoldKey.currentState?.openEndDrawer(),
                  ),
                ],
              ),
            ),
          ),

          // ── Map layer + follow-vehicle controls (bottom-right above sheet) ─
          Positioned(
            right: 12,
            bottom: MediaQuery.of(context).size.height * 0.42,
            child: Column(
              children: [
                // Satellite / OSM toggle
                _MapButton(
                  icon: mapLayer == MapLayer.osm
                      ? LucideIcons.satellite
                      : LucideIcons.map,
                  onTap: () =>
                      ref.read(_mapLayerProvider.notifier).toggle(),
                ),
                const SizedBox(height: 8),
                // Follow-vehicle toggle (only shown when vehicles present)
                if (liveVehicles.isNotEmpty)
                  _MapButton(
                    icon: followPlate != null
                        ? LucideIcons.crosshair
                        : LucideIcons.crosshair,
                    active: followPlate != null,
                    onTap: () {
                      if (followPlate != null) {
                        ref.read(_followPlateProvider.notifier).follow(null);
                        _userInteractingWithMap = false;
                      } else {
                        ref.read(_followPlateProvider.notifier)
                            .follow(liveVehicles.first.plateNumber);
                        _userInteractingWithMap = false;
                      }
                    },
                  ),
              ],
            ),
          ),

          // ── Bottom detail sheet ───────────────────────────────────────────
          DraggableScrollableSheet(
            initialChildSize: 0.4,
            minChildSize: 0.22,
            maxChildSize: 0.9,
            builder: (ctx, sc) => _DetailSheet(
              suggestion: suggestion,
              originLabel: originLabel,
              destLabel: destLabel,
              scrollController: sc,
              stopsAsync: stopsAsync,
              liveVehicles: liveVehicles,
              onViewSteps: () =>
                  _scaffoldKey.currentState?.openEndDrawer(),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Full-screen map ─────────────────────────────────────────────────────────

class _RouteMap extends StatelessWidget {
  final RouteSuggestion suggestion;
  final LatLng? originLatLng;
  final LatLng? destLatLng;
  final MapController mapController;
  final AsyncValue<List<RouteStopPoint>> stopsAsync;
  final List<RouteVehicleSnap> liveVehicles;
  final MapLayer mapLayer;
  final String? followPlate;
  final VoidCallback onMapInteract;

  const _RouteMap({
    required this.suggestion,
    required this.originLatLng,
    required this.destLatLng,
    required this.mapController,
    required this.stopsAsync,
    required this.liveVehicles,
    required this.mapLayer,
    required this.followPlate,
    required this.onMapInteract,
  });

  @override
  Widget build(BuildContext context) {
    final boarding  = suggestion.boardingPoint.coordinates;
    final alighting = suggestion.destinationPoint.coordinates;

    final tileUrl = mapLayer == MapLayer.satellite
        ? 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'
        : 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

    return FlutterMap(
      mapController: mapController,
      options: MapOptions(
        initialCenter: boarding,
        initialZoom: 14,
        interactionOptions:
            const InteractionOptions(flags: InteractiveFlag.all),
        onMapEvent: (event) {
          if (event.source == MapEventSource.dragStart ||
              event.source == MapEventSource.scrollWheel ||
              event.source == MapEventSource.multiFingerGestureStart) {
            onMapInteract();
          }
        },
      ),
      children: [
        TileLayer(
          urlTemplate: tileUrl,
          userAgentPackageName: 'com.iots.client',
          maxZoom: 19,
        ),

        // Satellite label overlay (roads/names on top of imagery)
        if (mapLayer == MapLayer.satellite)
          TileLayer(
            urlTemplate:
                'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
            userAgentPackageName: 'com.iots.client',
            maxZoom: 19,
          ),

        // Walk: origin → boarding (dashed)
        if (originLatLng != null)
          PolylineLayer(polylines: [
            Polyline(
              points: [originLatLng!, boarding],
              color: _kWalkGreen.withValues(alpha: 0.7),
              strokeWidth: 2.5,
              pattern: const StrokePattern.dotted(),
            ),
          ]),

        // Route polyline
        PolylineLayer(polylines: [
          Polyline(
            points: suggestion.routeCoordinates,
            color: _kRouteBlue,
            strokeWidth: 5,
            strokeCap: StrokeCap.round,
            strokeJoin: StrokeJoin.round,
          ),
        ]),

        // Walk: alighting → destination (dashed)
        if (destLatLng != null)
          PolylineLayer(polylines: [
            Polyline(
              points: [alighting, destLatLng!],
              color: _kAlightOrange.withValues(alpha: 0.7),
              strokeWidth: 2.5,
              pattern: const StrokePattern.dotted(),
            ),
          ]),

        // Key markers
        MarkerLayer(
          markers: [
            if (originLatLng != null)
              _pinMarker(originLatLng!, LucideIcons.navigation2, _kWalkGreen, 38),
            _pinMarker(boarding, LucideIcons.logIn, _kWalkGreen, 46, outlined: true),
            _pinMarker(alighting, LucideIcons.logOut, _kAlightOrange, 46, outlined: true),
            if (destLatLng != null)
              _pinMarker(destLatLng!, LucideIcons.mapPin, AppColors.error, 38),
          ],
        ),

        // Live bus triangles — directional, no wrapper circle
        if (liveVehicles.isNotEmpty)
          MarkerLayer(
            markers: liveVehicles.map((v) {
              final isFollowed = followPlate == v.plateNumber;
              return Marker(
                point: v.latLng,
                width: 48,
                height: 48,
                child: _LiveBusMarker(
                  heading: v.headingDeg,
                  highlighted: isFollowed,
                ),
              );
            }).toList(),
          ),
      ],
    );
  }

  Marker _pinMarker(LatLng point, IconData icon, Color bg, double size,
      {bool outlined = false}) {
    return Marker(
      point: point,
      width: size,
      height: size,
      child: Container(
        decoration: BoxDecoration(
          color: outlined ? Colors.white : bg,
          shape: BoxShape.circle,
          border: outlined
              ? Border.all(color: bg, width: 2.5)
              : Border.all(color: Colors.white, width: 2),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.22),
              blurRadius: 6,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Icon(icon,
            size: size * 0.44,
            color: outlined ? bg : Colors.white),
      ),
    );
  }
}

// ─── Map icon button ──────────────────────────────────────────────────────────

class _MapButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  final bool active;
  const _MapButton({required this.icon, required this.onTap, this.active = false});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 42,
        height: 42,
        decoration: BoxDecoration(
          color: active ? AppColors.primary : Colors.white,
          shape: BoxShape.circle,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.13),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Icon(icon,
            size: 20,
            color: active ? Colors.white : AppColors.onSurface),
      ),
    );
  }
}

// ─── Detail bottom sheet ──────────────────────────────────────────────────────

class _DetailSheet extends StatelessWidget {
  final RouteSuggestion suggestion;
  final String originLabel;
  final String destLabel;
  final ScrollController scrollController;
  final AsyncValue<List<RouteStopPoint>> stopsAsync;
  final List<RouteVehicleSnap> liveVehicles;
  final VoidCallback onViewSteps;

  const _DetailSheet({
    required this.suggestion,
    required this.originLabel,
    required this.destLabel,
    required this.scrollController,
    required this.stopsAsync,
    required this.liveVehicles,
    required this.onViewSteps,
  });

  @override
  Widget build(BuildContext context) {
    final tc = _tierColor(suggestion.tier);
    final tl = _tierLabel(suggestion.tier);

    final intermediateStops = stopsAsync.value
            ?.where((s) =>
                s.sequence > suggestion.boardingPoint.sequence &&
                s.sequence < suggestion.destinationPoint.sequence)
            .toList() ??
        [];

    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        boxShadow: [
          BoxShadow(
            color: Color(0x1A000000),
            blurRadius: 24,
            offset: Offset(0, -4),
          ),
        ],
      ),
      child: ListView(
        controller: scrollController,
        padding: EdgeInsets.zero,
        children: [
          // Handle
          Center(
            child: Container(
              margin: const EdgeInsets.only(top: 12, bottom: 8),
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.outlineVariant,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),

          Padding(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Route name + tier
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Text(
                        suggestion.routeName,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: AppColors.onSurface,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: tc.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(
                            color: tc.withValues(alpha: 0.35)),
                      ),
                      child: Text(tl,
                          style: TextStyle(
                            color: tc,
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                          )),
                    ),
                  ],
                ),

                const SizedBox(height: 20),

                // Stats row
                _StatsRow(suggestion: suggestion),

                const SizedBox(height: 20),

                // Live vehicles section
                _LiveVehiclesSection(
                  vehicles: liveVehicles,
                  boardingPoint: suggestion.boardingPoint.coordinates,
                  routeId: suggestion.routeId,
                ),

                const SizedBox(height: 20),

                // Boarding card
                _BoardingCard(
                  label: 'BOARD HERE',
                  point: suggestion.boardingPoint,
                  walkKm: suggestion.walkToBoardingKm,
                  walkMin: suggestion.walkToBoardingMinutes,
                  walkFrom: originLabel,
                  accent: _kWalkGreen,
                  gpsUnavailable: suggestion.walkToBoardingKm == null,
                ),

                const SizedBox(height: 12),

                // Stops on route section
                _StopsSection(
                  stopsAsync: stopsAsync,
                  boardingSeq: suggestion.boardingPoint.sequence,
                  alightingSeq: suggestion.destinationPoint.sequence,
                  intermediateStops: intermediateStops,
                ),

                const SizedBox(height: 12),

                // Alighting card
                _BoardingCard(
                  label: 'ALIGHT HERE',
                  point: suggestion.destinationPoint,
                  walkKm: suggestion.distanceToDestinationKm,
                  walkMin: suggestion.distanceToDestinationMinutes,
                  walkFrom: suggestion.destinationPoint.pointName,
                  walkTo: destLabel,
                  accent: _kAlightOrange,
                ),

                const SizedBox(height: 20),

                // View steps button
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: onViewSteps,
                    icon: const Icon(LucideIcons.layoutList, size: 16),
                    label: const Text('View Full Journey Steps'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.primary,
                      side: const BorderSide(
                          color: AppColors.outlineVariant),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    ).animate().slideY(begin: 0.12, end: 0, duration: 380.ms, curve: Curves.easeOut);
  }
}

// ─── Stats row ────────────────────────────────────────────────────────────────

class _StatsRow extends StatelessWidget {
  final RouteSuggestion suggestion;
  const _StatsRow({required this.suggestion});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.outlineVariant),
      ),
      child: Row(
        children: [
          _Stat(
            icon: LucideIcons.footprints,
            value: '${suggestion.totalWalkingKm.toStringAsFixed(2)} km',
            label: 'Total walk',
            color: AppColors.primary,
          ),
          _Divider(),
          _Stat(
            icon: LucideIcons.timer,
            value: '${suggestion.totalWalkingMinutes} min',
            label: 'Walk time',
            color: AppColors.primary,
          ),
          _Divider(),
          _Stat(
            icon: LucideIcons.bus,
            value: suggestion.boardingPoint.isBusPark ? 'Park' : 'Stop',
            label: 'Board via',
            color: _kWalkGreen,
          ),
          _Divider(),
          _Stat(
            icon: LucideIcons.banknote,
            value: suggestion.fareAmount > 0
                ? '${suggestion.fareAmount} RWF'
                : '—',
            label: 'Fare',
            color: AppColors.primaryDark,
          ),
        ],
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  final IconData icon;
  final String value;
  final String label;
  final Color color;
  const _Stat(
      {required this.icon,
      required this.value,
      required this.label,
      required this.color});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Icon(icon, size: 16, color: color),
          const SizedBox(height: 4),
          Text(value,
              style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: color),
              maxLines: 1,
              overflow: TextOverflow.ellipsis),
          Text(label,
              style: const TextStyle(
                  fontSize: 10,
                  color: AppColors.onSurfaceVariant)),
        ],
      ),
    );
  }
}

class _Divider extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
        width: 1, height: 36, color: AppColors.outlineVariant);
  }
}

// ─── Boarding / alighting card ────────────────────────────────────────────────

class _BoardingCard extends StatelessWidget {
  final String label;
  final RoutePoint point;
  final double? walkKm;
  final int walkMin;
  final String walkFrom;
  final String? walkTo;
  final Color accent;
  final bool gpsUnavailable;

  const _BoardingCard({
    required this.label,
    required this.point,
    required this.walkKm,
    required this.walkMin,
    required this.walkFrom,
    this.walkTo,
    required this.accent,
    this.gpsUnavailable = false,
  });

  @override
  Widget build(BuildContext context) {
    final isBoarding = walkTo == null;
    final String walkDesc;
    if (isBoarding && gpsUnavailable) {
      walkDesc = 'Enable GPS to see walking distance to boarding stop';
    } else if (walkKm != null) {
      walkDesc = isBoarding
          ? 'Walk ${walkKm!.toStringAsFixed(2)} km ($walkMin min) from $walkFrom'
          : 'Walk ${walkKm!.toStringAsFixed(2)} km ($walkMin min) to $walkTo';
    } else {
      walkDesc = isBoarding ? 'Walk from $walkFrom' : 'Walk to $walkTo';
    }

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: accent.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: accent.withValues(alpha: 0.22)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: accent,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(
              point.isBusPark ? LucideIcons.building2 : LucideIcons.bus,
              size: 18,
              color: Colors.white,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label,
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      color: accent,
                      letterSpacing: 0.5,
                    )),
                const SizedBox(height: 2),
                Text(point.pointName,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    )),
                const SizedBox(height: 2),
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: accent.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        point.isBusPark ? 'Bus Park' : 'Bus Stop',
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: accent,
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                    Text('Seq. ${point.sequence}',
                        style: const TextStyle(
                            fontSize: 10,
                            color: AppColors.onSurfaceVariant)),
                  ],
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Icon(LucideIcons.footprints,
                        size: 11, color: AppColors.onSurfaceVariant),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text(walkDesc,
                          style: const TextStyle(
                            fontSize: 11,
                            color: AppColors.onSurfaceVariant,
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Stops on route section ───────────────────────────────────────────────────

class _StopsSection extends StatefulWidget {
  final AsyncValue<List<RouteStopPoint>> stopsAsync;
  final int boardingSeq;
  final int alightingSeq;
  final List<RouteStopPoint> intermediateStops;

  const _StopsSection({
    required this.stopsAsync,
    required this.boardingSeq,
    required this.alightingSeq,
    required this.intermediateStops,
  });

  @override
  State<_StopsSection> createState() => _StopsSectionState();
}

class _StopsSectionState extends State<_StopsSection> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.outlineVariant),
      ),
      child: Column(
        children: [
          InkWell(
            onTap: () => setState(() => _expanded = !_expanded),
            borderRadius: BorderRadius.circular(14),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                children: [
                  Container(
                    width: 34,
                    height: 34,
                    decoration: BoxDecoration(
                      color: _kRouteBlue.withValues(alpha: 0.1),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(LucideIcons.mapPin,
                        size: 16, color: _kRouteBlue),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Stops on this ride',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: AppColors.onSurface,
                          ),
                        ),
                        widget.stopsAsync.when(
                          loading: () => const Text('Loading…',
                              style: TextStyle(
                                  fontSize: 11,
                                  color: AppColors.onSurfaceVariant)),
                          error: (e, _) => const Text('Unavailable',
                              style: TextStyle(
                                  fontSize: 11,
                                  color: AppColors.error)),
                          data: (_) => Text(
                            '${widget.intermediateStops.length} intermediate stop${widget.intermediateStops.length == 1 ? '' : 's'}',
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  AnimatedRotation(
                    turns: _expanded ? 0.5 : 0,
                    duration: 250.ms,
                    child: const Icon(LucideIcons.chevronDown,
                        size: 18,
                        color: AppColors.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          ),

          AnimatedCrossFade(
            duration: 250.ms,
            crossFadeState: _expanded
                ? CrossFadeState.showSecond
                : CrossFadeState.showFirst,
            firstChild: const SizedBox.shrink(),
            secondChild: widget.stopsAsync.when(
              loading: () => const Padding(
                padding: EdgeInsets.all(16),
                child: Center(
                    child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: AppColors.primary)),
              ),
              error: (e, _) => const Padding(
                padding: EdgeInsets.all(16),
                child: Text('Could not load stops',
                    style: TextStyle(color: AppColors.error)),
              ),
              data: (_) {
                if (widget.intermediateStops.isEmpty) {
                  return const Padding(
                    padding: EdgeInsets.fromLTRB(16, 0, 16, 16),
                    child: Text(
                      'No intermediate stops — direct ride from boarding to alighting.',
                      style: TextStyle(
                          fontSize: 12,
                          color: AppColors.onSurfaceVariant),
                    ),
                  );
                }
                return Column(
                  children: [
                    const Divider(
                        height: 1, color: AppColors.outlineVariant),
                    ...widget.intermediateStops
                        .asMap()
                        .entries
                        .map((entry) {
                      final i = entry.key;
                      final s = entry.value;
                      final isLast = i ==
                          widget.intermediateStops.length - 1;
                      return _StopTimelineRow(
                        stop: s,
                        isLast: isLast,
                      );
                    }),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _StopTimelineRow extends StatelessWidget {
  final RouteStopPoint stop;
  final bool isLast;
  const _StopTimelineRow({required this.stop, required this.isLast});

  @override
  Widget build(BuildContext context) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 52,
            child: Column(
              children: [
                Expanded(
                  child: Center(
                    child: Container(
                        width: 2,
                        color: _kRouteBlue.withValues(alpha: 0.3)),
                  ),
                ),
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: _kRouteBlue,
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white, width: 2),
                    boxShadow: [
                      BoxShadow(
                        color: _kRouteBlue.withValues(alpha: 0.3),
                        blurRadius: 4,
                      ),
                    ],
                  ),
                ),
                if (!isLast)
                  Expanded(
                    child: Center(
                      child: Container(
                          width: 2,
                          color: _kRouteBlue.withValues(alpha: 0.3)),
                    ),
                  )
                else
                  const SizedBox(height: 12),
              ],
            ),
          ),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(
                top: 8,
                bottom: isLast ? 12 : 8,
                right: 16,
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      stop.name,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                        color: AppColors.onSurface,
                      ),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      '#${stop.sequence}',
                      style: const TextStyle(
                        fontSize: 10,
                        color: AppColors.onSurfaceVariant,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Journey steps drawer ─────────────────────────────────────────────────────

class _StepsDrawer extends StatelessWidget {
  final RouteSuggestion suggestion;
  final String originLabel;
  final String destLabel;
  final LatLng? originLatLng;
  final LatLng? destLatLng;

  const _StepsDrawer({
    required this.suggestion,
    required this.originLabel,
    required this.destLabel,
    required this.originLatLng,
    required this.destLatLng,
  });

  @override
  Widget build(BuildContext context) {
    final steps = _buildSteps();

    return Drawer(
      width: MediaQuery.of(context).size.width * 0.86,
      backgroundColor: Colors.white,
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 12, 0),
              child: Row(
                children: [
                  const Icon(LucideIcons.layoutList,
                      color: AppColors.primary, size: 20),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Journey Steps',
                            style: TextStyle(
                              fontSize: 17,
                              fontWeight: FontWeight.bold,
                              color: AppColors.onSurface,
                            )),
                        Text(suggestion.routeName,
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.onSurfaceVariant,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(LucideIcons.x, size: 20),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),

            const Divider(height: 24, color: AppColors.outlineVariant),

            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                itemCount: steps.length,
                itemBuilder: (ctx, i) => _StepRow(
                  step: steps[i],
                  isFirst: i == 0,
                  isLast: i == steps.length - 1,
                ),
              ),
            ),

            // Summary footer
            Container(
              margin: const EdgeInsets.all(16),
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.07),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                    color: AppColors.primary.withValues(alpha: 0.18)),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _FooterStat(
                    label: 'Total walk',
                    value:
                        '${suggestion.totalWalkingKm.toStringAsFixed(2)} km',
                  ),
                  Container(
                      width: 1,
                      height: 32,
                      color: AppColors.primary.withValues(alpha: 0.18)),
                  _FooterStat(
                    label: 'Walk time',
                    value: '${suggestion.totalWalkingMinutes} min',
                  ),
                  if (suggestion.fareAmount > 0) ...[
                    Container(
                        width: 1,
                        height: 32,
                        color: AppColors.primary.withValues(alpha: 0.18)),
                    _FooterStat(
                      label: 'Fare',
                      value: '${suggestion.fareAmount} RWF',
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<_StepData> _buildSteps() => [
        _StepData(
          icon: LucideIcons.navigation2,
          color: _kWalkGreen,
          title: originLabel,
          subtitle: 'Your starting point',
          tag: 'START',
        ),
        _StepData(
          icon: LucideIcons.footprints,
          color: _kWalkGreen,
          title: suggestion.walkToBoardingKm != null
              ? 'Walk ${suggestion.walkToBoardingKm!.toStringAsFixed(2)} km'
              : 'Walk to boarding stop',
          subtitle: suggestion.walkToBoardingKm != null
              ? '${suggestion.walkToBoardingMinutes} min on foot'
              : 'GPS unavailable — distance unknown',
          tag: 'WALK',
          isConnector: true,
        ),
        _StepData(
          icon: suggestion.boardingPoint.isBusPark
              ? LucideIcons.building2
              : LucideIcons.bus,
          color: _kWalkGreen,
          title: suggestion.boardingPoint.pointName,
          subtitle: suggestion.boardingPoint.isBusPark
              ? 'Board at bus park'
              : 'Board at bus stop',
          tag: suggestion.boardingPoint.isBusPark
              ? 'BUS PARK'
              : 'BUS STOP',
        ),
        _StepData(
          icon: LucideIcons.bus,
          color: _kRouteBlue,
          title: 'Take ${suggestion.routeName}',
          subtitle:
              'Ride to ${suggestion.destinationPoint.pointName}',
          tag: 'RIDE',
          isConnector: true,
        ),
        _StepData(
          icon: suggestion.destinationPoint.isBusPark
              ? LucideIcons.building2
              : LucideIcons.bus,
          color: _kAlightOrange,
          title: suggestion.destinationPoint.pointName,
          subtitle: suggestion.destinationPoint.isBusPark
              ? 'Alight at bus park'
              : 'Alight at bus stop',
          tag: suggestion.destinationPoint.isBusPark
              ? 'BUS PARK'
              : 'BUS STOP',
        ),
        _StepData(
          icon: LucideIcons.footprints,
          color: _kAlightOrange,
          title:
              'Walk ${suggestion.distanceToDestinationKm.toStringAsFixed(2)} km',
          subtitle:
              '${suggestion.distanceToDestinationMinutes} min on foot',
          tag: 'WALK',
          isConnector: true,
        ),
        _StepData(
          icon: LucideIcons.mapPin,
          color: AppColors.error,
          title: destLabel,
          subtitle: 'Your destination',
          tag: 'ARRIVE',
        ),
      ];
}

class _StepData {
  final IconData icon;
  final Color color;
  final String title;
  final String subtitle;
  final String tag;
  final bool isConnector;
  const _StepData({
    required this.icon,
    required this.color,
    required this.title,
    required this.subtitle,
    required this.tag,
    this.isConnector = false,
  });
}

class _StepRow extends StatelessWidget {
  final _StepData step;
  final bool isFirst;
  final bool isLast;
  const _StepRow(
      {required this.step, required this.isFirst, required this.isLast});

  @override
  Widget build(BuildContext context) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 32,
            child: Column(
              children: [
                if (!isFirst)
                  Expanded(
                    flex: 1,
                    child: Center(
                      child: Container(
                          width: 2,
                          color: step.color.withValues(alpha: 0.3)),
                    ),
                  )
                else
                  const SizedBox(height: 8),
                Container(
                  width: step.isConnector ? 20 : 26,
                  height: step.isConnector ? 20 : 26,
                  decoration: BoxDecoration(
                    color: step.isConnector
                        ? step.color.withValues(alpha: 0.1)
                        : step.color,
                    shape: BoxShape.circle,
                    border: step.isConnector
                        ? Border.all(
                            color: step.color.withValues(alpha: 0.4))
                        : Border.all(
                            color: Colors.white, width: 2),
                  ),
                  child: Icon(step.icon,
                      size: step.isConnector ? 10 : 13,
                      color: step.isConnector
                          ? step.color
                          : Colors.white),
                ),
                if (!isLast)
                  Expanded(
                    flex: 2,
                    child: Center(
                      child: Container(
                          width: 2,
                          color: step.color.withValues(alpha: 0.3)),
                    ),
                  )
                else
                  const SizedBox(height: 8),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Padding(
              padding: EdgeInsets.symmetric(
                  vertical: step.isConnector ? 6 : 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 7, vertical: 2),
                    decoration: BoxDecoration(
                      color: step.color.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(step.tag,
                        style: TextStyle(
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                          color: step.color,
                          letterSpacing: 0.5,
                        )),
                  ),
                  const SizedBox(height: 3),
                  Text(step.title,
                      style: TextStyle(
                        fontSize: step.isConnector ? 13 : 14,
                        fontWeight: step.isConnector
                            ? FontWeight.w500
                            : FontWeight.bold,
                        color: AppColors.onSurface,
                      )),
                  Text(step.subtitle,
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.onSurfaceVariant,
                      )),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Live pulsing bus marker ──────────────────────────────────────────────────

class _LiveBusMarker extends StatefulWidget {
  final double? heading;
  final bool highlighted;
  const _LiveBusMarker({this.heading, this.highlighted = false});

  @override
  State<_LiveBusMarker> createState() => _LiveBusMarkerState();
}

class _LiveBusMarkerState extends State<_LiveBusMarker>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _pulse;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    )..repeat();
    _pulse = Tween<double>(begin: 0.4, end: 1.0).animate(
      CurvedAnimation(parent: _ctrl, curve: Curves.easeOut),
    );
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final headingRad = (widget.heading ?? 0) * math.pi / 180;
    final color = widget.highlighted ? AppColors.error : AppColors.primary;

    return AnimatedBuilder(
      animation: _pulse,
      builder: (context, child) => Stack(
        alignment: Alignment.center,
        children: [
          // Pulsing glow ring
          Container(
            width: 54 * _pulse.value,
            height: 54 * _pulse.value,
            decoration: BoxDecoration(
              color: color.withValues(alpha: (1 - _pulse.value) * 0.35),
              shape: BoxShape.circle,
            ),
          ),
          // Directional triangle — no wrapper circle
          Transform.rotate(
            angle: headingRad,
            child: CustomPaint(
              size: const Size(38, 38),
              painter: _TrianglePainter(
                color: color,
                borderColor: Colors.white,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// Draws a solid triangle pointing UP (north = 0°); rotate for heading
class _TrianglePainter extends CustomPainter {
  final Color color;
  final Color borderColor;
  const _TrianglePainter({required this.color, required this.borderColor});

  @override
  void paint(Canvas canvas, Size size) {
    final cx = size.width / 2;
    final cy = size.height / 2;
    final r  = size.width / 2;

    final path = ui.Path()
      ..moveTo(cx,             cy - r)
      ..lineTo(cx + r * 0.82, cy + r * 0.60)
      ..lineTo(cx - r * 0.82, cy + r * 0.60)
      ..close();

    canvas.drawPath(path, Paint()..color = color..style = PaintingStyle.fill);
    canvas.drawPath(
      path,
      Paint()
        ..color = borderColor
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.5
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(_TrianglePainter old) =>
      old.color != color || old.borderColor != borderColor;
}

// ─── Live vehicles section ────────────────────────────────────────────────────

class _LiveVehiclesSection extends ConsumerWidget {
  final List<RouteVehicleSnap> vehicles;
  final LatLng boardingPoint;
  final String routeId;

  const _LiveVehiclesSection({
    required this.vehicles,
    required this.boardingPoint,
    required this.routeId,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Watch live so stop names update on every WebSocket push
    final liveAll = ref
        .watch(routeVehiclesForPassengerProvider(routeId))
        .value ?? vehicles;

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: liveAll.isEmpty
              ? AppColors.outlineVariant
              : AppColors.primary.withValues(alpha: 0.25),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 12, 14, 0),
            child: Row(
              children: [
                Container(
                  width: 34,
                  height: 34,
                  decoration: BoxDecoration(
                    color: liveAll.isEmpty
                        ? AppColors.surfaceContainerLow
                        : AppColors.primary.withValues(alpha: 0.1),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    LucideIcons.bus,
                    size: 16,
                    color: liveAll.isEmpty
                        ? AppColors.onSurfaceVariant
                        : AppColors.primary,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        liveAll.isEmpty
                            ? 'No buses approaching yet'
                            : '${liveAll.length} bus${liveAll.length == 1 ? '' : 'es'} approaching',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: liveAll.isEmpty
                              ? AppColors.onSurfaceVariant
                              : AppColors.onSurface,
                        ),
                      ),
                      const Text(
                        'Live · updates in realtime',
                        style: TextStyle(
                            fontSize: 11,
                            color: AppColors.onSurfaceVariant),
                      ),
                    ],
                  ),
                ),
                if (liveAll.isNotEmpty)
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        width: 7,
                        height: 7,
                        decoration: const BoxDecoration(
                          color: AppColors.primary,
                          shape: BoxShape.circle,
                        ),
                      )
                          .animate(onPlay: (c) => c.repeat())
                          .scaleXY(begin: 0.6, end: 1.2, duration: 900.ms,
                              curve: Curves.easeInOut)
                          .then()
                          .scaleXY(begin: 1.2, end: 0.6, duration: 900.ms,
                              curve: Curves.easeInOut),
                      const SizedBox(width: 4),
                      const Text('LIVE',
                          style: TextStyle(
                            fontSize: 9,
                            fontWeight: FontWeight.bold,
                            color: AppColors.primary,
                            letterSpacing: 0.5,
                          )),
                    ],
                  ),
              ],
            ),
          ),
          // Vehicle rows
          if (liveAll.isNotEmpty) ...[
            const SizedBox(height: 10),
            const Divider(height: 1, color: AppColors.outlineVariant),
            ...liveAll.map((v) => _LiveVehicleRow(
                  vehicle: v,
                  boardingPoint: boardingPoint,
                )),
          ] else
            const Padding(
              padding: EdgeInsets.fromLTRB(14, 8, 14, 14),
              child: Text(
                'Buses will appear here once they are on the route and heading your way.',
                style: TextStyle(
                    fontSize: 12, color: AppColors.onSurfaceVariant),
              ),
            ),
        ],
      ),
    );
  }
}

class _LiveVehicleRow extends ConsumerWidget {
  final RouteVehicleSnap vehicle;
  final LatLng boardingPoint;
  const _LiveVehicleRow({required this.vehicle, required this.boardingPoint});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final speed = vehicle.speedKmh != null
        ? '${vehicle.speedKmh!.round()} km/h'
        : null;
    final eta = _etaMinutes(vehicle, boardingPoint);
    final isFollowed = ref.watch(_followPlateProvider) == vehicle.plateNumber;

    return InkWell(
      onTap: () => _showContinueDialog(context, ref),
      child: Container(
        color: isFollowed
            ? AppColors.primary.withValues(alpha: 0.05)
            : null,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Directional triangle only — no white circle wrapper
              SizedBox(
                width: 34,
                height: 34,
                child: CustomPaint(
                  painter: _TrianglePainter(
                    color: isFollowed ? AppColors.error : AppColors.primary,
                    borderColor: Colors.white,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          vehicle.plateNumber,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: AppColors.onSurface,
                          ),
                        ),
                        if (speed != null) ...[
                          const SizedBox(width: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 5, vertical: 1),
                            decoration: BoxDecoration(
                              color: AppColors.surfaceContainerLow,
                              borderRadius: BorderRadius.circular(5),
                            ),
                            child: Text(speed,
                                style: const TextStyle(
                                  fontSize: 10,
                                  color: AppColors.onSurfaceVariant,
                                )),
                          ),
                        ],
                        const SizedBox(width: 6),
                        // ETA badge
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 5, vertical: 1),
                          decoration: BoxDecoration(
                            color: eta <= 2
                                ? AppColors.error.withValues(alpha: 0.1)
                                : AppColors.primary.withValues(alpha: 0.08),
                            borderRadius: BorderRadius.circular(5),
                          ),
                          child: Text(
                            '~$eta min',
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                              color: eta <= 2
                                  ? AppColors.error
                                  : AppColors.primary,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 3),
                    Row(
                      children: [
                        const Icon(LucideIcons.mapPin,
                            size: 11, color: AppColors.onSurfaceVariant),
                        const SizedBox(width: 3),
                        Expanded(
                          child: Text(
                            _stopText(vehicle),
                            style: const TextStyle(
                                fontSize: 11,
                                color: AppColors.onSurfaceVariant),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              // Passengers + follow indicator
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(LucideIcons.users,
                          size: 11, color: AppColors.onSurfaceVariant),
                      const SizedBox(width: 3),
                      Text(
                        '${vehicle.passengersOnBoard}',
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: AppColors.onSurface,
                        ),
                      ),
                    ],
                  ),
                  const Text('on board',
                      style: TextStyle(
                          fontSize: 9, color: AppColors.onSurfaceVariant)),
                  if (isFollowed)
                    const Padding(
                      padding: EdgeInsets.only(top: 4),
                      child: Text('tracking',
                          style: TextStyle(
                              fontSize: 9,
                              color: AppColors.error,
                              fontWeight: FontWeight.bold)),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showContinueDialog(BuildContext context, WidgetRef ref) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        title: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(LucideIcons.bus,
                  size: 18, color: AppColors.primary),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                vehicle.plateNumber,
                style: const TextStyle(
                    fontSize: 16, fontWeight: FontWeight.bold),
              ),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'This bus has passed your boarding stop.',
              style: TextStyle(fontSize: 14, color: AppColors.onSurface),
            ),
            const SizedBox(height: 6),
            Text(
              _stopText(vehicle),
              style: const TextStyle(
                  fontSize: 12, color: AppColors.onSurfaceVariant),
            ),
            const SizedBox(height: 12),
            const Text(
              'Do you want to keep tracking it?',
              style: TextStyle(fontSize: 13, color: AppColors.onSurface),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Dismiss',
                style: TextStyle(color: AppColors.onSurfaceVariant)),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.primary,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            child: const Text('Continue Tracking'),
          ),
        ],
      ),
    );

    if (result == true) {
      ref
          .read(continuedTrackingPlatesProvider.notifier)
          .add(vehicle.plateNumber);
    }
  }

  String _stopText(RouteVehicleSnap v) {
    if (v.currentStopName != null && v.nextStopName != null) {
      return 'At ${v.currentStopName} → next: ${v.nextStopName}';
    }
    if (v.currentStopName != null) return 'At ${v.currentStopName}';
    if (v.nextStopName != null) return 'Heading to ${v.nextStopName}';
    return 'In transit';
  }
}

// ─── Footer stat ─────────────────────────────────────────────────────────────

class _FooterStat extends StatelessWidget {
  final String label;
  final String value;
  const _FooterStat({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(value,
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.primary,
            )),
        Text(label,
            style: const TextStyle(
              fontSize: 11,
              color: AppColors.onSurfaceVariant,
            )),
      ],
    );
  }
}
