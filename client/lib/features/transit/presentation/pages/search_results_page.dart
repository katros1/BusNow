import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/app_widgets.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/presentation/providers/journey_planner_providers.dart';

class SearchResultsPage extends ConsumerWidget {
  const SearchResultsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journeyPlanAsync = ref.watch(journeyPlanNotifierProvider);
    final origin = ref.watch(selectedOriginProvider);
    final destination = ref.watch(selectedDestinationProvider);
    final subtitle = [
      origin?.name ?? 'Current Location',
      if (destination != null) destination.name,
    ].join(' → ');

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.surfaceContainerLowest,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(LucideIcons.chevronLeft, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Route Options',
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.bold),
            ),
            Text(
              subtitle,
              style: const TextStyle(
                fontSize: 11,
                color: AppColors.onSurfaceVariant,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
      body: journeyPlanAsync.when(
        loading: () => const Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text(
                'Finding best routes...',
                style: TextStyle(color: AppColors.onSurfaceVariant),
              ),
            ],
          ),
        ),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(LucideIcons.alertCircle, color: AppColors.error, size: 48),
                const SizedBox(height: 16),
                Text(
                  e.toString(),
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.onSurfaceVariant),
                ),
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
        data: (plan) {
          if (plan == null) {
            return const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  CircularProgressIndicator(),
                  SizedBox(height: 16),
                  Text(
                    'Finding best routes...',
                    style: TextStyle(color: AppColors.onSurfaceVariant),
                  ),
                ],
              ),
            );
          }

          if (plan.suggestions.isEmpty) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLow,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        LucideIcons.search,
                        size: 40,
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 24),
                    const Text(
                      'No routes found',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Try adjusting your destination',
                      style: TextStyle(color: AppColors.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
            );
          }

          return ListView.builder(
            padding: const EdgeInsets.all(24),
            itemCount: plan.suggestions.length,
            itemBuilder: (context, index) {
              return _RouteSuggestionCard(
                suggestion: plan.suggestions[index],
                index: index,
              );
            },
          );
        },
      ),
    );
  }
}

class _RouteSuggestionCard extends StatelessWidget {
  final RouteSuggestion suggestion;
  final int index;

  const _RouteSuggestionCard({required this.suggestion, required this.index});

  @override
  Widget build(BuildContext context) {
    final tierData = _tierInfo(suggestion.tier);

    return TonalCard(
      margin: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  suggestion.routeName,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 17,
                    color: AppColors.onSurface,
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: tierData.color.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  tierData.label,
                  style: TextStyle(
                    color: tierData.color,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _JourneyStep(
            icon: LucideIcons.footprints,
            iconColor: AppColors.primary,
            text:
                'Walk ${suggestion.walkToBoardingKm.toStringAsFixed(1)} km to ${suggestion.boardingPoint.pointName}',
          ),
          const SizedBox(height: 8),
          _JourneyStep(
            icon: LucideIcons.bus,
            iconColor: AppColors.secondary,
            text:
                '${suggestion.boardingPoint.pointName} → ${suggestion.destinationPoint.pointName}',
          ),
          const SizedBox(height: 8),
          _JourneyStep(
            icon: LucideIcons.footprints,
            iconColor: AppColors.primary,
            text: 'Walk ${suggestion.walkToDestinationKm.toStringAsFixed(1)} km to destination',
          ),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Divider(color: AppColors.outlineVariant),
          ),
          Row(
            children: [
              const Icon(LucideIcons.personStanding, size: 14, color: AppColors.onSurfaceVariant),
              const SizedBox(width: 4),
              Text(
                'Total walking: ${suggestion.totalWalkingKm.toStringAsFixed(1)} km',
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
              const Spacer(),
              TextButton.icon(
                onPressed: () => context.push('/tracking'),
                icon: const Icon(LucideIcons.map, size: 14),
                label: const Text('View on Map', style: TextStyle(fontSize: 13)),
                style: TextButton.styleFrom(
                  foregroundColor: AppColors.primary,
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                ),
              ),
            ],
          ),
        ],
      ),
    )
        .animate()
        .fadeIn(delay: (index * 100).ms, duration: 400.ms)
        .slideY(begin: 0.1, end: 0);
  }

  _TierInfo _tierInfo(String tier) {
    return switch (tier) {
      'TIER_1' => _TierInfo(color: const Color(0xFF1A8F4A), label: 'Excellent'),
      'TIER_2' => _TierInfo(color: AppColors.secondary, label: 'Good'),
      _ => _TierInfo(color: const Color(0xFFE07B00), label: 'Fair'),
    };
  }
}

class _JourneyStep extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String text;

  const _JourneyStep({
    required this.icon,
    required this.iconColor,
    required this.text,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 16, color: iconColor),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(fontSize: 14, color: AppColors.onSurface),
          ),
        ),
      ],
    );
  }
}

class _TierInfo {
  final Color color;
  final String label;
  const _TierInfo({required this.color, required this.label});
}
