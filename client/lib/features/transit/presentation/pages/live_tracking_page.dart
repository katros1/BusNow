import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';
import 'package:lucide_icons/lucide_icons.dart';

import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/glass_container.dart';
import 'package:client/features/journey_planner/presentation/providers/journey_planner_providers.dart';

// ─── Snapshot model ───────────────────────────────────────────────────────────

class _VehicleSnap {
  final String plateNumber;
  final double? lat;
  final double? lon;
  final double? speedKmh;
  final double? headingDeg;
  final bool gpsValid;
  final String? routeName;
  final String? currentStopName;
  final String? nextStopName;
  final int passengersOnBoard;
  final bool hasTrip;

  const _VehicleSnap({
    required this.plateNumber,
    this.lat,
    this.lon,
    this.speedKmh,
    this.headingDeg,
    this.gpsValid = false,
    this.routeName,
    this.currentStopName,
    this.nextStopName,
    this.passengersOnBoard = 0,
    this.hasTrip = false,
  });

  factory _VehicleSnap.fromJson(Map<String, dynamic> j) => _VehicleSnap(
        plateNumber: j['plateNumber'] as String? ?? '',
        lat: (j['latitude'] as num?)?.toDouble(),
        lon: (j['longitude'] as num?)?.toDouble(),
        speedKmh: (j['speedKmh'] as num?)?.toDouble(),
        headingDeg: (j['headingDeg'] as num?)?.toDouble(),
        gpsValid: j['gpsValid'] as bool? ?? false,
        routeName: j['routeName'] as String?,
        currentStopName: j['currentStopName'] as String?,
        nextStopName: j['nextStopName'] as String?,
        passengersOnBoard: j['passengersOnBoard'] as int? ?? 0,
        hasTrip: j['tripId'] != null,
      );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

class LiveTrackingPage extends ConsumerStatefulWidget {
  /// Optional plate number to subscribe to on open.
  final String? plate;

  const LiveTrackingPage({super.key, this.plate});

  @override
  ConsumerState<LiveTrackingPage> createState() => _LiveTrackingPageState();
}

class _LiveTrackingPageState extends ConsumerState<LiveTrackingPage> {
  WebSocket? _ws;
  StreamSubscription? _sub;
  Timer? _pingTimer;
  Timer? _retryTimer;

  _VehicleSnap? _snap;
  bool _connected = false;
  bool _followBus = true;
  int _retryDelay = 1;

  final _mapController = MapController();

  @override
  void initState() {
    super.initState();
    _connect();
  }

  @override
  void dispose() {
    _pingTimer?.cancel();
    _retryTimer?.cancel();
    _sub?.cancel();
    _ws?.close();
    _mapController.dispose();
    super.dispose();
  }

  String _wsUrl() {
    final base = ref.read(dioProvider).options.baseUrl; // http://host:port/api/v1
    final host = base
        .replaceFirst(RegExp(r'/api/v1/?$'), '')
        .replaceFirst('http://', 'ws://')
        .replaceFirst('https://', 'wss://');
    return '$host/ws/tracking';
  }

  Future<void> _connect() async {
    _sub?.cancel();
    _ws?.close();
    _pingTimer?.cancel();

    try {
      _ws = await WebSocket.connect(_wsUrl());
      if (!mounted) { _ws?.close(); return; }

      setState(() { _connected = true; _retryDelay = 1; });

      if (widget.plate != null) {
        _ws!.add(jsonEncode({'type': 'subscribe', 'plates': [widget.plate]}));
      }

      _pingTimer = Timer.periodic(const Duration(seconds: 25), (_) {
        if (_connected) _ws?.add(jsonEncode({'type': 'ping'}));
      });

      _sub = _ws!.listen(
        _onMessage,
        onDone: _onDisconnect,
        onError: (_) => _onDisconnect(),
        cancelOnError: true,
      );
    } catch (_) {
      _onDisconnect();
    }
  }

  void _onMessage(dynamic data) {
    if (!mounted) return;
    try {
      final msg = jsonDecode(data as String) as Map<String, dynamic>;
      if (msg['type'] == 'snapshot' && msg['data'] != null) {
        final snap = _VehicleSnap.fromJson(msg['data'] as Map<String, dynamic>);
        setState(() => _snap = snap);
        if (_followBus && snap.lat != null && snap.lon != null) {
          _mapController.move(LatLng(snap.lat!, snap.lon!), 15);
        }
      }
    } catch (_) {}
  }

  void _onDisconnect() {
    if (!mounted) return;
    setState(() => _connected = false);
    _pingTimer?.cancel();
    // Exponential back-off, cap at 30 s
    _retryTimer = Timer(Duration(seconds: _retryDelay), () {
      if (mounted) {
        _retryDelay = math.min(_retryDelay * 2, 30);
        _connect();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final snap = _snap;
    final busPos = (snap?.lat != null && snap?.lon != null)
        ? LatLng(snap!.lat!, snap.lon!)
        : null;

    return Scaffold(
      body: Stack(
        children: [
          // ── Map ──────────────────────────────────────────────────────────
          FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: busPos ?? const LatLng(-1.9441, 29.8739),
              initialZoom: 15.0,
              onPositionChanged: (_, hasGesture) {
                if (hasGesture && _followBus) {
                  setState(() => _followBus = false);
                }
              },
            ),
            children: [
              TileLayer(
                urlTemplate:
                    'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                userAgentPackageName: 'com.busnow.client',
              ),
              if (busPos != null)
                MarkerLayer(markers: [
                  Marker(
                    point: busPos,
                    width: 56,
                    height: 56,
                    child: _BusMarker(
                      heading: snap?.headingDeg,
                      active: snap?.hasTrip ?? false,
                    ),
                  ),
                ]),
            ],
          ),

          // ── Re-centre button ──────────────────────────────────────────────
          if (!_followBus && busPos != null)
            Positioned(
              right: 16,
              bottom: 210,
              child: FloatingActionButton.small(
                backgroundColor: Colors.white,
                foregroundColor: AppColors.primary,
                onPressed: () {
                  setState(() => _followBus = true);
                  _mapController.move(busPos, 15);
                },
                child: const Icon(LucideIcons.locate, size: 18),
              ),
            ),

          // ── Top bar ───────────────────────────────────────────────────────
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  GestureDetector(
                    onTap: () => Navigator.pop(context),
                    child: const GlassContainer(
                      padding: EdgeInsets.all(12),
                      child:
                          Icon(LucideIcons.chevronLeft, color: Colors.white),
                    ),
                  ),
                  GlassContainer(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 14, vertical: 8),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 7,
                          height: 7,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: _connected
                                ? Colors.greenAccent
                                : Colors.orange,
                          ),
                        ),
                        const SizedBox(width: 7),
                        Text(
                          _connected ? 'Live' : 'Reconnecting…',
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),

          // ── Info card ─────────────────────────────────────────────────────
          Positioned(
            bottom: 40,
            left: 16,
            right: 16,
            child: GlassContainer(
              padding: const EdgeInsets.all(20),
              child: snap == null
                  ? const _WaitingForData()
                  : _SnapCard(snap: snap),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Pulsing bus icon ─────────────────────────────────────────────────────────

class _BusMarker extends StatefulWidget {
  final double? heading;
  final bool active;
  const _BusMarker({this.heading, this.active = false});

  @override
  State<_BusMarker> createState() => _BusMarkerState();
}

class _BusMarkerState extends State<_BusMarker>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _pulse;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 1800))
      ..repeat();
    _pulse = Tween<double>(begin: 0.35, end: 1.0)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOut));
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final color = widget.active ? AppColors.primary : AppColors.primaryLight;
    final headingRad =
        widget.heading != null ? widget.heading! * math.pi / 180 : null;

    return AnimatedBuilder(
      animation: _pulse,
      builder: (context, child) => Stack(
        alignment: Alignment.center,
        children: [
          // Pulse ring
          Container(
            width: 54 * _pulse.value,
            height: 54 * _pulse.value,
            decoration: BoxDecoration(
              color: color.withValues(alpha: (1 - _pulse.value) * 0.5),
              shape: BoxShape.circle,
            ),
          ),
          // Heading arrow
          if (headingRad != null)
            Transform.rotate(
              angle: headingRad,
              child: Align(
                alignment: Alignment.topCenter,
                child: Container(
                  width: 5,
                  height: 14,
                  margin: const EdgeInsets.only(bottom: 30),
                  decoration: BoxDecoration(
                    color: color,
                    borderRadius:
                        const BorderRadius.vertical(top: Radius.circular(3)),
                  ),
                ),
              ),
            ),
          // Bus dot
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
              border: Border.all(color: Colors.white, width: 2.5),
              boxShadow: [
                BoxShadow(
                  color: color.withValues(alpha: 0.55),
                  blurRadius: 10,
                  offset: const Offset(0, 3),
                ),
              ],
            ),
            child: const Icon(LucideIcons.bus, color: Colors.white, size: 15),
          ),
        ],
      ),
    );
  }
}

// ─── Info card contents ───────────────────────────────────────────────────────

class _WaitingForData extends StatelessWidget {
  const _WaitingForData();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.symmetric(vertical: 14),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(
                  color: Colors.white, strokeWidth: 2),
            ),
            SizedBox(width: 12),
            Text('Waiting for bus signal…',
                style: TextStyle(color: Colors.white70, fontSize: 13)),
          ],
        ),
      ),
    );
  }
}

class _SnapCard extends StatelessWidget {
  final _VehicleSnap snap;
  const _SnapCard({required this.snap});

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.25),
                shape: BoxShape.circle,
              ),
              child: const Icon(LucideIcons.bus, color: Colors.white),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    snap.routeName ?? 'Unknown Route',
                    style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 16),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(snap.plateNumber,
                      style:
                          const TextStyle(color: Colors.white70, fontSize: 12)),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 18),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _InfoChip(
              icon: LucideIcons.gauge,
              label: 'Speed',
              value: snap.gpsValid && snap.speedKmh != null
                  ? '${snap.speedKmh!.round()} km/h'
                  : '—',
            ),
            _InfoChip(
              icon: LucideIcons.mapPin,
              label: 'Stop',
              value: snap.currentStopName ??
                  (snap.hasTrip ? 'In transit' : 'At terminal'),
            ),
            _InfoChip(
              icon: LucideIcons.users,
              label: 'On board',
              value: '${snap.passengersOnBoard}',
            ),
          ],
        ),
        if (snap.nextStopName != null) ...[
          const SizedBox(height: 10),
          Row(
            children: [
              const Icon(LucideIcons.chevronRight,
                  color: Colors.white54, size: 13),
              const SizedBox(width: 5),
              Expanded(
                child: Text(
                  'Next: ${snap.nextStopName}',
                  style:
                      const TextStyle(color: Colors.white70, fontSize: 12),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }
}

class _InfoChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  const _InfoChip(
      {required this.icon, required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon, color: Colors.white54, size: 16),
        const SizedBox(height: 5),
        Text(label,
            style: const TextStyle(color: Colors.white54, fontSize: 10)),
        Text(value,
            style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
                fontSize: 13)),
      ],
    );
  }
}
