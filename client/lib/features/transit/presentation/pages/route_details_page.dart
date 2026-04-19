import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:go_router/go_router.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/app_widgets.dart';

class RouteDetailsPage extends StatelessWidget {
  const RouteDetailsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: CustomScrollView(
        slivers: [
          // Elegant Header with Route Info
          SliverAppBar(
            pinned: true,
            expandedHeight: 200,
            backgroundColor: AppColors.primary,
            leading: IconButton(
              icon: const Icon(LucideIcons.chevronLeft, color: Colors.white),
              onPressed: () => Navigator.pop(context),
            ),
            flexibleSpace: FlexibleSpaceBar(
              background: Container(
                decoration: const BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [AppColors.primary, AppColors.primaryContainer],
                  ),
                ),
                child: Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const SizedBox(height: 40),
                      Text(
                        'ROUTE 102',
                        style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 2,
                        ),
                      ),
                      const Text(
                        'Market to Remera',
                        style: TextStyle(color: Colors.white70, fontSize: 16),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          
          // Timeline of Stops
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SectionHeader(title: 'Stops & Schedule'),
                  const SizedBox(height: 16),
                  ListView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: 8,
                    itemBuilder: (context, index) {
                      return _StopTimelineItem(
                        index: index,
                        isLast: index == 7,
                        isNext: index == 2,
                        time: '${12 + index}:${index * 5}',
                        stopName: 'Stop Name ${index + 1}',
                      ).animate().fadeIn(delay: (index * 50).ms);
                    },
                  ),
                ],
              ),
            ),
          ),
          
          // Live Tracking CTA
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
              child: FilledButton.icon(
                onPressed: () {
                  context.push('/tracking');
                },
                icon: const Icon(LucideIcons.navigation),
                label: const Text('Track Live Vehicle'),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _StopTimelineItem extends StatelessWidget {
  final int index;
  final bool isLast;
  final bool isNext;
  final String time;
  final String stopName;

  const _StopTimelineItem({
    required this.index,
    required this.isLast,
    required this.isNext,
    required this.time,
    required this.stopName,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 0),
      child: IntrinsicHeight(
        child: Row(
          children: [
            Column(
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    color: isNext ? AppColors.secondary : (index < 2 ? AppColors.outlineVariant : AppColors.primary),
                    shape: BoxShape.circle,
                    border: isNext ? Border.all(color: Colors.white, width: 2) : null,
                    boxShadow: isNext ? [BoxShadow(color: AppColors.secondary.withOpacity(0.5), blurRadius: 10)] : null,
                  ),
                ),
                if (!isLast)
                  Expanded(
                    child: Container(
                      width: 2,
                      color: index < 2 ? AppColors.surfaceContainerHigh : AppColors.primary.withOpacity(0.3),
                    ),
                  ),
              ],
            ),
            const SizedBox(width: 20),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(bottom: 24),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          stopName,
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: isNext ? FontWeight.bold : FontWeight.w500,
                            color: index < 2 ? AppColors.onSurfaceVariant : AppColors.onSurface,
                          ),
                        ),
                        if (isNext)
                          const Text(
                            'Arriving in 4 mins',
                            style: TextStyle(color: AppColors.secondary, fontSize: 12, fontWeight: FontWeight.bold),
                          ),
                      ],
                    ),
                    Text(
                      time,
                      style: TextStyle(
                        color: index < 2 ? AppColors.onSurfaceVariant : AppColors.onSurface,
                        fontWeight: isNext ? FontWeight.bold : FontWeight.normal,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
