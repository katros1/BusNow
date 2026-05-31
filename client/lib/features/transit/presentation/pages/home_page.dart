import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:lucide_icons/lucide_icons.dart';

import 'package:client/core/theme/app_colors.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/presentation/providers/journey_planner_providers.dart';

// ─── Home Page ────────────────────────────────────────────────────────────────

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return const Scaffold(
      backgroundColor: AppColors.background,
      body: _HomeBody(),
    );
  }
}

class _HomeBody extends ConsumerStatefulWidget {
  const _HomeBody();

  @override
  ConsumerState<_HomeBody> createState() => _HomeBodyState();
}

class _HomeBodyState extends ConsumerState<_HomeBody> {

  Future<void> _openSearch(bool isOrigin) async {
    final result = await showModalBottomSheet<OsmPlace>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _PlaceSearchSheet(
        label: isOrigin ? 'From' : 'To',
        hint: isOrigin ? 'Search origin in Rwanda…' : 'Where are you going?',
      ),
    );
    if (result == null) return;
    if (isOrigin) {
      ref.read(selectedOriginProvider.notifier).select(result);
    } else {
      ref.read(selectedDestinationProvider.notifier).select(result);
    }
  }

  void _swapPlaces() {
    final origin = ref.read(selectedOriginProvider);
    final dest = ref.read(selectedDestinationProvider);
    ref.read(selectedOriginProvider.notifier).select(dest);
    ref.read(selectedDestinationProvider.notifier).select(origin);
  }

  Future<void> _findRoutes() async {
    final destination = ref.read(selectedDestinationProvider);
    if (destination == null) {
      _snack('Please select a destination');
      return;
    }
    final origin = ref.read(selectedOriginProvider);
    List<double> originCoords;
    if (origin != null) {
      originCoords = [origin.lon, origin.lat];
    } else {
      final position = ref.read(locationProvider).value;
      if (position == null) {
        _snack('Could not get your GPS location. Please select an origin.');
        return;
      }
      originCoords = [position.longitude, position.latitude];
    }
    await ref.read(journeyPlanNotifierProvider.notifier).plan(
          origin: originCoords,
          destination: [destination.lon, destination.lat],
        );
    if (mounted) context.push('/search');
  }

  void _snack(String msg) => ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(msg),
          behavior: SnackBarBehavior.floating,
          backgroundColor: AppColors.onSurface,
        ),
      );

  @override
  Widget build(BuildContext context) {
    final locAsync = ref.watch(locationProvider);
    final origin = ref.watch(selectedOriginProvider);
    final dest = ref.watch(selectedDestinationProvider);
    final isPlanning = ref.watch(journeyPlanNotifierProvider).isLoading;

    final originLabel = origin?.name ??
        (locAsync.value != null ? 'My Current Location' : null);
    final isGps = origin == null;

    return Stack(
      children: [
        // ── Gradient header background ────────────────────────────────────────
        _GradientHeader(),

        // ── Scrollable content ───────────────────────────────────────────────
        SafeArea(
          child: SingleChildScrollView(
            physics: const BouncingScrollPhysics(),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Top bar
                _TopBar(),
                const SizedBox(height: 10),

                // Hero text
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Plan your journey',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 26,
                          fontWeight: FontWeight.bold,
                          height: 1.2,
                        ),
                      ),
                      Row(
                        children: [
                          const Text(
                            'in Kigali',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 26,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(width: 8),
                          const Text('🇷🇼', style: TextStyle(fontSize: 20)),
                        ],
                      ),
                    ],
                  ),
                ).animate().fadeIn(delay: 200.ms).slideX(begin: -0.05, end: 0),

                const SizedBox(height: 24),

                // ── Search card ───────────────────────────────────────────────
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Container(
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(24),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.14),
                          blurRadius: 28,
                          offset: const Offset(0, 10),
                        ),
                      ],
                    ),
                    child: Column(
                      children: [
                        // From
                        _SearchFieldTile(
                          icon: LucideIcons.navigation2,
                          iconBg: AppColors.primary.withValues(alpha: 0.1),
                          iconColor: AppColors.primary,
                          label: 'FROM',
                          value: originLabel,
                          placeholder: 'Search origin in Rwanda…',
                          gpsChip: isGps && locAsync.value != null,
                          isLoading: locAsync.isLoading && isGps,
                          hasSelection: !isGps,
                          onTap: () => _openSearch(true),
                          onClear: () =>
                              ref.read(selectedOriginProvider.notifier).select(null),
                          topRadius: 24,
                        ),

                        // Divider + swap
                        _SwapRow(onSwap: _swapPlaces),

                        // To
                        _SearchFieldTile(
                          icon: LucideIcons.mapPin,
                          iconBg: AppColors.primaryContainer,
                          iconColor: AppColors.primaryDark,
                          label: 'TO',
                          value: dest?.name,
                          placeholder: 'Where are you going?',
                          gpsChip: false,
                          isLoading: false,
                          hasSelection: dest != null,
                          onTap: () => _openSearch(false),
                          onClear: () => ref
                              .read(selectedDestinationProvider.notifier)
                              .select(null),
                          topRadius: 0,
                        ),

                        // Find button
                        Padding(
                          padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                          child: SizedBox(
                            width: double.infinity,
                            child: AnimatedContainer(
                              duration: 250.ms,
                              child: FilledButton(
                                onPressed: isPlanning ? null : _findRoutes,
                                style: FilledButton.styleFrom(
                                  backgroundColor: dest != null
                                      ? AppColors.primary
                                      : AppColors.surfaceContainerHigh,
                                  foregroundColor: dest != null
                                      ? Colors.white
                                      : AppColors.onSurfaceVariant,
                                  shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(14)),
                                  padding:
                                      const EdgeInsets.symmetric(vertical: 16),
                                  elevation: dest != null ? 0 : 0,
                                ),
                                child: isPlanning
                                    ? const SizedBox(
                                        height: 20,
                                        width: 20,
                                        child: CircularProgressIndicator(
                                            strokeWidth: 2,
                                            color: Colors.white),
                                      )
                                    : Row(
                                        mainAxisAlignment:
                                            MainAxisAlignment.center,
                                        children: [
                                          Icon(LucideIcons.search, size: 18),
                                          const SizedBox(width: 8),
                                          const Text(
                                            'Find Best Routes',
                                            style: TextStyle(
                                                fontWeight: FontWeight.bold,
                                                fontSize: 15),
                                          ),
                                        ],
                                      ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ).animate().fadeIn(delay: 350.ms).slideY(begin: 0.12, end: 0),

                const SizedBox(height: 36),

                // ── Quick actions ─────────────────────────────────────────────
                _SectionHeader(title: 'Quick Actions'),
                const SizedBox(height: 14),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    children: [
                      Expanded(
                        child: _QuickCard(
                          icon: LucideIcons.bus,
                          label: 'Nearby\nStops',
                          color: AppColors.primaryDark,
                          onTap: () => context.push('/nearby'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _QuickCard(
                          icon: LucideIcons.map,
                          label: 'Live\nTracking',
                          color: AppColors.primary,
                          onTap: () => context.push('/tracking'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _QuickCard(
                          icon: LucideIcons.bookmark,
                          label: 'Saved\nRoutes',
                          color: AppColors.primary,
                          onTap: () => context.push('/saved'),
                        ),
                      ),
                    ],
                  ),
                ).animate().fadeIn(delay: 500.ms),

                const SizedBox(height: 36),

                // ── Recent ────────────────────────────────────────────────────
                _SectionHeader(title: 'Recent Trips'),
                const SizedBox(height: 14),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Container(
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AppColors.outlineVariant),
                    ),
                    child: Column(
                      children: [
                        _RecentTile(
                          from: 'CBD',
                          to: 'Remera',
                          time: '5 min',
                          isFirst: true,
                          onTap: () => context.push('/tracking'),
                        ),
                        const Divider(
                            height: 1,
                            indent: 64,
                            color: AppColors.outlineVariant),
                        _RecentTile(
                          from: 'Kimironko',
                          to: 'Nyabugogo',
                          time: '12 min',
                          isLast: true,
                          onTap: () => context.push('/tracking'),
                        ),
                      ],
                    ),
                  ),
                ).animate().fadeIn(delay: 650.ms),

                const SizedBox(height: 120),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

// ─── Gradient header ──────────────────────────────────────────────────────────

class _GradientHeader extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 290,
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomCenter,
          colors: [AppColors.primaryDark, AppColors.primary, AppColors.primaryLight],
          stops: [0.0, 0.55, 1.0],
        ),
      ),
      child: Stack(
        children: [
          Positioned(
            top: -50,
            right: -50,
            child: Container(
              width: 220,
              height: 220,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withValues(alpha: 0.05),
              ),
            ),
          ),
          Positioned(
            top: 60,
            right: 80,
            child: Container(
              width: 90,
              height: 90,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withValues(alpha: 0.04),
              ),
            ),
          ),
          Positioned(
            bottom: 30,
            left: -20,
            child: Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withValues(alpha: 0.03),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

class _TopBar extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'BusNow',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 2,
                ),
              ),
              const Text(
                'Rwanda Transit',
                style: TextStyle(
                  color: Colors.white60,
                  fontSize: 10,
                  letterSpacing: 0.5,
                ),
              ),
            ],
          ),
          Row(
            children: [
              _HeaderIconButton(
                icon: LucideIcons.bell,
                onTap: () {},
              ),
              const SizedBox(width: 8),
              _HeaderIconButton(
                icon: LucideIcons.user,
                onTap: () => context.push('/settings'),
              ),
            ],
          ),
        ],
      ),
    ).animate().fadeIn(duration: 500.ms);
  }
}

class _HeaderIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _HeaderIconButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 36,
        height: 36,
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.15),
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white.withValues(alpha: 0.2)),
        ),
        child: Icon(icon, color: Colors.white, size: 17),
      ),
    );
  }
}

// ─── Search field tile ────────────────────────────────────────────────────────

class _SearchFieldTile extends StatelessWidget {
  final IconData icon;
  final Color iconBg;
  final Color iconColor;
  final String label;
  final String? value;
  final String placeholder;
  final bool gpsChip;
  final bool isLoading;
  final bool hasSelection;
  final VoidCallback onTap;
  final VoidCallback onClear;
  final double topRadius;

  const _SearchFieldTile({
    required this.icon,
    required this.iconBg,
    required this.iconColor,
    required this.label,
    required this.value,
    required this.placeholder,
    required this.gpsChip,
    required this.isLoading,
    required this.hasSelection,
    required this.onTap,
    required this.onClear,
    required this.topRadius,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(
            top: Radius.circular(topRadius),
          ),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: iconBg,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: iconColor, size: 16),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: const TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurfaceVariant,
                      letterSpacing: 0.8,
                    ),
                  ),
                  const SizedBox(height: 3),
                  if (isLoading)
                    const SizedBox(
                      height: 2,
                      child: LinearProgressIndicator(
                        color: AppColors.primary,
                        backgroundColor: AppColors.outlineVariant,
                      ),
                    )
                  else
                    Row(
                      children: [
                        if (gpsChip) ...[
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: AppColors.primary.withValues(alpha: 0.1),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(LucideIcons.navigation2,
                                    size: 9, color: AppColors.primary),
                                const SizedBox(width: 3),
                                const Text('GPS',
                                    style: TextStyle(
                                      fontSize: 9,
                                      fontWeight: FontWeight.bold,
                                      color: AppColors.primary,
                                    )),
                              ],
                            ),
                          ),
                          const SizedBox(width: 6),
                        ],
                        Expanded(
                          child: Text(
                            value ?? placeholder,
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: value != null
                                  ? FontWeight.w600
                                  : FontWeight.normal,
                              color: value != null
                                  ? AppColors.onSurface
                                  : AppColors.onSurfaceVariant,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            if (hasSelection)
              GestureDetector(
                onTap: onClear,
                child: Container(
                  width: 22,
                  height: 22,
                  decoration: const BoxDecoration(
                    color: AppColors.surfaceContainerHigh,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(LucideIcons.x,
                      size: 11, color: AppColors.onSurfaceVariant),
                ),
              )
            else
              const Icon(LucideIcons.chevronRight,
                  size: 15, color: AppColors.outlineVariant),
          ],
        ),
      ),
    );
  }
}

// ─── Swap divider row ────────────────────────────────────────────────────────

class _SwapRow extends StatelessWidget {
  final VoidCallback onSwap;
  const _SwapRow({required this.onSwap});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 1,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Container(color: AppColors.outlineVariant),
          Positioned(
            right: 20,
            top: -19,
            child: GestureDetector(
              onTap: onSwap,
              child: Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: Colors.white,
                  shape: BoxShape.circle,
                  border: Border.all(
                      color: AppColors.outlineVariant, width: 1.5),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.07),
                      blurRadius: 8,
                    ),
                  ],
                ),
                child: const Icon(LucideIcons.arrowUpDown,
                    size: 16, color: AppColors.primary),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Section header ───────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader({required this.title});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Text(
        title,
        style: const TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.bold,
          color: AppColors.onSurface,
        ),
      ),
    );
  }
}

// ─── Quick action card ────────────────────────────────────────────────────────

class _QuickCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;
  const _QuickCard(
      {required this.icon,
      required this.label,
      required this.color,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 18),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: color.withValues(alpha: 0.15)),
        ),
        child: Column(
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(13),
              ),
              child: Icon(icon, color: Colors.white, size: 20),
            ),
            const SizedBox(height: 10),
            Text(
              label,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.bold,
                color: color,
                height: 1.3,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Recent trip tile ────────────────────────────────────────────────────────

class _RecentTile extends StatelessWidget {
  final String from;
  final String to;
  final String time;
  final bool isFirst;
  final bool isLast;
  final VoidCallback onTap;

  const _RecentTile({
    required this.from,
    required this.to,
    required this.time,
    this.isFirst = false,
    this.isLast = false,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.vertical(
        top: isFirst ? const Radius.circular(16) : Radius.zero,
        bottom: isLast ? const Radius.circular(16) : Radius.zero,
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
        child: Row(
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                shape: BoxShape.circle,
              ),
              child: const Icon(LucideIcons.clock,
                  size: 15, color: AppColors.onSurfaceVariant),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '$from  →  $to',
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: AppColors.onSurface,
                    ),
                  ),
                  Text(
                    'Bus route · $time walk',
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(LucideIcons.chevronRight,
                size: 15, color: AppColors.outlineVariant),
          ],
        ),
      ),
    );
  }
}

// ─── Place search modal ───────────────────────────────────────────────────────

class _PlaceSearchSheet extends ConsumerStatefulWidget {
  final String label;
  final String hint;
  const _PlaceSearchSheet({required this.label, required this.hint});

  @override
  ConsumerState<_PlaceSearchSheet> createState() => _PlaceSearchSheetState();
}

class _PlaceSearchSheetState extends ConsumerState<_PlaceSearchSheet> {
  final _controller = TextEditingController();
  final _focusNode = FocusNode();
  String _query = '';

  @override
  void initState() {
    super.initState();
    _controller.addListener(() {
      final t = _controller.text;
      if (t != _query) setState(() => _query = t);
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _focusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final q = _query.trim();
    final results = q.length >= 2 ? ref.watch(osmSearchProvider(q)) : null;

    return Container(
      height: MediaQuery.of(context).size.height * 0.93,
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        children: [
          // Handle bar
          Container(
            margin: const EdgeInsets.only(top: 12),
            width: 36,
            height: 4,
            decoration: BoxDecoration(
              color: AppColors.outlineVariant,
              borderRadius: BorderRadius.circular(2),
            ),
          ),

          // Search input row
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
            child: Row(
              children: [
                GestureDetector(
                  onTap: () => Navigator.pop(context),
                  child: Container(
                    width: 38,
                    height: 38,
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(LucideIcons.arrowLeft,
                        size: 18, color: AppColors.onSurface),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 12, vertical: 11),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppColors.outlineVariant),
                    ),
                    child: Row(
                      children: [
                        const Icon(LucideIcons.search,
                            size: 16,
                            color: AppColors.onSurfaceVariant),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: _controller,
                            focusNode: _focusNode,
                            decoration: InputDecoration(
                              hintText: widget.hint,
                              hintStyle: const TextStyle(
                                  color: AppColors.onSurfaceVariant,
                                  fontSize: 14),
                              isDense: true,
                              contentPadding: EdgeInsets.zero,
                              border: InputBorder.none,
                              enabledBorder: InputBorder.none,
                              focusedBorder: InputBorder.none,
                            ),
                            style: const TextStyle(
                                fontSize: 14, fontWeight: FontWeight.w500),
                            textInputAction: TextInputAction.search,
                          ),
                        ),
                        if (_query.isNotEmpty)
                          GestureDetector(
                            onTap: () {
                              _controller.clear();
                              setState(() => _query = '');
                            },
                            child: const Icon(LucideIcons.x,
                                size: 16,
                                color: AppColors.onSurfaceVariant),
                          ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 8),
          const Divider(height: 1, color: AppColors.outlineVariant),

          // Results
          Expanded(child: _buildBody(results)),
        ],
      ),
    );
  }

  Widget _buildBody(AsyncValue<List<OsmPlace>>? results) {
    if (results == null) return _buildSuggestions();

    return results.when(
      loading: () => const Center(
        child: Padding(
          padding: EdgeInsets.all(40),
          child: CircularProgressIndicator(
              color: AppColors.primary, strokeWidth: 2),
        ),
      ),
      error: (e, _) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(LucideIcons.wifiOff,
                  size: 44, color: AppColors.onSurfaceVariant),
              const SizedBox(height: 16),
              const Text(
                'Search unavailable',
                style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: AppColors.onSurface),
              ),
              const SizedBox(height: 6),
              const Text(
                'Check your internet connection\nand try again',
                textAlign: TextAlign.center,
                style: TextStyle(
                    fontSize: 13, color: AppColors.onSurfaceVariant),
              ),
            ],
          ),
        ),
      ),
      data: (places) {
        if (places.isEmpty) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(LucideIcons.searchX,
                      size: 44, color: AppColors.onSurfaceVariant),
                  const SizedBox(height: 16),
                  Text(
                    'No results for "$_query"',
                    style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'Try a different spelling or\na nearby landmark',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                        fontSize: 13, color: AppColors.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.symmetric(vertical: 8),
          itemCount: places.length,
          separatorBuilder: (context, i) => const Divider(
              height: 1,
              indent: 64,
              endIndent: 16,
              color: AppColors.outlineVariant),
          itemBuilder: (_, i) => _PlaceTile(
            place: places[i],
            onTap: () => Navigator.pop(context, places[i]),
          ),
        );
      },
    );
  }

  Widget _buildSuggestions() {
    const suggestions = [
      ('Nyabugogo Bus Terminal', LucideIcons.building2),
      ('Kimironko Market', LucideIcons.shoppingBag),
      ('Remera Bus Stop', LucideIcons.bus),
      ('Kigali City Tower', LucideIcons.landmark),
      ('Kacyiru', LucideIcons.mapPin),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(20, 16, 20, 10),
          child: Text(
            'POPULAR PLACES',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.8,
            ),
          ),
        ),
        Expanded(
          child: ListView.separated(
            padding: EdgeInsets.zero,
            itemCount: suggestions.length,
            separatorBuilder: (context, i) => const Divider(
                height: 1,
                indent: 64,
                endIndent: 16,
                color: AppColors.outlineVariant),
            itemBuilder: (_, i) {
              final (label, icon) = suggestions[i];
              return InkWell(
                onTap: () {
                  _controller.text = label;
                  setState(() => _query = label);
                },
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 16, vertical: 13),
                  child: Row(
                    children: [
                      Container(
                        width: 36,
                        height: 36,
                        decoration: BoxDecoration(
                          color:
                              AppColors.primary.withValues(alpha: 0.08),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(icon,
                            size: 16, color: AppColors.primary),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          label,
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w500,
                            color: AppColors.onSurface,
                          ),
                        ),
                      ),
                      const Icon(LucideIcons.arrowUpLeft,
                          size: 14,
                          color: AppColors.outlineVariant),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

// ─── Place result tile ────────────────────────────────────────────────────────

class _PlaceTile extends StatelessWidget {
  final OsmPlace place;
  final VoidCallback onTap;
  const _PlaceTile({required this.place, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.08),
                shape: BoxShape.circle,
              ),
              child: const Icon(LucideIcons.mapPin,
                  size: 16, color: AppColors.primary),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    place.name,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: AppColors.onSurface,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    place.displayName,
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
            const SizedBox(width: 8),
            const Icon(LucideIcons.cornerDownLeft,
                size: 14, color: AppColors.outlineVariant),
          ],
        ),
      ),
    );
  }
}
