import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';
import 'package:lucide_icons/lucide_icons.dart';

import 'package:client/core/theme/app_colors.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/presentation/providers/journey_planner_providers.dart';

// ─── Colour palette ──────────────────────────────────────────────────────────

const _kRouteColor = Color(0xFF005BBF);
const _kWalkColor = Color(0xFF1A8F4A);
const _kAlightColor = Color(0xFFE07B00);
const _kTier1Color = Color(0xFF1A8F4A);
const _kTier2Color = Color(0xFF795900);
const _kTier3Color = Color(0xFFBA1A1A);

// ─── Page ────────────────────────────────────────────────────────────────────

class SearchResultsPage extends ConsumerStatefulWidget {
  const SearchResultsPage({super.key});

  @override
  ConsumerState<SearchResultsPage> createState() => _SearchResultsPageState();
}

class _SearchResultsPageState extends ConsumerState<SearchResultsPage> {
  final _mapController = MapController();
  int _selectedIndex = 0;
  final _sheetKey = GlobalKey();
  final _scaffoldKey = GlobalKey<ScaffoldState>();

  @override
  void dispose() {
    _mapController.dispose();
    super.dispose();
  }

  // Resolve origin LatLng from providers
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
      if (origin != null) origin,
      if (dest != null) dest,
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

    _mapController.fitCamera(
      CameraFit.bounds(
        bounds: LatLngBounds(
          LatLng(minLat, minLng),
          LatLng(maxLat, maxLng),
        ),
        padding: const EdgeInsets.fromLTRB(40, 80, 40, 340),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final journeyAsync = ref.watch(journeyPlanNotifierProvider);
    final origin = ref.watch(selectedOriginProvider);
    final dest = ref.watch(selectedDestinationProvider);

    final originLabel = origin?.name ?? 'My Location';
    final destLabel = dest?.name ?? 'Destination';

    return Scaffold(
      key: _scaffoldKey,
      endDrawer: journeyAsync.value?.suggestions.isNotEmpty == true
          ? _StopsDrawer(
              suggestion:
                  journeyAsync.value!.suggestions[_selectedIndex],
              originLabel: originLabel,
              destLabel: destLabel,
              originLatLng: _originLatLng(),
              destLatLng: _destLatLng(),
            )
          : null,
      body: journeyAsync.when(
        loading: () => _LoadingView(),
        error: (e, _) => _ErrorView(error: e.toString()),
        data: (plan) {
          if (plan == null || plan.suggestions.isEmpty) {
            return _EmptyView(originLabel: originLabel, destLabel: destLabel);
          }

          final suggestion = plan.suggestions[_selectedIndex];
          final originLatLng = _originLatLng();
          final destLatLng = _destLatLng();

          // Fit map after first frame
          WidgetsBinding.instance.addPostFrameCallback((_) {
            _fitMap(suggestion, originLatLng, destLatLng);
          });

          return Stack(
            children: [
              // ── Map ────────────────────────────────────────────────────
              _JourneyMap(
                suggestion: suggestion,
                originLatLng: originLatLng,
                destLatLng: destLatLng,
                mapController: _mapController,
              ),

              // ── Top bar ────────────────────────────────────────────────
              SafeArea(
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 12, vertical: 8),
                  child: Row(
                    children: [
                      _MapButton(
                        icon: LucideIcons.chevronLeft,
                        onTap: () => Navigator.pop(context),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: _RouteChips(
                          suggestions: plan.suggestions,
                          selectedIndex: _selectedIndex,
                          onSelect: (i) {
                            setState(() => _selectedIndex = i);
                            _fitMap(
                              plan.suggestions[i],
                              originLatLng,
                              destLatLng,
                            );
                          },
                        ),
                      ),
                      const SizedBox(width: 8),
                      _MapButton(
                        icon: LucideIcons.listTree,
                        onTap: () =>
                            _scaffoldKey.currentState?.openEndDrawer(),
                      ),
                    ],
                  ),
                ),
              ),

              // ── Bottom detail sheet ────────────────────────────────────
              DraggableScrollableSheet(
                initialChildSize: 0.38,
                minChildSize: 0.22,
                maxChildSize: 0.88,
                builder: (ctx, scrollController) => _DetailSheet(
                  key: _sheetKey,
                  suggestion: suggestion,
                  originLabel: originLabel,
                  destLabel: destLabel,
                  scrollController: scrollController,
                  onViewStops: () =>
                      _scaffoldKey.currentState?.openEndDrawer(),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

// ─── Map widget ───────────────────────────────────────────────────────────────

class _JourneyMap extends StatelessWidget {
  final RouteSuggestion suggestion;
  final LatLng? originLatLng;
  final LatLng? destLatLng;
  final MapController mapController;

  const _JourneyMap({
    required this.suggestion,
    required this.originLatLng,
    required this.destLatLng,
    required this.mapController,
  });

  @override
  Widget build(BuildContext context) {
    final boarding = suggestion.boardingPoint.coordinates;
    final alighting = suggestion.destinationPoint.coordinates;

    return FlutterMap(
      mapController: mapController,
      options: MapOptions(
        initialCenter: boarding,
        initialZoom: 13,
        interactionOptions: const InteractionOptions(
          flags: InteractiveFlag.all,
        ),
      ),
      children: [
        // OSM tile layer
        TileLayer(
          urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
          userAgentPackageName: 'com.iots.client',
          maxZoom: 19,
        ),

        // Walking path: origin → boarding (dashed green)
        if (originLatLng != null)
          PolylineLayer(
            polylines: [
              Polyline(
                points: [originLatLng!, boarding],
                color: _kWalkColor.withValues(alpha: 0.7),
                strokeWidth: 3,
                pattern: const StrokePattern.dotted(),
              ),
            ],
          ),

        // Bus route polyline
        PolylineLayer(
          polylines: [
            Polyline(
              points: suggestion.routeCoordinates,
              color: _kRouteColor,
              strokeWidth: 5,
              strokeCap: StrokeCap.round,
              strokeJoin: StrokeJoin.round,
            ),
          ],
        ),

        // Walking path: alighting → destination (dashed orange)
        if (destLatLng != null)
          PolylineLayer(
            polylines: [
              Polyline(
                points: [alighting, destLatLng!],
                color: _kAlightColor.withValues(alpha: 0.7),
                strokeWidth: 3,
                pattern: const StrokePattern.dotted(),
              ),
            ],
          ),

        // Markers
        MarkerLayer(
          markers: [
            // Origin (user start)
            if (originLatLng != null)
              _buildMarker(
                point: originLatLng!,
                icon: LucideIcons.personStanding,
                bg: _kWalkColor,
                size: 36,
              ),

            // Boarding stop / bus park
            _buildMarker(
              point: boarding,
              icon: suggestion.boardingPoint.isBusPark
                  ? LucideIcons.building2
                  : LucideIcons.bus,
              bg: _kWalkColor,
              size: 44,
              outlined: true,
            ),

            // Alighting stop / bus park
            _buildMarker(
              point: alighting,
              icon: suggestion.destinationPoint.isBusPark
                  ? LucideIcons.building2
                  : LucideIcons.bus,
              bg: _kAlightColor,
              size: 44,
              outlined: true,
            ),

            // Destination
            if (destLatLng != null)
              _buildMarker(
                point: destLatLng!,
                icon: LucideIcons.mapPin,
                bg: AppColors.error,
                size: 36,
              ),
          ],
        ),
      ],
    );
  }

  Marker _buildMarker({
    required LatLng point,
    required IconData icon,
    required Color bg,
    required double size,
    bool outlined = false,
  }) {
    return Marker(
      point: point,
      width: size,
      height: size,
      child: Container(
        decoration: BoxDecoration(
          color: outlined ? Colors.white : bg,
          shape: BoxShape.circle,
          border: outlined
              ? Border.all(color: bg, width: 3)
              : Border.all(color: Colors.white, width: 2),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.25),
              blurRadius: 6,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Icon(icon,
            size: size * 0.45,
            color: outlined ? bg : Colors.white),
      ),
    );
  }
}

// ─── Route chip selector ─────────────────────────────────────────────────────

class _RouteChips extends StatelessWidget {
  final List<RouteSuggestion> suggestions;
  final int selectedIndex;
  final void Function(int) onSelect;

  const _RouteChips({
    required this.suggestions,
    required this.selectedIndex,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: List.generate(suggestions.length, (i) {
          final selected = i == selectedIndex;
          return GestureDetector(
            onTap: () => onSelect(i),
            child: AnimatedContainer(
              duration: 200.ms,
              margin: const EdgeInsets.only(right: 6),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              decoration: BoxDecoration(
                color: selected
                    ? AppColors.primary
                    : Colors.white.withValues(alpha: 0.92),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.15),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Text(
                'Route ${i + 1}',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: selected ? Colors.white : AppColors.onSurface,
                ),
              ),
            ),
          );
        }),
      ),
    );
  }
}

// ─── Map icon button ─────────────────────────────────────────────────────────

class _MapButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _MapButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.15),
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

// ─── Bottom detail sheet ──────────────────────────────────────────────────────

class _DetailSheet extends StatelessWidget {
  final RouteSuggestion suggestion;
  final String originLabel;
  final String destLabel;
  final ScrollController scrollController;
  final VoidCallback onViewStops;

  const _DetailSheet({
    super.key,
    required this.suggestion,
    required this.originLabel,
    required this.destLabel,
    required this.scrollController,
    required this.onViewStops,
  });

  @override
  Widget build(BuildContext context) {
    final tierColor = _tierColor(suggestion.tier);
    final tierLabel = _tierLabel(suggestion.tier);

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
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 0),
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
                        color: tierColor.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(
                            color: tierColor.withValues(alpha: 0.4)),
                      ),
                      child: Text(
                        tierLabel,
                        style: TextStyle(
                          color: tierColor,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: 16),

                // Journey timeline
                _JourneyTimeline(
                  suggestion: suggestion,
                  originLabel: originLabel,
                  destLabel: destLabel,
                ),

                const SizedBox(height: 20),

                // Stats row
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppColors.outlineVariant),
                  ),
                  child: Row(
                    children: [
                      _StatCell(
                        icon: LucideIcons.footprints,
                        value:
                            '${suggestion.totalWalkingKm.toStringAsFixed(1)} km',
                        label: 'Total walk',
                        color: AppColors.primary,
                      ),
                      _VerticalDivider(),
                      _StatCell(
                        icon: LucideIcons.timer,
                        value: '${suggestion.totalWalkingMinutes} min',
                        label: 'Walk time',
                        color: AppColors.primary,
                      ),
                      _VerticalDivider(),
                      _StatCell(
                        icon: LucideIcons.arrowUpDown,
                        value: _tierLabel(suggestion.tier),
                        label: 'Walk tier',
                        color: tierColor,
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 16),

                // Boarding & alighting cards
                _PointCard(
                  label: 'Board here',
                  point: suggestion.boardingPoint,
                  accent: _kWalkColor,
                  walkKm: suggestion.walkToBoardingKm,
                  walkMin: suggestion.walkToBoardingMinutes,
                  walkFrom: originLabel,
                ),
                const SizedBox(height: 10),
                _PointCard(
                  label: 'Alight here',
                  point: suggestion.destinationPoint,
                  accent: _kAlightColor,
                  walkKm: suggestion.walkToDestinationKm,
                  walkMin: suggestion.walkToDestinationMinutes,
                  walkFrom: suggestion.destinationPoint.pointName,
                  walkTo: destLabel,
                ),

                const SizedBox(height: 16),

                // View stops button
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: onViewStops,
                    icon: const Icon(LucideIcons.layoutList, size: 16),
                    label: const Text('View Journey Steps'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.primary,
                      side:
                          const BorderSide(color: AppColors.outlineVariant),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                  ),
                ),

                const SizedBox(height: 32),
              ],
            ),
          ),
        ],
      ),
    ).animate().slideY(begin: 0.15, end: 0, duration: 400.ms, curve: Curves.easeOut);
  }

  Color _tierColor(String tier) => switch (tier) {
        'TIER_1' => _kTier1Color,
        'TIER_2' => _kTier2Color,
        _ => _kTier3Color,
      };

  String _tierLabel(String tier) => switch (tier) {
        'TIER_1' => 'Excellent',
        'TIER_2' => 'Good',
        _ => 'Long walk',
      };
}

// ─── Journey timeline ─────────────────────────────────────────────────────────

class _JourneyTimeline extends StatelessWidget {
  final RouteSuggestion suggestion;
  final String originLabel;
  final String destLabel;

  const _JourneyTimeline({
    required this.suggestion,
    required this.originLabel,
    required this.destLabel,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        // Walk icon + km + min
        _TimelineStep(
          icon: LucideIcons.footprints,
          color: _kWalkColor,
          topText:
              '${suggestion.walkToBoardingKm.toStringAsFixed(2)} km',
          bottomText: '${suggestion.walkToBoardingMinutes} min',
        ),
        _TimelineLine(color: _kWalkColor),

        // Boarding dot
        _TimelineDot(
          icon: suggestion.boardingPoint.isBusPark
              ? LucideIcons.building2
              : LucideIcons.bus,
          color: _kWalkColor,
          label: suggestion.boardingPoint.pointName,
        ),
        _TimelineLine(color: _kRouteColor),

        // Bus icon
        _TimelineStep(
          icon: LucideIcons.bus,
          color: _kRouteColor,
          topText: 'Bus',
          bottomText: suggestion.boardingPoint.pointType == 'BUS_PARK'
              ? 'terminal'
              : 'stop',
        ),
        _TimelineLine(color: _kRouteColor),

        // Alighting dot
        _TimelineDot(
          icon: suggestion.destinationPoint.isBusPark
              ? LucideIcons.building2
              : LucideIcons.bus,
          color: _kAlightColor,
          label: suggestion.destinationPoint.pointName,
        ),
        _TimelineLine(color: _kAlightColor),

        // Walk to dest
        _TimelineStep(
          icon: LucideIcons.footprints,
          color: _kAlightColor,
          topText:
              '${suggestion.walkToDestinationKm.toStringAsFixed(2)} km',
          bottomText: '${suggestion.walkToDestinationMinutes} min',
        ),
      ],
    );
  }
}

class _TimelineStep extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String topText;
  final String bottomText;

  const _TimelineStep({
    required this.icon,
    required this.color,
    required this.topText,
    required this.bottomText,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(topText,
            style: TextStyle(
                fontSize: 10, fontWeight: FontWeight.bold, color: color)),
        const SizedBox(height: 3),
        Icon(icon, size: 20, color: color),
        const SizedBox(height: 3),
        Text(bottomText,
            style: const TextStyle(
                fontSize: 9, color: AppColors.onSurfaceVariant)),
      ],
    );
  }
}

class _TimelineDot extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String label;

  const _TimelineDot(
      {required this.icon, required this.color, required this.label});

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 28,
          height: 28,
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 2),
          ),
          child: Icon(icon, size: 14, color: Colors.white),
        ),
        const SizedBox(height: 4),
        SizedBox(
          width: 56,
          child: Text(
            label,
            style: const TextStyle(
              fontSize: 9,
              color: AppColors.onSurfaceVariant,
              fontWeight: FontWeight.w600,
            ),
            textAlign: TextAlign.center,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }
}

class _TimelineLine extends StatelessWidget {
  final Color color;
  const _TimelineLine({required this.color});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        height: 2,
        margin: const EdgeInsets.only(bottom: 14),
        color: color.withValues(alpha: 0.4),
      ),
    );
  }
}

// ─── Stats cell ───────────────────────────────────────────────────────────────

class _StatCell extends StatelessWidget {
  final IconData icon;
  final String value;
  final String label;
  final Color color;

  const _StatCell({
    required this.icon,
    required this.value,
    required this.label,
    required this.color,
  });

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
                color: color,
              )),
          Text(label,
              style: const TextStyle(
                fontSize: 10,
                color: AppColors.onSurfaceVariant,
              )),
        ],
      ),
    );
  }
}

class _VerticalDivider extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      width: 1,
      height: 40,
      color: AppColors.outlineVariant,
    );
  }
}

// ─── Point card (boarding / alighting) ───────────────────────────────────────

class _PointCard extends StatelessWidget {
  final String label;
  final RoutePoint point;
  final Color accent;
  final double walkKm;
  final int walkMin;
  final String walkFrom;
  final String? walkTo;

  const _PointCard({
    required this.label,
    required this.point,
    required this.accent,
    required this.walkKm,
    required this.walkMin,
    required this.walkFrom,
    this.walkTo,
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
        color: accent.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: accent.withValues(alpha: 0.25)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 36,
            height: 36,
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
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: accent,
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  point.pointName,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    color: AppColors.onSurface,
                  ),
                ),
                const SizedBox(height: 2),
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: accent.withValues(alpha: 0.12),
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
                    Text(
                      'Seq. ${point.sequence}',
                      style: const TextStyle(
                        fontSize: 10,
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Icon(LucideIcons.footprints,
                        size: 12, color: AppColors.onSurfaceVariant),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text(
                        walkDesc,
                        style: const TextStyle(
                          fontSize: 11,
                          color: AppColors.onSurfaceVariant,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
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

// ─── Stops drawer ────────────────────────────────────────────────────────────

class _StopsDrawer extends StatelessWidget {
  final RouteSuggestion suggestion;
  final String originLabel;
  final String destLabel;
  final LatLng? originLatLng;
  final LatLng? destLatLng;

  const _StopsDrawer({
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
      width: MediaQuery.of(context).size.width * 0.85,
      backgroundColor: Colors.white,
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 16, 0),
              child: Row(
                children: [
                  const Icon(LucideIcons.layoutList,
                      color: AppColors.primary, size: 20),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Journey Steps',
                          style: TextStyle(
                            fontSize: 17,
                            fontWeight: FontWeight.bold,
                            color: AppColors.onSurface,
                          ),
                        ),
                        Text(
                          suggestion.routeName,
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppColors.onSurfaceVariant,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
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

            // Steps list
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                itemCount: steps.length,
                itemBuilder: (ctx, i) => _DrawerStepRow(
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
                color: AppColors.primary.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                    color: AppColors.primary.withValues(alpha: 0.2)),
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
                      color: AppColors.primary.withValues(alpha: 0.2)),
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

  List<_JourneyStep> _buildSteps() {
    return [
      _JourneyStep(
        icon: LucideIcons.personStanding,
        color: _kWalkColor,
        title: originLabel,
        subtitle: 'Your starting point',
        tag: 'START',
        tagColor: _kWalkColor,
      ),
      _JourneyStep(
        icon: LucideIcons.footprints,
        color: _kWalkColor,
        title:
            'Walk ${suggestion.walkToBoardingKm.toStringAsFixed(2)} km',
        subtitle: '${suggestion.walkToBoardingMinutes} min on foot',
        tag: 'WALK',
        tagColor: _kWalkColor,
        isConnector: true,
      ),
      _JourneyStep(
        icon: suggestion.boardingPoint.isBusPark
            ? LucideIcons.building2
            : LucideIcons.bus,
        color: _kWalkColor,
        title: suggestion.boardingPoint.pointName,
        subtitle: suggestion.boardingPoint.isBusPark
            ? 'Board at bus park'
            : 'Board at bus stop',
        tag: suggestion.boardingPoint.isBusPark ? 'BUS PARK' : 'BUS STOP',
        tagColor: _kWalkColor,
      ),
      _JourneyStep(
        icon: LucideIcons.bus,
        color: _kRouteColor,
        title: 'Take ${suggestion.routeName}',
        subtitle: 'Ride towards ${suggestion.destinationPoint.pointName}',
        tag: 'RIDE',
        tagColor: _kRouteColor,
        isConnector: true,
      ),
      _JourneyStep(
        icon: suggestion.destinationPoint.isBusPark
            ? LucideIcons.building2
            : LucideIcons.bus,
        color: _kAlightColor,
        title: suggestion.destinationPoint.pointName,
        subtitle: suggestion.destinationPoint.isBusPark
            ? 'Alight at bus park'
            : 'Alight at bus stop',
        tag: suggestion.destinationPoint.isBusPark ? 'BUS PARK' : 'BUS STOP',
        tagColor: _kAlightColor,
      ),
      _JourneyStep(
        icon: LucideIcons.footprints,
        color: _kAlightColor,
        title:
            'Walk ${suggestion.walkToDestinationKm.toStringAsFixed(2)} km',
        subtitle: '${suggestion.walkToDestinationMinutes} min on foot',
        tag: 'WALK',
        tagColor: _kAlightColor,
        isConnector: true,
      ),
      _JourneyStep(
        icon: LucideIcons.mapPin,
        color: AppColors.error,
        title: destLabel,
        subtitle: 'Your destination',
        tag: 'ARRIVE',
        tagColor: AppColors.error,
      ),
    ];
  }
}

class _JourneyStep {
  final IconData icon;
  final Color color;
  final String title;
  final String subtitle;
  final String tag;
  final Color tagColor;
  final bool isConnector;

  const _JourneyStep({
    required this.icon,
    required this.color,
    required this.title,
    required this.subtitle,
    required this.tag,
    required this.tagColor,
    this.isConnector = false,
  });
}

class _DrawerStepRow extends StatelessWidget {
  final _JourneyStep step;
  final bool isFirst;
  final bool isLast;

  const _DrawerStepRow({
    required this.step,
    required this.isFirst,
    required this.isLast,
  });

  @override
  Widget build(BuildContext context) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Line + dot column
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
                        color: step.color.withValues(alpha: 0.3),
                      ),
                    ),
                  )
                else
                  const SizedBox(height: 8),
                Container(
                  width: step.isConnector ? 20 : 26,
                  height: step.isConnector ? 20 : 26,
                  decoration: BoxDecoration(
                    color: step.isConnector
                        ? step.color.withValues(alpha: 0.12)
                        : step.color,
                    shape: BoxShape.circle,
                    border: step.isConnector
                        ? Border.all(
                            color: step.color.withValues(alpha: 0.4))
                        : Border.all(color: Colors.white, width: 2),
                  ),
                  child: Icon(
                    step.icon,
                    size: step.isConnector ? 10 : 14,
                    color:
                        step.isConnector ? step.color : Colors.white,
                  ),
                ),
                if (!isLast)
                  Expanded(
                    flex: 2,
                    child: Center(
                      child: Container(
                        width: 2,
                        color: step.color.withValues(alpha: 0.3),
                      ),
                    ),
                  )
                else
                  const SizedBox(height: 8),
              ],
            ),
          ),

          const SizedBox(width: 12),

          // Content
          Expanded(
            child: Padding(
              padding: EdgeInsets.symmetric(
                  vertical: step.isConnector ? 6 : 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 7, vertical: 2),
                        decoration: BoxDecoration(
                          color: step.tagColor.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          step.tag,
                          style: TextStyle(
                            fontSize: 9,
                            fontWeight: FontWeight.bold,
                            color: step.tagColor,
                            letterSpacing: 0.5,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 3),
                  Text(
                    step.title,
                    style: TextStyle(
                      fontSize: step.isConnector ? 13 : 14,
                      fontWeight: step.isConnector
                          ? FontWeight.w500
                          : FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                  Text(
                    step.subtitle,
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppColors.onSurfaceVariant,
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
              fontSize: 16,
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

// ─── Loading / error / empty states ──────────────────────────────────────────

class _LoadingView extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: AppColors.background,
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(color: AppColors.primary),
            SizedBox(height: 20),
            Text('Finding best routes…',
                style: TextStyle(
                    color: AppColors.onSurfaceVariant, fontSize: 15)),
          ],
        ),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String error;
  const _ErrorView({required this.error});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(LucideIcons.alertCircle,
                  color: AppColors.error, size: 48),
              const SizedBox(height: 16),
              Text(error,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                      color: AppColors.onSurfaceVariant)),
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(LucideIcons.arrowLeft, size: 18),
                label: const Text('Go Back'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptyView extends StatelessWidget {
  final String originLabel;
  final String destLabel;
  const _EmptyView({required this.originLabel, required this.destLabel});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.surfaceContainerLowest,
        leading: IconButton(
          icon: const Icon(LucideIcons.chevronLeft),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text('No Routes Found'),
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                padding: const EdgeInsets.all(20),
                decoration: const BoxDecoration(
                  color: AppColors.surfaceContainerLow,
                  shape: BoxShape.circle,
                ),
                child: const Icon(LucideIcons.bus,
                    size: 40, color: AppColors.onSurfaceVariant),
              ),
              const SizedBox(height: 24),
              const Text('No routes available',
                  style: TextStyle(
                      fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text(
                '$originLabel → $destLabel',
                textAlign: TextAlign.center,
                style: const TextStyle(
                    color: AppColors.onSurfaceVariant, fontSize: 13),
              ),
              const SizedBox(height: 8),
              const Text(
                'Try a different origin or destination',
                textAlign: TextAlign.center,
                style:
                    TextStyle(color: AppColors.onSurfaceVariant),
              ),
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(LucideIcons.arrowLeft, size: 18),
                label: const Text('Search Again'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
