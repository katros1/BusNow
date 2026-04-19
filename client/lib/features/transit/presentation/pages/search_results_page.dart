import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:go_router/go_router.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/app_widgets.dart';

class SearchResultsPage extends StatelessWidget {
  const SearchResultsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(LucideIcons.chevronLeft, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'Rwanda Bus Search',
          style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
        ),
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.all(24),
              itemCount: 4,
              itemBuilder: (context, index) {
                final routes = ['Kigali - Musanze', 'Downtown - Remera', 'Nyabugogo - Kicukiro', 'Kimironko - CBD'];
                return _BusRouteCard(route: routes[index], index: index).animate().fadeIn(
                  delay: (index * 100).ms,
                  duration: 500.ms,
                ).slideY(begin: 0.1, end: 0);
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _BusRouteCard extends StatelessWidget {
  final String route;
  final int index;

  const _BusRouteCard({required this.route, required this.index});

  @override
  Widget build(BuildContext context) {
    return TonalCard(
      margin: const EdgeInsets.only(bottom: 16),
      onTap: () {
        context.push('/tracking');
      },
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.primary.withOpacity(0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(LucideIcons.bus, color: AppColors.primary),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(route, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                const Text('Bus every 15 mins', style: TextStyle(color: AppColors.onSurfaceVariant, fontSize: 13)),
              ],
            ),
          ),
          const Text('FRW 500', style: TextStyle(fontWeight: FontWeight.bold, color: AppColors.primary)),
        ],
      ),
    );
  }
}
