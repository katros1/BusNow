import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:client/features/transit/presentation/pages/home_page.dart';
import 'package:client/features/transit/presentation/pages/search_results_page.dart';
import 'package:client/features/transit/presentation/pages/route_details_page.dart';
import 'package:client/features/transit/presentation/pages/saved_routes_page.dart';
import 'package:client/features/transit/presentation/pages/nearby_stops_page.dart';
import 'package:client/features/transit/presentation/pages/ai_recommendations_page.dart';
import 'package:client/features/profile/presentation/pages/settings_page.dart';
import 'package:client/core/widgets/root_layout.dart';
import 'package:client/features/splash/presentation/pages/splash_page.dart';

class AppRouter {
  static final _rootNavigatorKey = GlobalKey<NavigatorState>();
  static final _shellNavigatorKey = GlobalKey<NavigatorState>();

  static final GoRouter router = GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: '/splash',
    routes: [
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/splash',
        builder: (context, state) => const SplashPage(),
      ),
      ShellRoute(
        navigatorKey: _shellNavigatorKey,
        builder: (context, state, child) => RootLayout(child: child),
        routes: [
          GoRoute(
            path: '/',
            builder: (context, state) => const HomePage(),
          ),
          GoRoute(
            path: '/saved',
            builder: (context, state) => const SavedRoutesPage(),
          ),
          GoRoute(
            path: '/settings',
            builder: (context, state) => const SettingsPage(),
          ),
        ],
      ),
      // Full-screen pages (no bottom nav bar)
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/search',
        builder: (context, state) => const SearchResultsPage(),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/route-details',
        builder: (context, state) => const RouteDetailsPage(),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/nearby',
        builder: (context, state) => const NearbyStopsPage(),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/ai-stops',
        builder: (context, state) => const AiRecommendationsPage(),
      ),
    ],
  );
}
