import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:client/features/transit/presentation/pages/home_page.dart';
import 'package:client/features/transit/presentation/pages/search_results_page.dart';
import 'package:client/features/transit/presentation/pages/route_details_page.dart';
import 'package:client/features/transit/presentation/pages/live_tracking_page.dart';
import 'package:client/features/transit/presentation/pages/saved_routes_page.dart';
import 'package:client/features/profile/presentation/pages/settings_page.dart';
import 'package:client/core/widgets/root_layout.dart';

class AppRouter {
  static final _rootNavigatorKey = GlobalKey<NavigatorState>();
  static final _shellNavigatorKey = GlobalKey<NavigatorState>();

  static final GoRouter router = GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: '/',
    routes: [
      ShellRoute(
        navigatorKey: _shellNavigatorKey,
        builder: (context, state, child) => RootLayout(child: child),
        routes: [
          GoRoute(
            path: '/',
            builder: (context, state) => const HomePage(),
          ),
          GoRoute(
            path: '/search',
            builder: (context, state) => const SearchResultsPage(),
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
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/route-details',
        builder: (context, state) => const RouteDetailsPage(),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/tracking',
        builder: (context, state) => const LiveTrackingPage(),
      ),
    ],
  );
}
