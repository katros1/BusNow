import 'package:flutter/material.dart';

class AppColors {
  // ── Primary  #406093 ──────────────────────────────────────────────────────
  static const Color primary          = Color(0xFF406093);
  static const Color primaryLight     = Color(0xFF6B8AB5); // lighter tint
  static const Color primaryDark      = Color(0xFF1A3A5C); // darker shade
  static const Color primaryContainer = Color(0xFFD6E4F7); // 10% tint bg
  static const Color onPrimary        = Color(0xFFFFFFFF);

  // ── Surface / Background (white family) ───────────────────────────────────
  static const Color background             = Color(0xFFF7F9FC);
  static const Color surface                = Color(0xFFFFFFFF);
  static const Color surfaceContainer       = Color(0xFFEEF3F9);
  static const Color surfaceContainerLow    = Color(0xFFF3F7FC);
  static const Color surfaceContainerLowest = Color(0xFFFFFFFF);
  static const Color surfaceContainerHigh   = Color(0xFFD8E2EC);
  static const Color surfaceContainerHighest= Color(0xFFCDD8E6);

  // ── Dark (text / nav) ─────────────────────────────────────────────────────
  static const Color onSurface        = Color(0xFF0D1B2A);
  static const Color onSurfaceVariant = Color(0xFF5A6A7A);

  // ── Outline ───────────────────────────────────────────────────────────────
  static const Color outline        = Color(0xFF8A9BAC);
  static const Color outlineVariant = Color(0xFFD8E2EC);

  // ── Functional ────────────────────────────────────────────────────────────
  static const Color error            = Color(0xFFBA1A1A);
  static const Color onError          = Color(0xFFFFFFFF);
  static const Color errorContainer   = Color(0xFFFFDAD6);
  static const Color onErrorContainer = Color(0xFF93000A);

  // ── Transit-specific (all derived from primary palette) ───────────────────
  static const Color routePrimary = Color(0xFF406093); // bus route line
  static const Color routeMuted   = Color(0xFF6B8AB5); // walk segments

  // ── Glass / overlay ───────────────────────────────────────────────────────
  static const Color glassFill = Color(0xB3D8E2EC);

  // ── Nav bar ───────────────────────────────────────────────────────────────
  static const Color navBar = Color(0xFF0D1B2A); // dark navy
}
