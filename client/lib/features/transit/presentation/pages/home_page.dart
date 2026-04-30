import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/app_widgets.dart';
import 'package:client/core/widgets/glass_container.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/presentation/providers/journey_planner_providers.dart';

class HomePage extends ConsumerStatefulWidget {
  const HomePage({super.key});

  @override
  ConsumerState<HomePage> createState() => _HomePageState();
}

class _HomePageState extends ConsumerState<HomePage> {
  final _fromController = TextEditingController();
  final _toController = TextEditingController();

  String _fromQuery = '';
  String _toQuery = '';

  @override
  void initState() {
    super.initState();
    _fromController.addListener(_onFromChanged);
    _toController.addListener(_onToChanged);
  }

  @override
  void dispose() {
    _fromController
      ..removeListener(_onFromChanged)
      ..dispose();
    _toController
      ..removeListener(_onToChanged)
      ..dispose();
    super.dispose();
  }

  void _onFromChanged() {
    final text = _fromController.text;
    final selected = ref.read(selectedOriginProvider);
    if (selected != null && text != selected.name) {
      ref.read(selectedOriginProvider.notifier).select(null);
    }
    setState(() => _fromQuery = text);
  }

  void _onToChanged() {
    final text = _toController.text;
    final selected = ref.read(selectedDestinationProvider);
    if (selected != null && text != selected.name) {
      ref.read(selectedDestinationProvider.notifier).select(null);
    }
    setState(() => _toQuery = text);
  }

  void _selectOrigin(OsmPlace place) {
    ref.read(selectedOriginProvider.notifier).select(place);
    _fromController.text = place.name;
    setState(() => _fromQuery = '');
    FocusScope.of(context).unfocus();
  }

  void _selectDestination(OsmPlace place) {
    ref.read(selectedDestinationProvider.notifier).select(place);
    _toController.text = place.name;
    setState(() => _toQuery = '');
    FocusScope.of(context).unfocus();
  }

  void _clearOrigin() {
    ref.read(selectedOriginProvider.notifier).select(null);
    _fromController.clear();
    setState(() => _fromQuery = '');
  }

  void _clearDestination() {
    ref.read(selectedDestinationProvider.notifier).select(null);
    _toController.clear();
    setState(() => _toQuery = '');
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
      // Fall back to GPS when From is empty
      final position = ref.read(locationProvider).value;
      if (position == null) {
        _snack('Could not get your location. Please search for an origin.');
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
        SnackBar(content: Text(msg), behavior: SnackBarBehavior.floating),
      );

  @override
  Widget build(BuildContext context) {
    final isPlanning = ref.watch(journeyPlanNotifierProvider).isLoading;

    return Scaffold(
      backgroundColor: AppColors.background,
      body: Stack(
        children: [
          _buildHeader(context),
          SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(height: 100),
                  _buildSearchCard(isPlanning),
                  const SizedBox(height: 40),
                  const SectionHeader(title: 'Quick Actions'),
                  const SizedBox(height: 16),
                  _buildQuickActions(context),
                  const SizedBox(height: 32),
                  const SectionHeader(
                    title: 'Saved Routes',
                    subtitle: 'Your frequent commutes',
                  ),
                  const SizedBox(height: 16),
                  _buildSavedRoutes(context),
                  const SizedBox(height: 120),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Container(
      height: 280,
      width: double.infinity,
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [AppColors.primary, AppColors.primaryContainer],
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 60, 24, 0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'IOTS',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Colors.white70,
                        letterSpacing: 2,
                        fontWeight: FontWeight.bold,
                      ),
                ),
                const GlassContainer(
                  padding: EdgeInsets.all(8),
                  child: Icon(LucideIcons.bell, size: 18, color: Colors.white),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              'Plan your journey\nin Kigali',
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    height: 1.2,
                  ),
            ),
          ],
        ),
      ),
    ).animate().fadeIn(duration: 800.ms);
  }

  Widget _buildSearchCard(bool isPlanning) {
    final locAsync = ref.watch(locationProvider);
    final selectedOrigin = ref.watch(selectedOriginProvider);
    final selectedDest = ref.watch(selectedDestinationProvider);

    final showFromDrop = _fromQuery.trim().length >= 2 && selectedOrigin == null;
    final showToDrop = _toQuery.trim().length >= 2 && selectedDest == null;

    // Watch providers only when dropdown is active to avoid unnecessary fetches
    final fromResults =
        showFromDrop ? ref.watch(osmSearchProvider(_fromQuery.trim())) : null;
    final toResults =
        showToDrop ? ref.watch(osmSearchProvider(_toQuery.trim())) : null;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: AppColors.primary.withValues(alpha: 0.15),
            blurRadius: 40,
            offset: const Offset(0, 20),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // ── From ──────────────────────────────────────────────────────────
          _SearchInputRow(
            controller: _fromController,
            icon: LucideIcons.circleDot,
            iconColor: AppColors.primary,
            label: 'From',
            hint: selectedOrigin == null && !locAsync.isLoading
                ? 'Current Location (GPS)'
                : 'Search origin in Rwanda…',
            isLoading: locAsync.isLoading && selectedOrigin == null,
            hasSelection: selectedOrigin != null,
            onClear: _clearOrigin,
          ),

          if (showFromDrop) _Dropdown(results: fromResults, onSelect: _selectOrigin),

          const Padding(
            padding: EdgeInsets.only(left: 36),
            child: Divider(height: 32, color: AppColors.surfaceContainerLow),
          ),

          // ── To ────────────────────────────────────────────────────────────
          _SearchInputRow(
            controller: _toController,
            icon: LucideIcons.mapPin,
            iconColor: AppColors.secondary,
            label: 'To',
            hint: 'Search destination in Rwanda…',
            isLoading: false,
            hasSelection: selectedDest != null,
            onClear: _clearDestination,
          ),

          if (showToDrop) _Dropdown(results: toResults, onSelect: _selectDestination),

          const SizedBox(height: 24),

          // ── Find button ───────────────────────────────────────────────────
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: isPlanning ? null : _findRoutes,
              style: FilledButton.styleFrom(
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16)),
                padding: const EdgeInsets.symmetric(vertical: 18),
              ),
              child: isPlanning
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white),
                    )
                  : const Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(LucideIcons.search, size: 20),
                        SizedBox(width: 8),
                        Text(
                          'Find Best Routes',
                          style: TextStyle(
                              fontWeight: FontWeight.bold, fontSize: 16),
                        ),
                      ],
                    ),
            ),
          ),
        ],
      ),
    ).animate().fadeIn(delay: 300.ms).slideY(begin: 0.2, end: 0);
  }

  Widget _buildQuickActions(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _QuickActionCard(
            icon: LucideIcons.bus,
            label: 'Nearby',
            onTap: () => context.push('/nearby'),
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: _QuickActionCard(
            icon: LucideIcons.map,
            label: 'View Map',
            onTap: () => context.push('/tracking'),
          ),
        ),
      ],
    ).animate().fadeIn(delay: 500.ms);
  }

  Widget _buildSavedRoutes(BuildContext context) {
    return TonalCard(
      margin: const EdgeInsets.only(bottom: 12),
      child: ListTile(
        leading: const Icon(LucideIcons.bookmark, color: AppColors.primary),
        title: const Text('CBD → Remera',
            style: TextStyle(fontWeight: FontWeight.bold)),
        subtitle: const Text('Arrival in 5 mins'),
        trailing: const Icon(LucideIcons.chevronRight, size: 16),
        onTap: () => context.push('/tracking'),
      ),
    ).animate().fadeIn(delay: 700.ms);
  }
}

// ── Reusable search input row ─────────────────────────────────────────────────

class _SearchInputRow extends StatelessWidget {
  final TextEditingController controller;
  final IconData icon;
  final Color iconColor;
  final String label;
  final String hint;
  final bool isLoading;
  final bool hasSelection;
  final VoidCallback onClear;

  const _SearchInputRow({
    required this.controller,
    required this.icon,
    required this.iconColor,
    required this.label,
    required this.hint,
    required this.isLoading,
    required this.hasSelection,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Icon(icon, color: iconColor, size: 20),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  fontSize: 11,
                  color: AppColors.onSurfaceVariant,
                  fontWeight: FontWeight.bold,
                ),
              ),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: controller,
                      decoration: InputDecoration(
                        hintText: hint,
                        hintStyle: const TextStyle(
                          color: AppColors.onSurfaceVariant,
                          fontWeight: FontWeight.normal,
                          fontSize: 14,
                        ),
                        isDense: true,
                        contentPadding:
                            const EdgeInsets.symmetric(vertical: 8),
                        border: InputBorder.none,
                        enabledBorder: InputBorder.none,
                        focusedBorder: InputBorder.none,
                        fillColor: Colors.transparent,
                      ),
                      style: const TextStyle(
                          fontWeight: FontWeight.w600, fontSize: 15),
                    ),
                  ),
                  if (isLoading)
                    const SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(strokeWidth: 1.5),
                    )
                  else if (hasSelection)
                    GestureDetector(
                      onTap: onClear,
                      child: const Icon(LucideIcons.x,
                          size: 16, color: AppColors.onSurfaceVariant),
                    ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ── Dropdown with OSM suggestions ─────────────────────────────────────────────

class _Dropdown extends StatelessWidget {
  final AsyncValue<List<OsmPlace>>? results;
  final void Function(OsmPlace) onSelect;

  const _Dropdown({required this.results, required this.onSelect});

  @override
  Widget build(BuildContext context) {
    if (results == null) return const SizedBox.shrink();

    return results!.when(
      loading: () => const Padding(
        padding: EdgeInsets.only(top: 10),
        child: Center(
          child: SizedBox(
            height: 20,
            width: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      ),
      error: (e, _) => Padding(
        padding: const EdgeInsets.only(top: 8),
        child: Text(
          'Search error: $e',
          style: const TextStyle(
              color: AppColors.error, fontSize: 12),
        ),
      ),
      data: (places) {
        if (places.isEmpty) {
          return const Padding(
            padding: EdgeInsets.only(top: 8),
            child: Text(
              'No places found — try a different name',
              style: TextStyle(
                  color: AppColors.onSurfaceVariant, fontSize: 12),
            ),
          );
        }

        final items = places.take(5).toList();
        return Container(
          margin: const EdgeInsets.only(top: 8),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.outlineVariant),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (int i = 0; i < items.length; i++) ...[
                InkWell(
                  onTap: () => onSelect(items[i]),
                  borderRadius: BorderRadius.vertical(
                    top: i == 0 ? const Radius.circular(12) : Radius.zero,
                    bottom: i == items.length - 1
                        ? const Radius.circular(12)
                        : Radius.zero,
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 14, vertical: 10),
                    child: Row(
                      children: [
                        const Icon(LucideIcons.mapPin,
                            size: 15, color: AppColors.secondary),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                items[i].name,
                                style: const TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.onSurface,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              Text(
                                items[i].displayName,
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
                      ],
                    ),
                  ),
                ),
                if (i < items.length - 1)
                  const Divider(height: 1, color: AppColors.outlineVariant),
              ],
            ],
          ),
        );
      },
    );
  }
}

// ── Quick action card ─────────────────────────────────────────────────────────

class _QuickActionCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _QuickActionCard(
      {required this.icon, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return TonalCard(
      onTap: onTap,
      child: Column(
        children: [
          Icon(icon, color: AppColors.primary, size: 24),
          const SizedBox(height: 8),
          Text(label,
              style: const TextStyle(
                  fontWeight: FontWeight.bold, fontSize: 13)),
        ],
      ),
    );
  }
}
