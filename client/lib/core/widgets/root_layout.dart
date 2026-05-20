import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:go_router/go_router.dart';
import 'package:lucide_icons/lucide_icons.dart';


class RootLayout extends StatelessWidget {
  final Widget child;
  const RootLayout({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      extendBody: true,
      body: child,
      bottomNavigationBar: const _BottomBar(),
    );
  }
}

// ─── Floating bottom bar ──────────────────────────────────────────────────────

class _BottomBar extends StatelessWidget {
  const _BottomBar();

  static const _items = [
    (path: '/', icon: LucideIcons.home, label: 'Home'),
    (path: '/saved', icon: LucideIcons.bookmark, label: 'Saved'),
    (path: '/settings', icon: LucideIcons.settings, label: 'Settings'),
  ];

  @override
  Widget build(BuildContext context) {
    final location = GoRouterState.of(context).uri.path;

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 28),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(28),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
          child: Container(
            height: 68,
            decoration: BoxDecoration(
              // Deep charcoal with slight transparency for the glass effect
              color: const Color(0xF0111827),
              borderRadius: BorderRadius.circular(28),
              border: Border.all(
                color: Colors.white.withValues(alpha: 0.08),
                width: 1,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.35),
                  blurRadius: 24,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: _items.map((item) {
                final active = location == item.path;
                return _BarItem(
                  icon: item.icon,
                  label: item.label,
                  isActive: active,
                  onTap: () {
                    HapticFeedback.selectionClick();
                    context.go(item.path);
                  },
                );
              }).toList(),
            ),
          ),
        ),
      ),
    );
  }
}

class _BarItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isActive;
  final VoidCallback onTap;

  const _BarItem({
    required this.icon,
    required this.label,
    required this.isActive,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: 280.ms,
        curve: Curves.easeOutCubic,
        padding: isActive
            ? const EdgeInsets.symmetric(horizontal: 18, vertical: 10)
            : const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: isActive
              ? Colors.white.withValues(alpha: 0.13)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(20),
        ),
        child: isActive
            ? Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(icon, color: Colors.white, size: 20),
                  const SizedBox(width: 8),
                  Text(
                    label,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.2,
                    ),
                  ),
                ],
              ).animate().fadeIn(duration: 200.ms).slideX(begin: -0.1, end: 0)
            : Icon(
                icon,
                color: Colors.white.withValues(alpha: 0.38),
                size: 22,
              ),
      ),
    );
  }
}
