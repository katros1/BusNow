import 'package:flutter/material.dart';
import 'package:client/core/theme/app_theme.dart';
import 'package:client/core/router/app_router.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const UrbanTransitApp());
}

class UrbanTransitApp extends StatelessWidget {
  const UrbanTransitApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'IOTS',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      routerConfig: AppRouter.router,
    );
  }
}
