import 'package:latlong2/latlong.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';

class NearestStopModel {
  final String stopId;
  final String stopName;
  final LatLng coordinates;
  final double distanceKm;

  const NearestStopModel({
    required this.stopId,
    required this.stopName,
    required this.coordinates,
    required this.distanceKm,
  });

  factory NearestStopModel.fromJson(Map<String, dynamic> json) {
    final rawCoords = json['coordinates'] as List<dynamic>;
    final lng = (rawCoords[0] as num).toDouble();
    final lat = (rawCoords[1] as num).toDouble();

    return NearestStopModel(
      stopId: json['stopId'] as String? ?? '',
      stopName: json['stopName'] as String? ?? '',
      coordinates: LatLng(lat, lng),
      distanceKm: (json['distanceKm'] as num? ?? 0).toDouble(),
    );
  }

  NearestStop toEntity() => NearestStop(
        stopId: stopId,
        stopName: stopName,
        coordinates: coordinates,
        distanceKm: distanceKm,
      );
}
