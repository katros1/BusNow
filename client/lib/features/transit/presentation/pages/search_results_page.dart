import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:lucide_icons/lucide_icons.dart';

import 'package:client/core/theme/app_colors.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/presentation/providers/journey_planner_providers.dart';

// ─── Colour constants ────────────────────────────────────────────────────────

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

IconData _tierIcon(String tier) => switch (tier) {
      'TIER_1' => LucideIcons.checkCircle2,
      'TIER_2' => LucideIcons.alertCircle,
      _ => LucideIcons.alertTriangle,
    };

// ─── Page ────────────────────────────────────────────────────────────────────

class SearchResultsPage extends ConsumerWidget {
  const SearchResultsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journeyAsync = ref.watch(journeyPlanNotifierProvider);
    final origin = ref.watch(selectedOriginProvider);
    final dest = ref.watch(selectedDestinationProvider);

    final originLabel = origin?.name ?? 'My Location';
    final destLabel = dest?.name ?? 'Destination';

    return Scaffold(
      backgroundColor: AppColors.background,
      body: Column(
        children: [
          // ── Header ───────────────────────────────────────────────────────
          _SearchHeader(
            originLabel: originLabel,
            destLabel: destLabel,
          ),

          // ── Body ─────────────────────────────────────────────────────────
          Expanded(
            child: journeyAsync.when(
              loading: () => const _LoadingView(),
              error: (e, _) => _ErrorView(message: e.toString()),
              data: (plan) {
                if (plan == null || plan.suggestions.isEmpty) {
                  return _EmptyView(
                      originLabel: originLabel, destLabel: destLabel);
                }
                return _RouteList(
                  suggestions: plan.suggestions,
                  originLabel: originLabel,
                  destLabel: destLabel,
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Header ──────────────────────────────────────────────────────────────────

class _SearchHeader extends StatelessWidget {
  final String originLabel;
  final String destLabel;

  const _SearchHeader(
      {required this.originLabel, required this.destLabel});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.primary,
      ),
      child: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // Back + title row
            Padding(
              padding: const EdgeInsets.fromLTRB(4, 8, 16, 0),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(LucideIcons.arrowLeft,
                        color: Colors.white),
                    onPressed: () => context.pop(),
                  ),
                  const Expanded(
                    child: Text(
                      'Route Options',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 17,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            // Route summary card
            Container(
              margin: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                    color: Colors.white.withValues(alpha: 0.18)),
              ),
              child: Row(
                children: [
                  Column(
                    children: [
                      Container(
                        width: 8,
                        height: 8,
                        decoration: const BoxDecoration(
                          color: Colors.white,
                          shape: BoxShape.circle,
                        ),
                      ),
                      Container(
                          width: 2, height: 24, color: Colors.white54),
                      const Icon(LucideIcons.mapPin,
                          color: Colors.white, size: 14),
                    ],
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          originLabel,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 12),
                        Text(
                          destLabel,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Route list ───────────────────────────────────────────────────────────────

class _RouteList extends ConsumerWidget {
  final List<RouteSuggestion> suggestions;
  final String originLabel;
  final String destLabel;

  const _RouteList({
    required this.suggestions,
    required this.originLabel,
    required this.destLabel,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 100),
      itemCount: suggestions.length + 1,
      itemBuilder: (ctx, i) {
        if (i == 0) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Row(
              children: [
                const Icon(LucideIcons.bus,
                    size: 16, color: AppColors.primary),
                const SizedBox(width: 6),
                Text(
                  '${suggestions.length} route${suggestions.length == 1 ? '' : 's'} found',
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: AppColors.primary,
                  ),
                ),
              ],
            ),
          );
        }
        final s = suggestions[i - 1];
        return _RouteOptionCard(
          suggestion: s,
          rank: i,
          originLabel: originLabel,
          destLabel: destLabel,
          onTap: () {
            ref.read(selectedSuggestionProvider.notifier).select(s);
            context.push('/route-details');
          },
        ).animate().fadeIn(delay: (i * 80).ms).slideY(begin: 0.1, end: 0);
      },
    );
  }
}

// ─── Route option card ────────────────────────────────────────────────────────

class _RouteOptionCard extends StatelessWidget {
  final RouteSuggestion suggestion;
  final int rank;
  final String originLabel;
  final String destLabel;
  final VoidCallback onTap;

  const _RouteOptionCard({
    required this.suggestion,
    required this.rank,
    required this.originLabel,
    required this.destLabel,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final tc = _tierColor(suggestion.tier);
    final tl = _tierLabel(suggestion.tier);
    final ti = _tierIcon(suggestion.tier);

    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.07),
              blurRadius: 16,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── Card header: route name + tier ────────────────────────────
            Container(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 12),
              decoration: BoxDecoration(
                color: tc.withValues(alpha: 0.06),
                borderRadius:
                    const BorderRadius.vertical(top: Radius.circular(20)),
                border: Border(
                  bottom: BorderSide(
                      color: tc.withValues(alpha: 0.15), width: 1),
                ),
              ),
              child: Row(
                children: [
                  Container(
                    width: 30,
                    height: 30,
                    decoration: BoxDecoration(
                      color: _kRouteBlue,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Center(
                      child: Text(
                        '$rank',
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      suggestion.routeName,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 8),
                  _TierBadge(color: tc, icon: ti, label: tl),
                ],
              ),
            ),

            // ── Journey path: boarding → alighting ────────────────────────
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
              child: Row(
                children: [
                  _PointDot(
                    icon: suggestion.boardingPoint.isBusPark
                        ? LucideIcons.building2
                        : LucideIcons.bus,
                    color: _kWalkGreen,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          suggestion.boardingPoint.pointName,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AppColors.onSurface,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          suggestion.boardingPoint.isBusPark
                              ? 'Bus Park · Seq ${suggestion.boardingPoint.sequence}'
                              : 'Bus Stop · Seq ${suggestion.boardingPoint.sequence}',
                          style: const TextStyle(
                            fontSize: 11,
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            Padding(
              padding: const EdgeInsets.only(left: 27, top: 2, bottom: 2),
              child: Column(
                children: [
                  for (int i = 0; i < 3; i++) ...[
                    Container(
                        width: 2,
                        height: 5,
                        color: _kRouteBlue.withValues(alpha: 0.4)),
                    const SizedBox(height: 2),
                  ],
                ],
              ),
            ),

            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
              child: Row(
                children: [
                  _PointDot(
                    icon: suggestion.destinationPoint.isBusPark
                        ? LucideIcons.building2
                        : LucideIcons.bus,
                    color: _kAlightOrange,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          suggestion.destinationPoint.pointName,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AppColors.onSurface,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          suggestion.destinationPoint.isBusPark
                              ? 'Bus Park · Seq ${suggestion.destinationPoint.sequence}'
                              : 'Bus Stop · Seq ${suggestion.destinationPoint.sequence}',
                          style: const TextStyle(
                            fontSize: 11,
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            // ── Walk journey visual ───────────────────────────────────────
            Container(
              margin: const EdgeInsets.fromLTRB(16, 0, 16, 14),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppColors.outlineVariant),
              ),
              child: Row(
                children: [
                  // Walk to boarding
                  _WalkSegment(
                    km: suggestion.walkToBoardingKm,
                    minutes: suggestion.walkToBoardingMinutes,
                    color: _kWalkGreen,
                  ),

                  // Bus segment
                  Expanded(
                    child: Column(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 10, vertical: 5),
                          decoration: BoxDecoration(
                            color: _kRouteBlue,
                            borderRadius: BorderRadius.circular(20),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: const [
                              Icon(LucideIcons.bus,
                                  size: 12, color: Colors.white),
                              SizedBox(width: 4),
                              Text('BUS',
                                  style: TextStyle(
                                    color: Colors.white,
                                    fontSize: 10,
                                    fontWeight: FontWeight.bold,
                                  )),
                            ],
                          ),
                        ),
                        Container(
                            height: 2,
                            color: _kRouteBlue.withValues(alpha: 0.3)),
                      ],
                    ),
                  ),

                  // Walk to destination
                  _WalkSegment(
                    km: suggestion.walkToDestinationKm,
                    minutes: suggestion.walkToDestinationMinutes,
                    color: _kAlightOrange,
                    alignRight: true,
                  ),
                ],
              ),
            ),

            // ── Stats footer ──────────────────────────────────────────────
            Container(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius:
                    const BorderRadius.vertical(bottom: Radius.circular(20)),
              ),
              child: Row(
                children: [
                  _StatChip(
                    icon: LucideIcons.footprints,
                    label:
                        '${suggestion.totalWalkingKm.toStringAsFixed(2)} km total walk',
                    color: AppColors.primary,
                  ),
                  const SizedBox(width: 8),
                  _StatChip(
                    icon: LucideIcons.timer,
                    label: '${suggestion.totalWalkingMinutes} min',
                    color: AppColors.primary,
                  ),
                  const Spacer(),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: _kRouteBlue,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text('View',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                            )),
                        SizedBox(width: 4),
                        Icon(LucideIcons.arrowRight,
                            size: 12, color: Colors.white),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Small reusable components ────────────────────────────────────────────────

class _TierBadge extends StatelessWidget {
  final Color color;
  final IconData icon;
  final String label;
  const _TierBadge(
      {required this.color, required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.35)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 11, color: color),
          const SizedBox(width: 4),
          Text(label,
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.bold,
                color: color,
              )),
        ],
      ),
    );
  }
}

class _PointDot extends StatelessWidget {
  final IconData icon;
  final Color color;
  const _PointDot({required this.icon, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 28,
      height: 28,
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        shape: BoxShape.circle,
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Icon(icon, size: 13, color: color),
    );
  }
}

class _WalkSegment extends StatelessWidget {
  final double km;
  final int minutes;
  final Color color;
  final bool alignRight;
  const _WalkSegment({
    required this.km,
    required this.minutes,
    required this.color,
    this.alignRight = false,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment:
          alignRight ? CrossAxisAlignment.end : CrossAxisAlignment.start,
      children: [
        Icon(LucideIcons.footprints, size: 14, color: color),
        const SizedBox(height: 2),
        Text(
          '${km.toStringAsFixed(2)} km',
          style: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
        Text(
          '$minutes min',
          style: const TextStyle(
              fontSize: 10, color: AppColors.onSurfaceVariant),
        ),
      ],
    );
  }
}

class _StatChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  const _StatChip(
      {required this.icon, required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 12, color: color),
        const SizedBox(width: 3),
        Text(label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: color,
            )),
      ],
    );
  }
}

// ─── States ──────────────────────────────────────────────────────────────────

class _LoadingView extends StatelessWidget {
  const _LoadingView();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          CircularProgressIndicator(color: AppColors.primary, strokeWidth: 2),
          SizedBox(height: 20),
          Text('Finding best routes…',
              style: TextStyle(
                  color: AppColors.onSurfaceVariant, fontSize: 15)),
        ],
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String message;
  const _ErrorView({required this.message});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(LucideIcons.alertCircle,
                color: AppColors.error, size: 48),
            const SizedBox(height: 16),
            Text(message,
                textAlign: TextAlign.center,
                style:
                    const TextStyle(color: AppColors.onSurfaceVariant)),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: () => context.pop(),
              icon: const Icon(LucideIcons.arrowLeft, size: 18),
              label: const Text('Go Back'),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyView extends StatelessWidget {
  final String originLabel;
  final String destLabel;
  const _EmptyView(
      {required this.originLabel, required this.destLabel});

  @override
  Widget build(BuildContext context) {
    return Center(
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
            Text('$originLabel → $destLabel',
                textAlign: TextAlign.center,
                style: const TextStyle(
                    color: AppColors.onSurfaceVariant, fontSize: 13)),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: () => context.pop(),
              icon: const Icon(LucideIcons.arrowLeft, size: 18),
              label: const Text('Search Again'),
            ),
          ],
        ),
      ),
    );
  }
}
