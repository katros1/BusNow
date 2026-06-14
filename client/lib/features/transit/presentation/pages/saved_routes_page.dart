import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:lucide_icons/lucide_icons.dart';

import 'package:go_router/go_router.dart';

import 'package:client/core/theme/app_colors.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/presentation/providers/journey_planner_providers.dart';

// ─── Model ────────────────────────────────────────────────────────────────────

class SavedPlace {
  final String id;
  final String label;
  final String iconKey;
  final String name;
  final String displayName;
  final double lat;
  final double lon;

  const SavedPlace({
    required this.id,
    required this.label,
    required this.iconKey,
    required this.name,
    required this.displayName,
    required this.lat,
    required this.lon,
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'label': label,
        'iconKey': iconKey,
        'name': name,
        'displayName': displayName,
        'lat': lat,
        'lon': lon,
      };

  factory SavedPlace.fromJson(Map<String, dynamic> j) => SavedPlace(
        id: j['id'] as String,
        label: j['label'] as String,
        iconKey: j['iconKey'] as String,
        name: j['name'] as String,
        displayName: j['displayName'] as String,
        lat: (j['lat'] as num).toDouble(),
        lon: (j['lon'] as num).toDouble(),
      );

  IconData get icon {
    switch (iconKey) {
      case 'home':
        return LucideIcons.home;
      case 'work':
        return LucideIcons.briefcase;
      case 'school':
        return LucideIcons.graduationCap;
      case 'heart':
        return LucideIcons.heart;
      case 'star':
        return LucideIcons.star;
      default:
        return LucideIcons.mapPin;
    }
  }
}

// ─── Hive-backed provider ─────────────────────────────────────────────────────

const _kHiveBox = 'saved_places';
const _kHiveKey = 'places_v1';

final class SavedPlacesNotifier extends Notifier<List<SavedPlace>> {
  @override
  List<SavedPlace> build() {
    _load();
    return [];
  }

  Future<void> _load() async {
    final box = await Hive.openBox(_kHiveBox);
    final raw = box.get(_kHiveKey) as String?;
    if (raw != null) {
      state = (jsonDecode(raw) as List<dynamic>)
          .map((e) => SavedPlace.fromJson(e as Map<String, dynamic>))
          .toList();
    }
  }

  Future<void> _persist() async {
    final box = await Hive.openBox(_kHiveBox);
    await box.put(_kHiveKey, jsonEncode(state.map((e) => e.toJson()).toList()));
  }

  Future<void> add(SavedPlace place) async {
    state = [...state, place];
    await _persist();
  }

  Future<void> update(SavedPlace place) async {
    state = [
      for (final p in state)
        if (p.id == place.id) place else p,
    ];
    await _persist();
  }

  Future<void> remove(String id) async {
    state = state.where((p) => p.id != id).toList();
    await _persist();
  }

  bool hasId(String id) => state.any((p) => p.id == id);
}

final savedPlacesProvider =
    NotifierProvider<SavedPlacesNotifier, List<SavedPlace>>(
  SavedPlacesNotifier.new,
);

// ─── Recent Trip model ────────────────────────────────────────────────────────

class RecentTrip {
  final String fromName;
  final String toName;
  final String toDisplayName;
  final double fromLat;
  final double fromLon;
  final double toLat;
  final double toLon;
  final DateTime timestamp;

  const RecentTrip({
    required this.fromName,
    required this.toName,
    required this.toDisplayName,
    required this.fromLat,
    required this.fromLon,
    required this.toLat,
    required this.toLon,
    required this.timestamp,
  });

  Map<String, dynamic> toJson() => {
        'fromName': fromName,
        'toName': toName,
        'toDisplayName': toDisplayName,
        'fromLat': fromLat,
        'fromLon': fromLon,
        'toLat': toLat,
        'toLon': toLon,
        'timestamp': timestamp.millisecondsSinceEpoch,
      };

  factory RecentTrip.fromJson(Map<String, dynamic> j) => RecentTrip(
        fromName: j['fromName'] as String,
        toName: j['toName'] as String,
        toDisplayName: j['toDisplayName'] as String,
        fromLat: (j['fromLat'] as num).toDouble(),
        fromLon: (j['fromLon'] as num).toDouble(),
        toLat: (j['toLat'] as num).toDouble(),
        toLon: (j['toLon'] as num).toDouble(),
        timestamp:
            DateTime.fromMillisecondsSinceEpoch(j['timestamp'] as int),
      );

  String get timeAgo {
    final diff = DateTime.now().difference(timestamp);
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays == 1) return 'Yesterday';
    return '${diff.inDays}d ago';
  }
}

// ─── Recent Trips notifier ────────────────────────────────────────────────────

const _kHiveTripsKey = 'recent_trips_v1';

final class RecentTripsNotifier extends Notifier<List<RecentTrip>> {
  @override
  List<RecentTrip> build() {
    _load();
    return [];
  }

  Future<void> _load() async {
    final box = await Hive.openBox(_kHiveBox);
    final raw = box.get(_kHiveTripsKey) as String?;
    if (raw != null) {
      state = (jsonDecode(raw) as List<dynamic>)
          .map((e) => RecentTrip.fromJson(e as Map<String, dynamic>))
          .toList();
    }
  }

  Future<void> _persist() async {
    final box = await Hive.openBox(_kHiveBox);
    await box.put(
        _kHiveTripsKey, jsonEncode(state.map((e) => e.toJson()).toList()));
  }

  Future<void> addTrip(RecentTrip trip) async {
    // remove duplicate destination before prepending
    final filtered = state
        .where((t) => t.toLat != trip.toLat || t.toLon != trip.toLon)
        .toList();
    final updated = [trip, ...filtered];
    state = updated.length > 10 ? updated.sublist(0, 10) : updated;
    await _persist();
  }

  Future<void> removeTrip(double toLat, double toLon) async {
    state = state.where((t) => t.toLat != toLat || t.toLon != toLon).toList();
    await _persist();
  }
}

final recentTripsProvider =
    NotifierProvider<RecentTripsNotifier, List<RecentTrip>>(
  RecentTripsNotifier.new,
);

// ─── Page ─────────────────────────────────────────────────────────────────────

class SavedRoutesPage extends ConsumerWidget {
  const SavedRoutesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final places = ref.watch(savedPlacesProvider);
    final trips = ref.watch(recentTripsProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          _buildAppBar(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 120),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _SectionLabel(
                    icon: LucideIcons.mapPin,
                    title: 'My Places',
                    subtitle: 'Tap to navigate · Hold to edit',
                  ),
                  const SizedBox(height: 16),
                  _PlacesGrid(places: places),
                  if (places.isEmpty) ...[
                    const SizedBox(height: 32),
                    _EmptyState(),
                  ],
                  if (trips.isNotEmpty) ...[
                    const SizedBox(height: 40),
                    _SectionLabel(
                      icon: LucideIcons.clock,
                      title: 'Recent Trips',
                      subtitle: 'Last ${trips.length} journeys',
                    ),
                    const SizedBox(height: 16),
                    _RecentTripsList(trips: trips),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  SliverAppBar _buildAppBar(BuildContext context) {
    return SliverAppBar(
      expandedHeight: 160,
      backgroundColor: AppColors.background,
      elevation: 0,
      pinned: true,
      flexibleSpace: FlexibleSpaceBar(
        centerTitle: false,
        titlePadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        title: Text(
          'Saved Places',
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                fontWeight: FontWeight.bold,
                color: AppColors.onSurface,
              ),
        ),
        background: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                AppColors.primary.withValues(alpha: 0.07),
                AppColors.background,
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ─── Section label ────────────────────────────────────────────────────────────

class _SectionLabel extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;

  const _SectionLabel({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: AppColors.primary.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, size: 17, color: AppColors.primary),
        ),
        const SizedBox(width: 12),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: AppColors.onSurface,
              ),
            ),
            Text(
              subtitle,
              style: const TextStyle(
                fontSize: 11,
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

// ─── Places grid ──────────────────────────────────────────────────────────────

class _PlacesGrid extends ConsumerWidget {
  final List<SavedPlace> places;

  const _PlacesGrid({required this.places});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final presets = [
      ('home', 'Home', 'home'),
      ('work', 'Work', 'work'),
    ];

    final List<Widget> cards = [];

    for (final (id, label, iconKey) in presets) {
      final saved = places.where((p) => p.id == id).firstOrNull;
      cards.add(
        _PlaceCard(
          id: id,
          label: label,
          iconKey: iconKey,
          savedPlace: saved,
        ).animate().fadeIn(delay: (cards.length * 80).ms).slideY(begin: 0.1, end: 0),
      );
    }

    for (final place in places.where((p) => p.id != 'home' && p.id != 'work')) {
      cards.add(
        _PlaceCard(
          id: place.id,
          label: place.label,
          iconKey: place.iconKey,
          savedPlace: place,
        ).animate().fadeIn(delay: (cards.length * 80).ms).slideY(begin: 0.1, end: 0),
      );
    }

    cards.add(
      _AddCard().animate().fadeIn(delay: (cards.length * 80).ms).slideY(begin: 0.1, end: 0),
    );

    return GridView.count(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisCount: 2,
      crossAxisSpacing: 14,
      mainAxisSpacing: 14,
      childAspectRatio: 1.05,
      children: cards,
    );
  }
}

// ─── Individual place card ────────────────────────────────────────────────────

class _PlaceCard extends ConsumerWidget {
  final String id;
  final String label;
  final String iconKey;
  final SavedPlace? savedPlace;

  const _PlaceCard({
    required this.id,
    required this.label,
    required this.iconKey,
    this.savedPlace,
  });

  IconData _icon() {
    switch (iconKey) {
      case 'home':
        return LucideIcons.home;
      case 'work':
        return LucideIcons.briefcase;
      case 'school':
        return LucideIcons.graduationCap;
      case 'heart':
        return LucideIcons.heart;
      case 'star':
        return LucideIcons.star;
      default:
        return LucideIcons.mapPin;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isSet = savedPlace != null;

    return GestureDetector(
      onTap: () => isSet ? _onTap(context) : _openPicker(context, ref),
      onLongPress: () => isSet ? _onLongPress(context, ref) : null,
      child: AnimatedContainer(
        duration: 250.ms,
        decoration: BoxDecoration(
          color: isSet ? AppColors.surface : AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isSet
                ? AppColors.primary.withValues(alpha: 0.2)
                : AppColors.outlineVariant,
            width: 1.5,
          ),
          boxShadow: isSet
              ? [
                  BoxShadow(
                    color: AppColors.primary.withValues(alpha: 0.08),
                    blurRadius: 16,
                    offset: const Offset(0, 4),
                  ),
                ]
              : null,
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: isSet
                          ? AppColors.primary.withValues(alpha: 0.12)
                          : AppColors.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      _icon(),
                      color: isSet ? AppColors.primary : AppColors.onSurfaceVariant,
                      size: 20,
                    ),
                  ),
                  if (isSet)
                    Container(
                      width: 22,
                      height: 22,
                      decoration: BoxDecoration(
                        color: AppColors.primary.withValues(alpha: 0.1),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        LucideIcons.check,
                        size: 12,
                        color: AppColors.primary,
                      ),
                    ),
                ],
              ),
              const Spacer(),
              Text(
                label,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: isSet ? AppColors.onSurface : AppColors.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                isSet ? savedPlace!.name : 'Tap to set',
                style: TextStyle(
                  fontSize: 11,
                  color: isSet
                      ? AppColors.onSurfaceVariant
                      : AppColors.outline,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _onTap(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => _PlaceActionSheet(place: savedPlace!),
    );
  }

  void _onLongPress(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _PlaceEditSheet(
        place: savedPlace!,
        onSave: (updated) {
          ref.read(savedPlacesProvider.notifier).update(updated);
          Navigator.pop(ctx);
        },
        onDelete: () {
          ref.read(savedPlacesProvider.notifier).remove(savedPlace!.id);
          Navigator.pop(ctx);
        },
      ),
    );
  }

  Future<void> _openPicker(BuildContext context, WidgetRef ref) async {
    final result = await showModalBottomSheet<OsmPlace>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _LocationPickerSheet(
        hint: 'Search for $label location…',
      ),
    );
    if (result == null) return;

    final place = SavedPlace(
      id: id,
      label: label,
      iconKey: iconKey,
      name: result.name,
      displayName: result.displayName,
      lat: result.lat,
      lon: result.lon,
    );

    final notifier = ref.read(savedPlacesProvider.notifier);
    if (notifier.hasId(id)) {
      notifier.update(place);
    } else {
      notifier.add(place);
    }
  }
}

// ─── Add card ─────────────────────────────────────────────────────────────────

class _AddCard extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return GestureDetector(
      onTap: () => _openAddSheet(context, ref),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.transparent,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: AppColors.outlineVariant,
            width: 1.5,
            style: BorderStyle.solid,
          ),
        ),
        child: const Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(LucideIcons.plus, color: AppColors.onSurfaceVariant, size: 28),
            SizedBox(height: 10),
            Text(
              'Add Place',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openAddSheet(BuildContext context, WidgetRef ref) async {
    final result = await showModalBottomSheet<_NewPlaceResult>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const _AddPlaceSheet(),
    );
    if (result == null) return;

    final place = SavedPlace(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      label: result.label,
      iconKey: result.iconKey,
      name: result.place.name,
      displayName: result.place.displayName,
      lat: result.place.lat,
      lon: result.place.lon,
    );
    ref.read(savedPlacesProvider.notifier).add(place);
  }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

class _EmptyState extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        children: [
          Container(
            width: 72,
            height: 72,
            decoration: BoxDecoration(
              color: AppColors.primary.withValues(alpha: 0.08),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              LucideIcons.mapPin,
              size: 32,
              color: AppColors.primary,
            ),
          ),
          const SizedBox(height: 20),
          const Text(
            'No saved places yet',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Set your Home and Work, or\nadd any place you visit often.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 13,
              color: AppColors.onSurfaceVariant,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Recent trips list ────────────────────────────────────────────────────────

class _RecentTripsList extends ConsumerWidget {
  final List<RecentTrip> trips;
  const _RecentTripsList({required this.trips});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppColors.outlineVariant),
      ),
      child: Column(
        children: [
          for (int i = 0; i < trips.length; i++) ...[
            if (i > 0)
              const Divider(
                  height: 1, indent: 64, endIndent: 16, color: AppColors.outlineVariant),
            _RecentTripTile(
              trip: trips[i],
              isFirst: i == 0,
              isLast: i == trips.length - 1,
            ),
          ],
        ],
      ),
    ).animate().fadeIn(delay: 300.ms);
  }
}

class _RecentTripTile extends ConsumerWidget {
  final RecentTrip trip;
  final bool isFirst;
  final bool isLast;

  const _RecentTripTile({
    required this.trip,
    this.isFirst = false,
    this.isLast = false,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return InkWell(
      borderRadius: BorderRadius.vertical(
        top: isFirst ? const Radius.circular(20) : Radius.zero,
        bottom: isLast ? const Radius.circular(20) : Radius.zero,
      ),
      onTap: () => _onTap(context, ref),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
        child: Row(
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.08),
                shape: BoxShape.circle,
              ),
              child: const Icon(LucideIcons.clock,
                  size: 16, color: AppColors.primary),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          trip.fromName,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w500,
                            color: AppColors.onSurfaceVariant,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const Padding(
                        padding: EdgeInsets.symmetric(horizontal: 6),
                        child: Icon(LucideIcons.arrowRight,
                            size: 12, color: AppColors.onSurfaceVariant),
                      ),
                      Flexible(
                        child: Text(
                          trip.toName,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: AppColors.onSurface,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 2),
                  Text(
                    trip.timeAgo,
                    style: const TextStyle(
                        fontSize: 11, color: AppColors.onSurfaceVariant),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            const Icon(LucideIcons.chevronRight,
                size: 15, color: AppColors.outlineVariant),
          ],
        ),
      ),
    );
  }

  void _onTap(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => _RecentTripActionSheet(trip: trip),
    );
  }
}

class _RecentTripActionSheet extends ConsumerWidget {
  final RecentTrip trip;
  const _RecentTripActionSheet({required this.trip});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      padding: EdgeInsets.fromLTRB(20, 0, 20, MediaQuery.of(context).padding.bottom + 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _SheetHandle(),
          Row(
            children: [
              Container(
                width: 46,
                height: 46,
                decoration: BoxDecoration(
                  color: AppColors.primary.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(13),
                ),
                child: const Icon(LucideIcons.arrowRightLeft, color: AppColors.primary, size: 20),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${trip.fromName} → ${trip.toName}',
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface,
                      ),
                      maxLines: 2,
                    ),
                    Text(
                      trip.timeAgo,
                      style: const TextStyle(
                          fontSize: 12, color: AppColors.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: () {
                Navigator.pop(context);
              },
              icon: const Icon(LucideIcons.navigation2, size: 16),
              label: const Text('Plan this trip again'),
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.primary,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
            ),
          ),
          const SizedBox(height: 10),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () {
                ref.read(recentTripsProvider.notifier).removeTrip(
                    trip.toLat, trip.toLon);
                Navigator.pop(context);
              },
              icon: const Icon(LucideIcons.trash2, size: 16, color: AppColors.error),
              label: const Text('Remove from history',
                  style: TextStyle(color: AppColors.error)),
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 14),
                side: const BorderSide(color: AppColors.outlineVariant),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Place action sheet (tap an existing place) ───────────────────────────────

class _PlaceActionSheet extends ConsumerStatefulWidget {
  final SavedPlace place;
  const _PlaceActionSheet({required this.place});

  @override
  ConsumerState<_PlaceActionSheet> createState() => _PlaceActionSheetState();
}

class _PlaceActionSheetState extends ConsumerState<_PlaceActionSheet> {
  bool _loading = false;
  String? _error;

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.of(context).padding.bottom;
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      padding: EdgeInsets.fromLTRB(20, 0, 20, bottom + 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _SheetHandle(),
          const SizedBox(height: 4),
          // ── Place info ──────────────────────────────────────────────────────
          Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: AppColors.primary.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(widget.place.icon, color: AppColors.primary, size: 22),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.place.label,
                      style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface,
                      ),
                    ),
                    Text(
                      widget.place.name,
                      style: const TextStyle(fontSize: 13, color: AppColors.onSurfaceVariant),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          // ── GPS origin hint ─────────────────────────────────────────────────
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppColors.outlineVariant),
            ),
            child: Row(
              children: [
                const Icon(LucideIcons.navigation2, size: 14, color: AppColors.primary),
                const SizedBox(width: 8),
                const Text(
                  'From: My current GPS location',
                  style: TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                ),
              ],
            ),
          ),
          const SizedBox(height: 6),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: AppColors.primary.withValues(alpha: 0.06),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppColors.primary.withValues(alpha: 0.2)),
            ),
            child: Row(
              children: [
                const Icon(LucideIcons.mapPin, size: 14, color: AppColors.primary),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'To: ${widget.place.displayName}',
                    style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
          // ── Error ───────────────────────────────────────────────────────────
          if (_error != null) ...[
            const SizedBox(height: 10),
            Row(
              children: [
                const Icon(LucideIcons.alertCircle, size: 14, color: AppColors.error),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    _error!,
                    style: const TextStyle(fontSize: 12, color: AppColors.error),
                  ),
                ),
              ],
            ),
          ],
          const SizedBox(height: 20),
          // ── Navigate button ─────────────────────────────────────────────────
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: _loading ? null : _navigateHere,
              icon: _loading
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(LucideIcons.navigation2, size: 16),
              label: Text(_loading ? 'Getting location…' : 'Navigate here'),
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.primary,
                padding: const EdgeInsets.symmetric(vertical: 15),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _navigateHere() async {
    setState(() { _loading = true; _error = null; });
    try {
      // 1. Check GPS service
      final serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        setState(() => _error = 'GPS is off. Please enable location services.');
        return;
      }

      // 2. Check / request permission
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        setState(() => _error = 'Location permission denied. Enable in device settings.');
        return;
      }

      // 3. Get position
      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 15),
        ),
      );

      // 4. Set providers exactly like the home page does
      final dest = OsmPlace(
        name: widget.place.name,
        displayName: widget.place.displayName,
        lat: widget.place.lat,
        lon: widget.place.lon,
      );
      ref.read(selectedOriginProvider.notifier).select(null);
      ref.read(selectedDestinationProvider.notifier).select(dest);

      // 5. Plan the journey
      await ref.read(journeyPlanNotifierProvider.notifier).plan(
        origin: [position.longitude, position.latitude],
        destination: [widget.place.lon, widget.place.lat],
      );

      // 6. Save to recent trips
      ref.read(recentTripsProvider.notifier).addTrip(RecentTrip(
        fromName: 'My Location',
        toName: widget.place.name,
        toDisplayName: widget.place.displayName,
        fromLat: position.latitude,
        fromLon: position.longitude,
        toLat: widget.place.lat,
        toLon: widget.place.lon,
        timestamp: DateTime.now(),
      ));

      // 7. Navigate to search results (same as normal flow)
      if (mounted) {
        Navigator.pop(context);
        context.push('/search');
      }
    } on LocationServiceDisabledException {
      setState(() => _error = 'GPS is off. Please enable location services.');
    } catch (_) {
      setState(() => _error = 'Could not get your location. Try again.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }
}

// ─── Place edit sheet (long-press) ────────────────────────────────────────────

class _PlaceEditSheet extends StatelessWidget {
  final SavedPlace place;
  final ValueChanged<SavedPlace> onSave;
  final VoidCallback onDelete;

  const _PlaceEditSheet({
    required this.place,
    required this.onSave,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      padding: EdgeInsets.fromLTRB(20, 0, 20, MediaQuery.of(context).padding.bottom + 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _SheetHandle(),
          const SizedBox(height: 4),
          const Text(
            'Edit Place',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: 24),
          _ActionTile(
            icon: LucideIcons.pencil,
            label: 'Change location',
            subtitle: place.name,
            onTap: () async {
              Navigator.pop(context);
              final ctx = context;
              final result = await showModalBottomSheet<OsmPlace>(
                context: ctx,
                isScrollControlled: true,
                backgroundColor: Colors.transparent,
                builder: (_) => _LocationPickerSheet(
                  hint: 'Search new location…',
                ),
              );
              if (result == null) return;
              onSave(SavedPlace(
                id: place.id,
                label: place.label,
                iconKey: place.iconKey,
                name: result.name,
                displayName: result.displayName,
                lat: result.lat,
                lon: result.lon,
              ));
            },
          ),
          const SizedBox(height: 8),
          _ActionTile(
            icon: LucideIcons.trash2,
            label: 'Remove this place',
            subtitle: 'Cannot be undone',
            color: AppColors.error,
            onTap: onDelete,
          ),
        ],
      ),
    );
  }
}

class _ActionTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String subtitle;
  final Color? color;
  final VoidCallback onTap;

  const _ActionTile({
    required this.icon,
    required this.label,
    required this.subtitle,
    this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.onSurface;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: c.withValues(alpha: 0.06),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: c.withValues(alpha: 0.12)),
        ),
        child: Row(
          children: [
            Icon(icon, color: c, size: 20),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: c,
                    ),
                  ),
                  Text(
                    subtitle,
                    style: TextStyle(
                      fontSize: 11,
                      color: c.withValues(alpha: 0.6),
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            Icon(LucideIcons.chevronRight, size: 16, color: c.withValues(alpha: 0.4)),
          ],
        ),
      ),
    );
  }
}

// ─── Add place sheet (new custom place) ──────────────────────────────────────

class _NewPlaceResult {
  final OsmPlace place;
  final String label;
  final String iconKey;
  const _NewPlaceResult({
    required this.place,
    required this.label,
    required this.iconKey,
  });
}

class _AddPlaceSheet extends StatefulWidget {
  const _AddPlaceSheet();

  @override
  State<_AddPlaceSheet> createState() => _AddPlaceSheetState();
}

class _AddPlaceSheetState extends State<_AddPlaceSheet> {
  String _label = '';
  String _iconKey = 'map-pin';
  OsmPlace? _pickedPlace;
  bool _labelEdited = false;

  static const _icons = [
    ('map-pin', LucideIcons.mapPin, 'Place'),
    ('star', LucideIcons.star, 'Favourite'),
    ('school', LucideIcons.graduationCap, 'School'),
    ('heart', LucideIcons.heart, 'Care'),
  ];

  @override
  Widget build(BuildContext context) {
    final canSave = _pickedPlace != null && _label.trim().isNotEmpty;

    return Container(
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom +
            MediaQuery.of(context).padding.bottom +
            32,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _SheetHandle(),
          const Text(
            'Add a Place',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: 20),

          // Name input
          const Text(
            'LABEL',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            onChanged: (v) => setState(() {
              _label = v;
              _labelEdited = true;
            }),
            decoration: InputDecoration(
              hintText: 'e.g. Gym, Mosque, Market…',
              hintStyle: const TextStyle(color: AppColors.onSurfaceVariant),
              filled: true,
              fillColor: AppColors.surfaceContainerLow,
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: const BorderSide(color: AppColors.outlineVariant),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: const BorderSide(color: AppColors.outlineVariant),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide:
                    const BorderSide(color: AppColors.primary, width: 1.5),
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Icon picker
          const Text(
            'ICON',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: 10),
          Row(
            children: _icons.map((e) {
              final (key, icon, tip) = e;
              final selected = _iconKey == key;
              return Expanded(
                child: GestureDetector(
                  onTap: () => setState(() => _iconKey = key),
                  child: AnimatedContainer(
                    duration: 150.ms,
                    margin: const EdgeInsets.only(right: 8),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    decoration: BoxDecoration(
                      color: selected
                          ? AppColors.primary.withValues(alpha: 0.12)
                          : AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: selected
                            ? AppColors.primary
                            : AppColors.outlineVariant,
                        width: selected ? 1.5 : 1,
                      ),
                    ),
                    child: Column(
                      children: [
                        Icon(icon,
                            size: 20,
                            color: selected
                                ? AppColors.primary
                                : AppColors.onSurfaceVariant),
                        const SizedBox(height: 4),
                        Text(
                          tip,
                          style: TextStyle(
                            fontSize: 9,
                            color: selected
                                ? AppColors.primary
                                : AppColors.onSurfaceVariant,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 20),

          // Location picker
          const Text(
            'LOCATION',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: 8),
          GestureDetector(
            onTap: () => _pickLocation(context),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: _pickedPlace != null
                      ? AppColors.primary.withValues(alpha: 0.5)
                      : AppColors.outlineVariant,
                ),
              ),
              child: Row(
                children: [
                  Icon(
                    _pickedPlace != null
                        ? LucideIcons.mapPin
                        : LucideIcons.search,
                    size: 18,
                    color: _pickedPlace != null
                        ? AppColors.primary
                        : AppColors.onSurfaceVariant,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      _pickedPlace?.name ?? 'Search or use GPS location',
                      style: TextStyle(
                        fontSize: 14,
                        color: _pickedPlace != null
                            ? AppColors.onSurface
                            : AppColors.onSurfaceVariant,
                        fontWeight: _pickedPlace != null
                            ? FontWeight.w600
                            : FontWeight.normal,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  Icon(
                    LucideIcons.chevronRight,
                    size: 16,
                    color: AppColors.outlineVariant,
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 28),

          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: canSave
                  ? () => Navigator.pop(
                        context,
                        _NewPlaceResult(
                          place: _pickedPlace!,
                          label: _label.trim(),
                          iconKey: _iconKey,
                        ),
                      )
                  : null,
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.primary,
                disabledBackgroundColor: AppColors.surfaceContainerHigh,
                padding: const EdgeInsets.symmetric(vertical: 15),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
              child: Text(
                canSave ? 'Save Place' : 'Fill all fields',
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                  color: canSave ? Colors.white : AppColors.onSurfaceVariant,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _pickLocation(BuildContext context) async {
    final result = await showModalBottomSheet<OsmPlace>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _LocationPickerSheet(hint: 'Search a place in Rwanda…'),
    );
    if (result == null) return;
    setState(() {
      _pickedPlace = result;
      if (!_labelEdited) _label = result.name;
    });
  }
}

// ─── Location picker sheet (search + GPS) ────────────────────────────────────

class _LocationPickerSheet extends ConsumerStatefulWidget {
  final String hint;
  const _LocationPickerSheet({required this.hint});

  @override
  ConsumerState<_LocationPickerSheet> createState() =>
      _LocationPickerSheetState();
}

class _LocationPickerSheetState extends ConsumerState<_LocationPickerSheet> {
  final _controller = TextEditingController();
  final _focus = FocusNode();
  String _query = '';
  bool _loadingGps = false;

  @override
  void initState() {
    super.initState();
    _controller.addListener(() {
      final t = _controller.text;
      if (t != _query) setState(() => _query = t);
    });
    WidgetsBinding.instance.addPostFrameCallback((_) => _focus.requestFocus());
  }

  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final q = _query.trim();
    final results = q.length >= 2 ? ref.watch(osmSearchProvider(q)) : null;

    return Container(
      height: MediaQuery.of(context).size.height * 0.92,
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: Column(
        children: [
          _SheetHandle(),

          // Search bar row
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 4, 12, 0),
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
                    padding:
                        const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppColors.outlineVariant),
                    ),
                    child: Row(
                      children: [
                        const Icon(LucideIcons.search,
                            size: 16, color: AppColors.onSurfaceVariant),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: _controller,
                            focusNode: _focus,
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
                                size: 16, color: AppColors.onSurfaceVariant),
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

          // GPS tile
          _GpsTile(
            isLoading: _loadingGps,
            onTap: _useCurrentLocation,
          ),

          const Divider(height: 1, color: AppColors.outlineVariant),

          Expanded(child: _buildResults(results)),
        ],
      ),
    );
  }

  Widget _buildResults(AsyncValue<List<OsmPlace>>? results) {
    if (results == null) {
      return _buildSuggestions();
    }

    return results.when(
      loading: () => const Center(
        child: Padding(
          padding: EdgeInsets.all(40),
          child: CircularProgressIndicator(
              color: AppColors.primary, strokeWidth: 2),
        ),
      ),
      error: (_, _) => const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(LucideIcons.wifiOff, size: 40, color: AppColors.onSurfaceVariant),
              SizedBox(height: 12),
              Text('Search unavailable',
                  style: TextStyle(
                      fontWeight: FontWeight.bold, color: AppColors.onSurface)),
              SizedBox(height: 4),
              Text('Check your internet connection',
                  style: TextStyle(
                      fontSize: 12, color: AppColors.onSurfaceVariant)),
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
                      size: 40, color: AppColors.onSurfaceVariant),
                  const SizedBox(height: 12),
                  Text(
                    'No results for "$_query"',
                    style: const TextStyle(
                        fontWeight: FontWeight.bold, color: AppColors.onSurface),
                  ),
                  const SizedBox(height: 4),
                  const Text(
                    'Try a nearby landmark',
                    style: TextStyle(
                        fontSize: 12, color: AppColors.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.symmetric(vertical: 8),
          itemCount: places.length,
          separatorBuilder: (_, _) => const Divider(
              height: 1, indent: 64, endIndent: 16, color: AppColors.outlineVariant),
          itemBuilder: (_, i) => _ResultTile(
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
      ('Gisozi', LucideIcons.mapPin),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(20, 14, 20, 8),
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
            separatorBuilder: (_, _) => const Divider(
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
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
                  child: Row(
                    children: [
                      Container(
                        width: 36,
                        height: 36,
                        decoration: BoxDecoration(
                          color: AppColors.primary.withValues(alpha: 0.08),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(icon, size: 16, color: AppColors.primary),
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
                          size: 14, color: AppColors.outlineVariant),
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

  Future<void> _useCurrentLocation() async {
    setState(() => _loadingGps = true);
    OsmPlace? result;
    try {
      final serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        _snack('GPS is off. Please enable location services.');
        return;
      }

      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        _snack('Location permission denied. Enable in device settings.');
        return;
      }

      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 15),
        ),
      );

      result = await _reverseGeocode(position.latitude, position.longitude);
    } on LocationServiceDisabledException {
      _snack('GPS is off. Please enable location services.');
    } catch (_) {
      _snack('Could not get GPS location. Try again.');
    } finally {
      if (mounted) setState(() => _loadingGps = false);
    }
    if (result != null && mounted) Navigator.pop(context, result);
  }

  Future<OsmPlace> _reverseGeocode(double lat, double lon) async {
    try {
      final response = await Dio(BaseOptions(
        baseUrl: 'https://nominatim.openstreetmap.org',
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 10),
        headers: {
          'User-Agent': 'IOTS-Rwanda-Transit/1.0 (lambertbayiringire@gmail.com)',
          'Accept-Language': 'en',
        },
      )).get('/reverse', queryParameters: {
        'lat': lat,
        'lon': lon,
        'format': 'json',
        'addressdetails': 1,
      });

      final data = response.data as Map<String, dynamic>;
      final displayName = data['display_name'] as String? ?? 'Current Location';
      final address = data['address'] as Map<String, dynamic>?;
      final shortName = address?['road'] as String? ??
          address?['neighbourhood'] as String? ??
          address?['suburb'] as String? ??
          'Current Location';

      return OsmPlace(
        name: shortName,
        displayName: displayName,
        lat: lat,
        lon: lon,
      );
    } catch (_) {
      return OsmPlace(
        name: 'Current Location',
        displayName: 'GPS: ${lat.toStringAsFixed(5)}, ${lon.toStringAsFixed(5)}',
        lat: lat,
        lon: lon,
      );
    }
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(msg),
      behavior: SnackBarBehavior.floating,
      backgroundColor: AppColors.onSurface,
    ));
  }
}

// ─── GPS tile ──────────────────────────────────────────────────────────────────

class _GpsTile extends StatelessWidget {
  final bool isLoading;
  final VoidCallback onTap;

  const _GpsTile({required this.isLoading, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: isLoading ? null : onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: isLoading
                  ? const Padding(
                      padding: EdgeInsets.all(9),
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: AppColors.primary),
                    )
                  : const Icon(LucideIcons.navigation2,
                      size: 16, color: AppColors.primary),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    isLoading ? 'Getting your location…' : 'Use current location',
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: AppColors.primary,
                    ),
                  ),
                  const Text(
                    'GPS will find you automatically',
                    style: TextStyle(
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
    );
  }
}

// ─── Result tile ──────────────────────────────────────────────────────────────

class _ResultTile extends StatelessWidget {
  final OsmPlace place;
  final VoidCallback onTap;

  const _ResultTile({required this.place, required this.onTap});

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
              child: const Icon(LucideIcons.mapPin, size: 16, color: AppColors.primary),
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
                        fontSize: 11, color: AppColors.onSurfaceVariant),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            const Icon(LucideIcons.cornerDownLeft,
                size: 14, color: AppColors.outlineVariant),
          ],
        ),
      ),
    );
  }
}

// ─── Sheet handle ─────────────────────────────────────────────────────────────

class _SheetHandle extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 12),
        width: 36,
        height: 4,
        decoration: BoxDecoration(
          color: AppColors.outlineVariant,
          borderRadius: BorderRadius.circular(2),
        ),
      ),
    );
  }
}
