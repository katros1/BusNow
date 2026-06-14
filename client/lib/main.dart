import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:client/core/theme/app_theme.dart';
import 'package:client/core/router/app_router.dart';
import 'package:client/core/services/notification_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Hive.initFlutter();
  await NotificationService.instance.init();
  runApp(const ProviderScope(child: UrbanTransitApp()));
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
