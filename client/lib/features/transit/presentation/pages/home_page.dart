import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:go_router/go_router.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/app_widgets.dart';
import 'package:client/core/widgets/glass_container.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final TextEditingController _fromController = TextEditingController(text: 'Current Location');
  final TextEditingController _toController = TextEditingController();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Stack(
        children: [
          _buildRichHeader(context),
          
          SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(height: 100),
                  _buildSearchCard(context),
                  const SizedBox(height: 40),
                  const SectionHeader(title: 'Quick Actions'),
                  const SizedBox(height: 16),
                  _buildQuickActions(context),
                  const SizedBox(height: 32),
                  const SectionHeader(title: 'Saved Routes', subtitle: 'Your frequent commutes'),
                  const SizedBox(height: 16),
                  _buildRecentUpdates(context),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRichHeader(BuildContext context) {
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

  Widget _buildSearchCard(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: AppColors.primary.withOpacity(0.15),
            blurRadius: 40,
            offset: const Offset(0, 20),
          ),
        ],
      ),
      child: Column(
        children: [
          _buildLocationInput(
            controller: _fromController,
            icon: LucideIcons.circleDot,
            label: 'From',
            hint: 'Your current location',
            iconColor: AppColors.primary,
          ),
          const Padding(
            padding: EdgeInsets.only(left: 36),
            child: Divider(height: 32, color: AppColors.surfaceContainerLow),
          ),
          _buildLocationInput(
            controller: _toController,
            icon: LucideIcons.mapPin,
            label: 'To',
            hint: 'Search destination station...',
            iconColor: AppColors.secondary,
          ),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () => context.push('/search'),
              style: FilledButton.styleFrom(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                padding: const EdgeInsets.symmetric(vertical: 18),
              ),
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(LucideIcons.search, size: 20),
                  SizedBox(width: 8),
                  Text('Find Best Routes', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                ],
              ),
            ),
          ),
        ],
      ),
    ).animate().fadeIn(delay: 300.ms).slideY(begin: 0.2, end: 0);
  }

  Widget _buildLocationInput({
    required TextEditingController controller,
    required IconData icon,
    required String label,
    required String hint,
    required Color iconColor,
  }) {
    return Row(
      children: [
        Icon(icon, color: iconColor, size: 20),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: const TextStyle(fontSize: 11, color: AppColors.onSurfaceVariant, fontWeight: FontWeight.bold)),
              TextField(
                controller: controller,
                decoration: InputDecoration(
                  hintText: hint,
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(vertical: 8),
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  fillColor: Colors.transparent,
                ),
                style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildQuickActions(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _QuickActionCard(
            icon: LucideIcons.bus,
            label: 'Nearby',
            onTap: () => context.push('/tracking'),
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

  Widget _buildRecentUpdates(BuildContext context) {
    return Column(
      children: [
        TonalCard(
          margin: const EdgeInsets.only(bottom: 12),
          child: ListTile(
            leading: const Icon(LucideIcons.bookmark, color: AppColors.primary),
            title: const Text('CBD → Remera', style: TextStyle(fontWeight: FontWeight.bold)),
            subtitle: const Text('Arrival in 5 mins'),
            trailing: const Icon(LucideIcons.chevronRight, size: 16),
            onTap: () => context.push('/tracking'),
          ),
        ),
      ],
    ).animate().fadeIn(delay: 700.ms);
  }
}

class _QuickActionCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _QuickActionCard({required this.icon, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return TonalCard(
      onTap: onTap,
      child: Column(
        children: [
          Icon(icon, color: AppColors.primary, size: 24),
          const SizedBox(height: 8),
          Text(label, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
        ],
      ),
    );
  }
}
