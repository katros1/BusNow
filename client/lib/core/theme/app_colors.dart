import 'package:flutter/material.dart';

class AppColors {
  // Brand Colors
  static const Color primary = Color(0xFF005BBF);
  static const Color primaryContainer = Color(0xFF1A73E8);
  static const Color onPrimary = Color(0xFFFFFFFF);
  
  static const Color secondary = Color(0xFF795900);
  static const Color secondaryContainer = Color(0xFFFEBF0D);
  static const Color onSecondary = Color(0xFFFFFFFF);

  // Surface Colors (Tonal Layering)
  static const Color background = Color(0xFFF8F9FA);
  static const Color surface = Color(0xFFF8F9FA);
  static const Color surfaceContainer = Color(0xFFEDEEEF);
  static const Color surfaceContainerLow = Color(0xFFF3F4F5);
  static const Color surfaceContainerLowest = Color(0xFFFFFFFF);
  static const Color surfaceContainerHigh = Color(0xFFE7E8E9);
  static const Color surfaceContainerHighest = Color(0xFFE1E3E4);
  
  static const Color onSurface = Color(0xFF191C1D);
  static const Color onSurfaceVariant = Color(0xFF414754);
  
  // Neutral / Outline
  static const Color outline = Color(0xFF727785);
  static const Color outlineVariant = Color(0xFFC1C6D6);
  
  // Functional Colors
  static const Color error = Color(0xFFBA1A1A);
  static const Color onError = Color(0xFFFFFFFF);
  static const Color errorContainer = Color(0xFFFFDAD6);
  static const Color onErrorContainer = Color(0xFF93000A);
  
  // Transit Specific (Bus/Train colors)
  static const Color routeBlue = Color(0xFF005BC0);
  static const Color routeAmber = Color(0xFFFBBC05);
  
  // Effects
  static const Color glassFill = Color(0xB3E1E3E4); // 70% opacity of surfaceVariant
}
