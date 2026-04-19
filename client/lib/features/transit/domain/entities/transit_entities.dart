import 'package:equatable/equatable.dart';

class TransitStop extends Equatable {
  final String id;
  final String name;
  final String locationName;
  final double latitude;
  final double longitude;
  final List<String> routesServed;

  const TransitStop({
    required this.id,
    required this.name,
    required this.locationName,
    required this.latitude,
    required this.longitude,
    required this.routesServed,
  });

  @override
  List<Object?> get props => [id, name, locationName, latitude, longitude, routesServed];
}

class TransitRoute extends Equatable {
  final String id;
  final String routeNumber;
  final String origin;
  final String destination;
  final String status;
  final int etaMinutes;
  final List<String> stopIds;

  const TransitRoute({
    required this.id,
    required this.routeNumber,
    required this.origin,
    required this.destination,
    required this.status,
    required this.etaMinutes,
    required this.stopIds,
  });

  @override
  List<Object?> get props => [id, routeNumber, origin, destination, status, etaMinutes, stopIds];
}

class TransitVehicle extends Equatable {
  final String id;
  final String routeId;
  final double latitude;
  final double longitude;
  final double speed;
  final int occupancy; // 0-100%

  const TransitVehicle({
    required this.id,
    required this.routeId,
    required this.latitude,
    required this.longitude,
    required this.speed,
    required this.occupancy,
  });

  @override
  List<Object?> get props => [id, routeId, latitude, longitude, speed, occupancy];
}
