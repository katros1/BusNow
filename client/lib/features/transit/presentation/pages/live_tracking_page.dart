import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/glass_container.dart';

class LiveTrackingPage extends StatelessWidget {
  const LiveTrackingPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          FlutterMap(
            options: const MapOptions(// Kigali, Rwanda
              initialZoom: 15.0,
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                userAgentPackageName: 'com.example.client',
              ),
              PolylineLayer(
                polylines: [
                  Polyline(
                    points: const [
                      LatLng(-1.9441, 30.0619),
                      LatLng(-1.9455, 30.0635),
                      LatLng(-1.9470, 30.0655),
                      LatLng(-1.9541, 30.0719),
                    ],
                    color: AppColors.primary.withOpacity(0.8),
                    strokeWidth: 4.0,
                  ),
                ],
              ),
              MarkerLayer(
                markers: [
                  // Bus Marker
                  const Marker(
                    point: LatLng(-1.9470, 30.0655),
                    width: 60,
                    height: 60,
                    child: _BusMarker(),
                  ),
                  // Destination Marker
                  const Marker(
                    point: LatLng(-1.9541, 30.0719),
                    width: 40,
                    height: 40,
                    child: Icon(LucideIcons.mapPin, color: AppColors.primary, size: 30),
                  ),
                ],
              ),
            ],
          ),
          
          // Pure UI Overlays
          _buildTopBar(context),
          _buildBottomCard(),
        ],
      ),
    );
  }

  Widget _buildTopBar(BuildContext context) {
    return Positioned(
      top: 54,
      left: 20,
      right: 20,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          GestureDetector(
            onTap: () => Navigator.pop(context),
            child: const GlassContainer(
              padding: EdgeInsets.all(12),
              child: Icon(LucideIcons.chevronLeft, color: Colors.white),
            ),
          ),
          const GlassContainer(
            padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                Icon(LucideIcons.radio, color: Colors.greenAccent, size: 16),
                SizedBox(width: 8),
                Text('Live Feedback', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomCard() {
    return Positioned(
      bottom: 40,
      left: 20,
      right: 20,
      child: GlassContainer(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.primary.withOpacity(0.2),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(LucideIcons.bus, color: Colors.white),
                ),
                const SizedBox(width: 16),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Route 402 - Kigali Central',
                        style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 18),
                      ),
                      Text(
                        'Bus Plate: RAE 442 D',
                        style: TextStyle(color: Colors.white70, fontSize: 14),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _buildInfoItem(LucideIcons.clock, 'Arrival', '6 mins'),
                _buildInfoItem(LucideIcons.users, 'Status', 'Half Full'),
                _buildInfoItem(LucideIcons.map, 'Distance', '1.2 km'),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoItem(IconData icon, String label, String value) {
    return Column(
      children: [
        Icon(icon, color: Colors.white54, size: 18),
        const SizedBox(height: 8),
        Text(label, style: const TextStyle(color: Colors.white54, fontSize: 11)),
        Text(value, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14)),
      ],
    );
  }
}

class _BusMarker extends StatelessWidget {
  const _BusMarker();

  @override
  Widget build(BuildContext context) {
    return Stack(
      alignment: Alignment.center,
      children: [
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: AppColors.primary.withOpacity(0.3),
            shape: BoxShape.circle,
          ),
        ),
        Container(
          width: 25,
          height: 25,
          decoration: const BoxDecoration(
            color: AppColors.primary,
            shape: BoxShape.circle,
            boxShadow: [BoxShadow(color: Colors.black26, blurRadius: 4)],
          ),
          child: const Icon(LucideIcons.bus, color: Colors.white, size: 14),
        ),
      ],
    );
  }
}
