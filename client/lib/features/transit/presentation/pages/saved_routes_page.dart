import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/app_widgets.dart';

class SavedRoutesPage extends StatelessWidget {
  const SavedRoutesPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: CustomScrollView(
        slivers: [
          _buildSliverHeader(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 32, 24, 120),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SectionHeader(title: 'Quick Access Locations', subtitle: 'Places you visit often'),
                  const SizedBox(height: 16),
                  _buildSavedLocations(),
                  const SizedBox(height: 40),
                  const SectionHeader(title: 'Pinned Bus Routes', subtitle: 'Track your frequent lines'),
                  const SizedBox(height: 16),
                  _buildPinnedRoutes(),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSliverHeader(BuildContext context) {
    return SliverAppBar(
      expandedHeight: 180,
      backgroundColor: AppColors.background,
      elevation: 0,
      pinned: true,
      flexibleSpace: FlexibleSpaceBar(
        centerTitle: false,
        titlePadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        title: Text(
          'Saved',
          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
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
                AppColors.primary.withOpacity(0.05),
                AppColors.background,
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildSavedLocations() {
    return Row(
      children: [
        _LocationCard(icon: LucideIcons.home, label: 'Home', color: AppColors.primary),
        const SizedBox(width: 16),
        _LocationCard(icon: LucideIcons.briefcase, label: 'Work', color: AppColors.secondary),
        const SizedBox(width: 16),
        _LocationCard(icon: LucideIcons.plus, label: 'Add', color: AppColors.outline, isOutline: true),
      ],
    ).animate().fadeIn(delay: 200.ms).slideX(begin: 0.1, end: 0);
  }

  Widget _buildPinnedRoutes() {
    return Column(
      children: [
        _SavedRouteItem(
          title: 'Kigali CBD → Remera',
          route: 'Line 402',
          eta: '5 mins',
          color: AppColors.primary,
        ),
        _SavedRouteItem(
          title: 'Downtown → Nyabugogo',
          route: 'Line 102',
          eta: '12 mins',
          color: AppColors.secondary,
        ),
      ],
    ).animate().fadeIn(delay: 400.ms);
  }
}

class _LocationCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final bool isOutline;

  const _LocationCard({required this.icon, required this.label, required this.color, this.isOutline = false});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 20),
        decoration: BoxDecoration(
          color: isOutline ? Colors.transparent : color.withOpacity(0.1),
          borderRadius: BorderRadius.circular(24),
          border: isOutline ? Border.all(color: AppColors.outlineVariant, width: 2, style: BorderStyle.solid) : null,
        ),
        child: Column(
          children: [
            Icon(icon, color: color, size: 24),
            const SizedBox(height: 12),
            Text(label, style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: color)),
          ],
        ),
      ),
    );
  }
}

class _SavedRouteItem extends StatelessWidget {
  final String title;
  final String route;
  final String eta;
  final Color color;

  const _SavedRouteItem({required this.title, required this.route, required this.eta, required this.color});

  @override
  Widget build(BuildContext context) {
    return TonalCard(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(20),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: color.withOpacity(0.1),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(LucideIcons.bus, color: color, size: 20),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                Text(route, style: const TextStyle(color: AppColors.onSurfaceVariant, fontSize: 13)),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(eta, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
          ),
        ],
      ),
    );
  }
}
