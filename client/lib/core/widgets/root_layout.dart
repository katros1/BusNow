import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/glass_container.dart';
import 'dart:ui';

class RootLayout extends StatelessWidget {
  final Widget child;

  const RootLayout({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      extendBody: true, // Allows content to flow behind the bottom bar
      body: child,
      bottomNavigationBar: const _FloatingBottomBar(),
    );
  }
}

class _FloatingBottomBar extends StatelessWidget {
  const _FloatingBottomBar();

  @override
  Widget build(BuildContext context) {
    final location = GoRouterState.of(context).uri.path;
    
    return Container(
      height: 100,
      margin: const EdgeInsets.fromLTRB(24, 0, 24, 32),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(30),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(
              color: AppColors.onSurface.withOpacity(0.85), // Dark sleek bar
              borderRadius: BorderRadius.circular(30),
              border: Border.all(color: Colors.white.withOpacity(0.1)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _NavButton(
                  icon: LucideIcons.home,
                  label: 'Home',
                  isActive: location == '/',
                  onTap: () => context.go('/'),
                ),
                _NavButton(
                  icon: LucideIcons.bookmark,
                  label: 'Saved',
                  isActive: location == '/saved',
                  onTap: () => context.go('/saved'),
                ),
                _NavButton(
                  icon: LucideIcons.settings,
                  label: 'Settings',
                  isActive: location == '/settings',
                  onTap: () => context.go('/settings'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _NavButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isActive;
  final VoidCallback onTap;

  const _NavButton({
    required this.icon,
    required this.label,
    required this.isActive,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final color = isActive ? Colors.white : Colors.white.withOpacity(0.4);
    
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOutCubic,
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        decoration: BoxDecoration(
          color: isActive ? Colors.white.withOpacity(0.12) : Colors.transparent,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: color, size: 24),
            if (isActive) ...[
              const SizedBox(height: 4),
              Text(
                label,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 0.5,
                ),
              ).animate().fadeIn(duration: 200.ms).scale(begin: const Offset(0.8, 0.8)),
            ]
          ],
        ),
      ),
    );
  }
}
