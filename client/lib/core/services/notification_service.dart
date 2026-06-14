import 'package:flutter_local_notifications/flutter_local_notifications.dart';

class NotificationService {
  NotificationService._();
  static final NotificationService instance = NotificationService._();

  final _plugin = FlutterLocalNotificationsPlugin();
  bool _ready = false;

  Future<void> init() async {
    const android = AndroidInitializationSettings('@mipmap/ic_launcher');
    const ios = DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: false,
      requestSoundPermission: true,
    );
    await _plugin.initialize(
      const InitializationSettings(android: android, iOS: ios),
    );
    _ready = true;
  }

  Future<void> showBusApproaching(String plate, int etaMin) async {
    if (!_ready) return;
    const android = AndroidNotificationDetails(
      'bus_approach',
      'Bus Approaching',
      channelDescription: 'Alert when your bus is about to arrive',
      importance: Importance.max,
      priority: Priority.high,
      ticker: 'Bus arriving soon',
    );
    const ios = DarwinNotificationDetails(presentAlert: true, presentSound: true);
    await _plugin.show(
      plate.hashCode,
      '🚌 Bus approaching!',
      '$plate is ~$etaMin min away from your boarding stop',
      const NotificationDetails(android: android, iOS: ios),
    );
  }
}
