import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:client/core/theme/app_colors.dart';
import 'package:client/features/journey_planner/presentation/providers/journey_planner_providers.dart';

// ─── AI Recommendations Page ──────────────────────────────────────────────────

class AiRecommendationsPage extends ConsumerStatefulWidget {
  const AiRecommendationsPage({super.key});

  @override
  ConsumerState<AiRecommendationsPage> createState() =>
      _AiRecommendationsPageState();
}

class _AiRecommendationsPageState
    extends ConsumerState<AiRecommendationsPage> {
  String? _selectedDestination;
  final _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _onDestinationSelected(String dest) {
    final pos = ref.read(locationProvider).value;
    setState(() => _selectedDestination = dest);
    if (pos == null) return;
    ref.read(aiRecommendationNotifierProvider.notifier).fetch(
          destination: dest,
          lat: pos.latitude,
          lon: pos.longitude,
        );
  }

  @override
  Widget build(BuildContext context) {
    final locAsync = ref.watch(locationProvider);
    final recAsync = ref.watch(aiRecommendationNotifierProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Row(
          children: [
            Icon(Icons.auto_awesome, size: 18, color: AppColors.primary),
            const SizedBox(width: 8),
            const Text(
              'AI Stop Finder',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
            ),
          ],
        ),
        backgroundColor: Colors.white,
        foregroundColor: AppColors.onSurface,
        elevation: 0,
        surfaceTintColor: Colors.transparent,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(1),
          child: Container(height: 1, color: AppColors.outlineVariant),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── AI banner ────────────────────────────────────────────────────
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [AppColors.primaryDark, AppColors.primary],
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                ),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Row(
                children: [
                  Container(
                    width: 46,
                    height: 46,
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(13),
                    ),
                    child: const Icon(Icons.auto_awesome,
                        color: Colors.white, size: 22),
                  ),
                  const SizedBox(width: 14),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'AI-Powered Recommendations',
                          style: TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
                          ),
                        ),
                        SizedBox(height: 3),
                        Text(
                          'Picks the best stop based on time,\ndemand & your location',
                          style: TextStyle(
                              color: Colors.white70, fontSize: 11, height: 1.4),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 28),

            // ── Destination search ────────────────────────────────────────────
            const Text(
              'WHERE ARE YOU GOING?',
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.bold,
                color: AppColors.onSurfaceVariant,
                letterSpacing: 0.8,
              ),
            ),
            const SizedBox(height: 12),
            Autocomplete<String>(
              optionsBuilder: (TextEditingValue val) {
                if (val.text.isEmpty) return kAiDestinations;
                return kAiDestinations.where((d) =>
                    d.toLowerCase().contains(val.text.toLowerCase()));
              },
              onSelected: _onDestinationSelected,
              fieldViewBuilder: (context, controller, focusNode, onSubmitted) {
                return TextField(
                  controller: controller,
                  focusNode: focusNode,
                  onSubmitted: (_) => onSubmitted(),
                  decoration: InputDecoration(
                    hintText: 'Search destination…',
                    hintStyle: const TextStyle(
                        fontSize: 14, color: AppColors.onSurfaceVariant),
                    prefixIcon: const Icon(Icons.search,
                        size: 20, color: AppColors.onSurfaceVariant),
                    suffixIcon: controller.text.isNotEmpty
                        ? IconButton(
                            icon: const Icon(Icons.clear,
                                size: 18, color: AppColors.onSurfaceVariant),
                            onPressed: () {
                              controller.clear();
                              setState(() => _selectedDestination = null);
                            },
                          )
                        : null,
                    filled: true,
                    fillColor: Colors.white,
                    contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 14),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide:
                          BorderSide(color: AppColors.outlineVariant),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide:
                          BorderSide(color: AppColors.outlineVariant),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide:
                          const BorderSide(color: AppColors.primary, width: 1.5),
                    ),
                  ),
                );
              },
              optionsViewBuilder: (context, onSelected, options) {
                return Align(
                  alignment: Alignment.topLeft,
                  child: Material(
                    elevation: 6,
                    borderRadius: BorderRadius.circular(12),
                    shadowColor: Colors.black12,
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxHeight: 220),
                      child: ListView.separated(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        shrinkWrap: true,
                        itemCount: options.length,
                        separatorBuilder: (_, __) => const Divider(
                            height: 1,
                            indent: 48,
                            endIndent: 16,
                            color: AppColors.outlineVariant),
                        itemBuilder: (context, i) {
                          final dest = options.elementAt(i);
                          final isSelected = dest == _selectedDestination;
                          return ListTile(
                            dense: true,
                            leading: Icon(
                              Icons.location_on_outlined,
                              size: 18,
                              color: isSelected
                                  ? AppColors.primary
                                  : AppColors.onSurfaceVariant,
                            ),
                            title: Text(
                              dest,
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: isSelected
                                    ? FontWeight.bold
                                    : FontWeight.w500,
                                color: isSelected
                                    ? AppColors.primary
                                    : AppColors.onSurface,
                              ),
                            ),
                            onTap: () => onSelected(dest),
                          );
                        },
                      ),
                    ),
                  ),
                );
              },
            ),
            // Selected destination badge
            if (_selectedDestination != null) ...[
              const SizedBox(height: 10),
              Row(
                children: [
                  const Icon(Icons.check_circle,
                      size: 14, color: AppColors.primary),
                  const SizedBox(width: 6),
                  Text(
                    'Going to $_selectedDestination',
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: AppColors.primary,
                    ),
                  ),
                ],
              ),
            ],

            const SizedBox(height: 24),

            // ── GPS status row ─────────────────────────────────────────────────
            _GpsStatusRow(locAsync: locAsync),

            const SizedBox(height: 28),

            // ── Results area ──────────────────────────────────────────────────
            if (_selectedDestination == null)
              const _EmptyState()
            else
              recAsync.when(
                data: (result) {
                  if (result == null) return const _EmptyState();
                  if (!result.success) {
                    return _ErrorCard(
                        message: result.error ?? 'Recommendation failed');
                  }
                  return _ResultsSection(result: result);
                },
                loading: () => const Center(
                  child: Padding(
                    padding: EdgeInsets.symmetric(vertical: 48),
                    child: Column(
                      children: [
                        CircularProgressIndicator(
                            color: AppColors.primary, strokeWidth: 2),
                        SizedBox(height: 16),
                        Text(
                          'AI is finding the best stop…',
                          style: TextStyle(
                              color: AppColors.onSurfaceVariant, fontSize: 13),
                        ),
                      ],
                    ),
                  ),
                ),
                error: (e, _) => _ErrorCard(message: e.toString()),
              ),

            const SizedBox(height: 40),
          ],
        ),
      ),
    );
  }
}

// ─── GPS status row ───────────────────────────────────────────────────────────

class _GpsStatusRow extends StatelessWidget {
  final AsyncValue<dynamic> locAsync;
  const _GpsStatusRow({required this.locAsync});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.outlineVariant),
      ),
      child: Row(
        children: [
          Container(
            width: 30,
            height: 30,
            decoration: BoxDecoration(
              color: locAsync.value != null
                  ? AppColors.primary.withValues(alpha: 0.1)
                  : AppColors.surfaceContainerHigh,
              shape: BoxShape.circle,
            ),
            child: Icon(
              Icons.navigation,
              size: 14,
              color: locAsync.value != null
                  ? AppColors.primary
                  : AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: locAsync.when(
              data: (pos) => pos != null
                  ? const Text(
                      'Your current location',
                      style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: AppColors.onSurface),
                    )
                  : const Text('GPS unavailable — grant location permission',
                      style: TextStyle(
                          fontSize: 12, color: AppColors.onSurfaceVariant)),
              loading: () => const Text('Getting your location…',
                  style: TextStyle(
                      fontSize: 12, color: AppColors.onSurfaceVariant)),
              error: (_, __) => const Text('Could not get location',
                  style: TextStyle(
                      fontSize: 12, color: AppColors.onSurfaceVariant)),
            ),
          ),
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: locAsync.value != null
                  ? const Color(0xFFE8F5E9)
                  : AppColors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(6),
            ),
            child: Text(
              locAsync.value != null ? 'GPS' : 'No GPS',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.bold,
                color: locAsync.value != null
                    ? const Color(0xFF2E7D32)
                    : AppColors.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 32),
        child: Column(
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.08),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.auto_awesome,
                  size: 32, color: AppColors.primary),
            ),
            const SizedBox(height: 16),
            const Text(
              'Select a destination above',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.bold,
                color: AppColors.onSurface,
              ),
            ),
            const SizedBox(height: 6),
            const Text(
              'AI will recommend the best\nbus stop for your trip',
              textAlign: TextAlign.center,
              style: TextStyle(
                  fontSize: 12, color: AppColors.onSurfaceVariant, height: 1.5),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Error card ───────────────────────────────────────────────────────────────

class _ErrorCard extends StatelessWidget {
  final String message;
  const _ErrorCard({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF3F3),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFFFCDD2)),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline,
              color: Color(0xFFD32F2F), size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              message,
              style: const TextStyle(
                  fontSize: 13, color: Color(0xFFD32F2F), height: 1.4),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Results section ──────────────────────────────────────────────────────────

class _ResultsSection extends StatelessWidget {
  final AiRecommendationResult result;
  const _ResultsSection({required this.result});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Time context badge
        if (result.timeContext != null)
          Container(
            margin: const EdgeInsets.only(bottom: 16),
            padding:
                const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
            decoration: BoxDecoration(
              color: AppColors.primaryContainer,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColors.primaryLight),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.schedule,
                    size: 12, color: AppColors.primaryDark),
                const SizedBox(width: 6),
                Text(
                  '${_timeContextLabel(result.timeContext!)} · ${result.destination}',
                  style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: AppColors.primaryDark,
                  ),
                ),
              ],
            ),
          ),

        // Best stop card
        if (result.bestStop != null) ...[
          const Text(
            'BEST STOP',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: 10),
          _BestStopCard(stop: result.bestStop!),
          const SizedBox(height: 24),
        ],

        // All recommendations
        if (result.recommendations.isNotEmpty) ...[
          const Text(
            'ALL OPTIONS',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: 10),
          Container(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.outlineVariant),
            ),
            child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: result.recommendations.length,
              separatorBuilder: (_, __) => const Divider(
                  height: 1,
                  indent: 64,
                  endIndent: 16,
                  color: AppColors.outlineVariant),
              itemBuilder: (_, i) =>
                  _StopTile(stop: result.recommendations[i], rank: i + 1),
            ),
          ),
        ],
      ],
    );
  }

  String _timeContextLabel(String ctx) {
    switch (ctx) {
      case 'peak':
        return 'Peak hours';
      case 'offpeak':
        return 'Off-peak';
      case 'weekend':
        return 'Weekend';
      default:
        return ctx;
    }
  }
}

// ─── Best stop card ───────────────────────────────────────────────────────────

class _BestStopCard extends StatelessWidget {
  final AiStopRecommendation stop;
  const _BestStopCard({required this.stop});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [AppColors.primaryDark, AppColors.primary],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(Icons.location_on_outlined,
                    color: Colors.white, size: 20),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      stop.stopName,
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                    Text(
                      '${stop.confidence.toStringAsFixed(0)}% confidence',
                      style: const TextStyle(
                          color: Colors.white70, fontSize: 12),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text(
                  'BEST',
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 11,
                    letterSpacing: 0.5,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              _StatChip(
                  icon: Icons.access_time,
                  label: '${stop.waitTime} min wait'),
              const SizedBox(width: 8),
              _StatChip(
                  icon: Icons.directions_walk,
                  label: '${stop.walkingTime} min walk'),
              const SizedBox(width: 8),
              _StatChip(
                  icon: Icons.payments_outlined,
                  label: '${stop.fare} RWF'),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              _StatChip(
                  icon: Icons.directions_bus,
                  label: '${stop.busFrequency}/hr'),
              const SizedBox(width: 8),
              _StatChip(
                  icon: Icons.location_on_outlined,
                  label: '${stop.distanceKm.toStringAsFixed(2)} km away'),
            ],
          ),
        ],
      ),
    );
  }
}

class _StatChip extends StatelessWidget {
  final IconData icon;
  final String label;
  const _StatChip({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding:
          const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 11, color: Colors.white70),
          const SizedBox(width: 5),
          Text(
            label,
            style: const TextStyle(
                color: Colors.white, fontSize: 11, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }
}

// ─── Stop list tile ───────────────────────────────────────────────────────────

class _StopTile extends StatelessWidget {
  final AiStopRecommendation stop;
  final int rank;
  const _StopTile({required this.stop, required this.rank});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: rank == 1
                  ? AppColors.primary.withValues(alpha: 0.12)
                  : AppColors.surfaceContainerLow,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                '$rank',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: rank == 1
                      ? AppColors.primary
                      : AppColors.onSurfaceVariant,
                ),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  stop.stopName,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurface,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  '${stop.walkingTime} min walk · ${stop.waitTime} min wait · ${stop.fare} RWF',
                  style: const TextStyle(
                      fontSize: 11, color: AppColors.onSurfaceVariant),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: AppColors.primary.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              '${stop.confidence.toStringAsFixed(0)}%',
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.bold,
                color: AppColors.primary,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
