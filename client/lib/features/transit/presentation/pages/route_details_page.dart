import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:latlong2/latlong.dart';
import 'package:lucide_icons/lucide_icons.dart';

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

// ─── Page ────────────────────────────────────────────────────────────────────

class RouteDetailsPage extends ConsumerStatefulWidget {
  const RouteDetailsPage({super.key});

  @override
  ConsumerState<RouteDetailsPage> createState() => _RouteDetailsPageState();
}

class _RouteDetailsPageState extends ConsumerState<RouteDetailsPage> {
  final _mapController = MapController();
  final _scaffoldKey = GlobalKey<ScaffoldState>();

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

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _fitMap(suggestion, originLatLng, destLatLng);
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

  const _RouteMap({
    required this.suggestion,
    required this.originLatLng,
    required this.destLatLng,
    required this.mapController,
    required this.stopsAsync,
  });

  @override
  Widget build(BuildContext context) {
    final boarding = suggestion.boardingPoint.coordinates;
    final alighting = suggestion.destinationPoint.coordinates;
    final stopsInRange = stopsAsync.value?.where((s) =>
            s.sequence > suggestion.boardingPoint.sequence &&
            s.sequence < suggestion.destinationPoint.sequence) ??
        [];

    return FlutterMap(
      mapController: mapController,
      options: MapOptions(
        initialCenter: boarding,
        initialZoom: 13,
        interactionOptions:
            const InteractionOptions(flags: InteractiveFlag.all),
      ),
      children: [
        TileLayer(
          urlTemplate:
              'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
          userAgentPackageName: 'com.iots.client',
          maxZoom: 19,
        ),

        // Walk: origin → boarding
        if (originLatLng != null)
          PolylineLayer(polylines: [
            Polyline(
              points: [originLatLng!, boarding],
              color: _kWalkGreen.withValues(alpha: 0.75),
              strokeWidth: 3,
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

        // Walk: alighting → destination
        if (destLatLng != null)
          PolylineLayer(polylines: [
            Polyline(
              points: [alighting, destLatLng!],
              color: _kAlightOrange.withValues(alpha: 0.75),
              strokeWidth: 3,
              pattern: const StrokePattern.dotted(),
            ),
          ]),

        // Intermediate stop markers
        MarkerLayer(
          markers: stopsInRange.map((s) {
            return Marker(
              point: s.coordinates,
              width: 14,
              height: 14,
              child: Container(
                decoration: BoxDecoration(
                  color: _kRouteBlue,
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 2),
                ),
              ),
            );
          }).toList(),
        ),

        // Key markers
        MarkerLayer(
          markers: [
            if (originLatLng != null)
              _pinMarker(originLatLng!, LucideIcons.navigation2,
                  _kWalkGreen, 38),
            _pinMarker(
                boarding,
                suggestion.boardingPoint.isBusPark
                    ? LucideIcons.building2
                    : LucideIcons.bus,
                _kWalkGreen,
                46,
                outlined: true),
            _pinMarker(
                alighting,
                suggestion.destinationPoint.isBusPark
                    ? LucideIcons.building2
                    : LucideIcons.bus,
                _kAlightOrange,
                46,
                outlined: true),
            if (destLatLng != null)
              _pinMarker(destLatLng!, LucideIcons.mapPin,
                  AppColors.error, 38),
          ],
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
  const _MapButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 42,
        height: 42,
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.13),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Icon(icon, size: 20, color: AppColors.onSurface),
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
  final VoidCallback onViewSteps;

  const _DetailSheet({
    required this.suggestion,
    required this.originLabel,
    required this.destLabel,
    required this.scrollController,
    required this.stopsAsync,
    required this.onViewSteps,
  });

  @override
  Widget build(BuildContext context) {
    final tc = _tierColor(suggestion.tier);
    final tl = _tierLabel(suggestion.tier);

    // Stops between boarding and alighting (inclusive boundaries shown separately)
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

                // Boarding card
                _BoardingCard(
                  label: 'BOARD HERE',
                  point: suggestion.boardingPoint,
                  walkKm: suggestion.walkToBoardingKm,
                  walkMin: suggestion.walkToBoardingMinutes,
                  walkFrom: originLabel,
                  accent: _kWalkGreen,
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
                  walkKm: suggestion.walkToDestinationKm,
                  walkMin: suggestion.walkToDestinationMinutes,
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

                const SizedBox(height: 12),

                // Track live button
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: () => context.push('/tracking'),
                    icon: const Icon(LucideIcons.navigation, size: 16),
                    label: const Text('Track Live Vehicle'),
                    style: FilledButton.styleFrom(
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
            value:
                '${suggestion.totalWalkingKm.toStringAsFixed(2)} km',
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
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: color)),
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
  final double walkKm;
  final int walkMin;
  final String walkFrom;
  final String? walkTo;
  final Color accent;

  const _BoardingCard({
    required this.label,
    required this.point,
    required this.walkKm,
    required this.walkMin,
    required this.walkFrom,
    this.walkTo,
    required this.accent,
  });

  @override
  Widget build(BuildContext context) {
    final isBoarding = walkTo == null;
    final walkDesc = isBoarding
        ? 'Walk ${walkKm.toStringAsFixed(2)} km ($walkMin min) from $walkFrom'
        : 'Walk ${walkKm.toStringAsFixed(2)} km ($walkMin min) to $walkTo';

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: accent.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(14),
        border:
            Border.all(color: accent.withValues(alpha: 0.22)),
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
                        size: 11,
                        color: AppColors.onSurfaceVariant),
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
          // Header (always visible)
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

          // Expanded stops list
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
          // Timeline line + dot
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
                          color:
                              _kRouteBlue.withValues(alpha: 0.3)),
                    ),
                  )
                else
                  const SizedBox(height: 12),
              ],
            ),
          ),
          // Stop info
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
          title:
              'Walk ${suggestion.walkToBoardingKm.toStringAsFixed(2)} km',
          subtitle:
              '${suggestion.walkToBoardingMinutes} min on foot',
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
              'Walk ${suggestion.walkToDestinationKm.toStringAsFixed(2)} km',
          subtitle:
              '${suggestion.walkToDestinationMinutes} min on foot',
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
